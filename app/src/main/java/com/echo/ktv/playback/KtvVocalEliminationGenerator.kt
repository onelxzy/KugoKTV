package com.echo.ktv.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

data class DspSettings(
    val vocalCutDepth: Float = 0.88f, // 0.0f - 1.0f (Default 0.88: Deep vocal subtraction)
    val bassBoost: Float = 0.80f,     // 0.0f - 1.0f (Default 0.80: Rich, punchy bass)
    val gainBoost: Float = 0.85f,     // 0.0f - 1.0f (Default 0.85: Loudness make-up)
    val channelMode: Int = 0          // 0: Intelligent Multi-Band DSP, 1: Left Solo, 2: Right Solo
) {
    companion object {
        val DEFAULT = DspSettings()
    }
}

/**
 * Advanced Multi-Band DSP Vocal Eliminator with Formant Notch Filtering and Tunable Controls
 */
object KtvVocalEliminationGenerator {
    private val mainHandler = Handler(Looper.getMainLooper())

    class BiquadFilter(
        private val type: Type,
        private val frequency: Float,
        private val sampleRate: Float,
        private val q: Float = 0.7071f
    ) {
        enum class Type { LOWPASS, HIGHPASS, NOTCH }

        private var b0 = 0f
        private var b1 = 0f
        private var b2 = 0f
        private var a1 = 0f
        private var a2 = 0f

        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        init {
            val w0 = (2.0 * Math.PI * frequency / sampleRate).toFloat()
            val alpha = (sin(w0.toDouble()) / (2.0 * q)).toFloat()
            val cosW0 = cos(w0.toDouble()).toFloat()

            when (type) {
                Type.LOWPASS -> {
                    val b0Temp = (1f - cosW0) / 2f
                    val b1Temp = 1f - cosW0
                    val b2Temp = (1f - cosW0) / 2f
                    val a0Temp = 1f + alpha
                    val a1Temp = -2f * cosW0
                    val a2Temp = 1f - alpha

                    b0 = b0Temp / a0Temp
                    b1 = b1Temp / a0Temp
                    b2 = b2Temp / a0Temp
                    a1 = a1Temp / a0Temp
                    a2 = a2Temp / a0Temp
                }
                Type.HIGHPASS -> {
                    val b0Temp = (1f + cosW0) / 2f
                    val b1Temp = -(1f + cosW0)
                    val b2Temp = (1f + cosW0) / 2f
                    val a0Temp = 1f + alpha
                    val a1Temp = -2f * cosW0
                    val a2Temp = 1f - alpha

                    b0 = b0Temp / a0Temp
                    b1 = b1Temp / a0Temp
                    b2 = b2Temp / a0Temp
                    a1 = a1Temp / a0Temp
                    a2 = a2Temp / a0Temp
                }
                Type.NOTCH -> {
                    val b0Temp = 1f
                    val b1Temp = -2f * cosW0
                    val b2Temp = 1f
                    val a0Temp = 1f + alpha
                    val a1Temp = -2f * cosW0
                    val a2Temp = 1f - alpha

                    b0 = b0Temp / a0Temp
                    b1 = b1Temp / a0Temp
                    b2 = b2Temp / a0Temp
                    a1 = a1Temp / a0Temp
                    a2 = a2Temp / a0Temp
                }
            }
        }

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }
    }

    private fun softLimit(sample: Float): Short {
        if (sample > 28000f) {
            val over = (sample - 28000f) / 4767f
            val compressed = 28000f + 4767f * (over / (1f + over))
            return compressed.coerceIn(-32767f, 32767f).toInt().toShort()
        } else if (sample < -28000f) {
            val under = (-sample - 28000f) / 4767f
            val compressed = -(28000f + 4767f * (under / (1f + under)))
            return compressed.coerceIn(-32767f, 32767f).toInt().toShort()
        }
        return sample.toInt().toShort()
    }

    fun generateAccompaniment(
        context: Context,
        inputPathOrUrl: String,
        hash: String,
        settings: DspSettings = DspSettings.DEFAULT,
        callback: (Result<File>) -> Unit
    ) {
        val cacheDir = File(context.filesDir, "ktv_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val cutTag = (settings.vocalCutDepth * 100).toInt()
        val bassTag = (settings.bassBoost * 100).toInt()
        val gainTag = (settings.gainBoost * 100).toInt()
        val accFile = File(cacheDir, "${hash.lowercase()}_acc_c${cutTag}_b${bassTag}_g${gainTag}_m${settings.channelMode}.wav")
        
        if (accFile.exists() && accFile.length() > 1000) {
            mainHandler.post { callback(Result.success(accFile)) }
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val extractor = MediaExtractor()
                if (inputPathOrUrl.startsWith("http://") || inputPathOrUrl.startsWith("https://")) {
                    extractor.setDataSource(inputPathOrUrl)
                } else {
                    val localFile = File(inputPathOrUrl.replace("file:///", "").replace("file://", ""))
                    if (!localFile.exists()) {
                        mainHandler.post { callback(Result.failure(Exception("Input file not found"))) }
                        return@launch
                    }
                    extractor.setDataSource(localFile.absolutePath)
                }

                var audioTrackIndex = -1
                var inputFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        inputFormat = format
                        break
                    }
                }

                if (audioTrackIndex < 0 || inputFormat == null) {
                    mainHandler.post { callback(Result.failure(Exception("No audio track found"))) }
                    return@launch
                }

                extractor.selectTrack(audioTrackIndex)
                val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
                val decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(inputFormat, null, null, 0)
                decoder.start()

                val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                } else 44100
                val channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else 2

                // Multi-band filters tuned for optimum acoustic isolation
                val sRateF = sampleRate.toFloat()
                val bassFilterLeft = BiquadFilter(BiquadFilter.Type.LOWPASS, 160f, sRateF)
                val bassFilterRight = BiquadFilter(BiquadFilter.Type.LOWPASS, 160f, sRateF)
                val highFilterLeft = BiquadFilter(BiquadFilter.Type.HIGHPASS, 4200f, sRateF)
                val highFilterRight = BiquadFilter(BiquadFilter.Type.HIGHPASS, 4200f, sRateF)
                val notchFilterLeft = BiquadFilter(BiquadFilter.Type.NOTCH, 1500f, sRateF, q = 1.6f)
                val notchFilterRight = BiquadFilter(BiquadFilter.Type.NOTCH, 1500f, sRateF, q = 1.6f)

                val tempPcmFile = File(cacheDir, "${hash.lowercase()}_temp_${System.currentTimeMillis()}.pcm")
                val pcmOut = FileOutputStream(tempPcmFile)

                val bufferInfo = MediaCodec.BufferInfo()
                var isEOS = false
                val timeoutUs = 10000L

                val cutDepthMul = 1.15f + settings.vocalCutDepth * 0.70f // 1.15 ~ 1.85x
                val bassMul = 0.85f + settings.bassBoost * 0.75f         // 0.85 ~ 1.60x
                val totalGainMul = 1.05f + settings.gainBoost * 0.55f    // 1.05 ~ 1.60x

                while (!isEOS) {
                    val inIndex = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                decoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    var outIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    while (outIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            if (channelCount == 2) {
                                val pcmData = ByteArray(bufferInfo.size)
                                outputBuffer.get(pcmData)
                                val byteBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
                                val outBuffer = ByteBuffer.allocate(bufferInfo.size).order(ByteOrder.LITTLE_ENDIAN)

                                while (byteBuffer.hasRemaining()) {
                                    val leftRaw = byteBuffer.short.toFloat()
                                    val rightRaw = byteBuffer.short.toFloat()

                                    when (settings.channelMode) {
                                        1 -> {
                                            // Left Channel Solo (for left-accompaniment MVs)
                                            val mono = softLimit(leftRaw * totalGainMul)
                                            outBuffer.putShort(mono)
                                            outBuffer.putShort(mono)
                                        }
                                        2 -> {
                                            // Right Channel Solo (for right-accompaniment MVs)
                                            val mono = softLimit(rightRaw * totalGainMul)
                                            outBuffer.putShort(mono)
                                            outBuffer.putShort(mono)
                                        }
                                        else -> {
                                            // 0: Intelligent Multi-Band DSP Cancellation
                                            // 1. Low-frequency Bass Extraction (<160Hz)
                                            val bassLeft = bassFilterLeft.process(leftRaw)
                                            val bassRight = bassFilterRight.process(rightRaw)
                                            val monoBass = (bassLeft + bassRight) * 0.5f * bassMul

                                            // 2. High-frequency Air & Reverb Extraction (>4200Hz)
                                            val highLeft = highFilterLeft.process(leftRaw)
                                            val highRight = highFilterRight.process(rightRaw)

                                            // 3. Formant Notch Suppression on Side Difference (removes throat vocal resonance)
                                            val rawDiff = (leftRaw - rightRaw) * cutDepthMul
                                            val notchedDiffLeft = notchFilterLeft.process(rawDiff)
                                            val notchedDiffRight = notchFilterRight.process(-rawDiff)

                                            // 4. Harmonic Acoustic Reconstruction with dynamic make-up gain
                                            val outLeftF = (notchedDiffLeft + monoBass + highLeft * 0.65f) * totalGainMul
                                            val outRightF = (notchedDiffRight + monoBass + highRight * 0.65f) * totalGainMul

                                            val outLeft = softLimit(outLeftF)
                                            val outRight = softLimit(outRightF)

                                            outBuffer.putShort(outLeft)
                                            outBuffer.putShort(outRight)
                                        }
                                    }
                                }
                                pcmOut.write(outBuffer.array())
                            } else {
                                val pcmData = ByteArray(bufferInfo.size)
                                outputBuffer.get(pcmData)
                                pcmOut.write(pcmData)
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        outIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }

                pcmOut.close()
                decoder.stop()
                decoder.release()
                extractor.release()

                // Convert PCM to WAV
                writeWavHeader(tempPcmFile, accFile, sampleRate, channelCount)
                tempPcmFile.delete()

                mainHandler.post { callback(Result.success(accFile)) }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post { callback(Result.failure(e)) }
            }
        }
    }

    private fun writeWavHeader(pcmFile: File, wavFile: File, sampleRate: Int, channels: Int) {
        val pcmSize = pcmFile.length().toInt()
        val totalDataLen = pcmSize + 36
        val byteRate = sampleRate * channels * 2

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 2).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmSize and 0xff).toByte()
        header[41] = (pcmSize shr 8 and 0xff).toByte()
        header[42] = (pcmSize shr 16 and 0xff).toByte()
        header[43] = (pcmSize shr 24 and 0xff).toByte()

        val wavOut = FileOutputStream(wavFile)
        wavOut.write(header)
        pcmFile.inputStream().use { input ->
            input.copyTo(wavOut)
        }
        wavOut.close()
    }
}

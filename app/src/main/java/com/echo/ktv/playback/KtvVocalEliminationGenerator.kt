package com.echo.ktv.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object KtvVocalEliminationGenerator {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun generateAccompaniment(
        context: Context,
        inputPathOrUrl: String,
        hash: String,
        callback: (Result<File>) -> Unit
    ) {
        val cacheDir = File(context.filesDir, "ktv_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val accFile = File(cacheDir, "${hash.lowercase()}_acc.wav")
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

                val tempPcmFile = File(cacheDir, "${hash.lowercase()}_temp.pcm")
                val pcmOut = FileOutputStream(tempPcmFile)

                val bufferInfo = MediaCodec.BufferInfo()
                var isEOS = false
                val timeoutUs = 10000L

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
                                // Process 16-bit Stereo PCM: Center Vocal Cancellation with +3dB Volume Gain Boost (0.707f)
                                val pcmData = ByteArray(bufferInfo.size)
                                outputBuffer.get(pcmData)
                                val byteBuffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
                                val outBuffer = ByteBuffer.allocate(bufferInfo.size).order(ByteOrder.LITTLE_ENDIAN)

                                while (byteBuffer.hasRemaining()) {
                                    val left = byteBuffer.short
                                    val right = byteBuffer.short
                                    val rawDiff = (left.toInt() - right.toInt())
                                    val diff = (rawDiff * 0.707f).toInt().coerceIn(-32768, 32767).toShort()
                                    outBuffer.putShort(diff)
                                    outBuffer.putShort(diff)
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

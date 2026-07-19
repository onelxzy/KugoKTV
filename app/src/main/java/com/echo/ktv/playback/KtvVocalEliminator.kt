package com.echo.ktv.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KtvVocalEliminator : AudioProcessor {
    private var inputFormat = AudioFormat.NOT_SET
    private var outputFormat = AudioFormat.NOT_SET
    private var isEliminating = false
    private var buffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    fun setEliminateVocal(enabled: Boolean) {
        this.isEliminating = enabled
    }

    fun isEliminatingVocal(): Boolean = isEliminating

    override fun configure(format: AudioFormat): AudioFormat {
        if (format.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(format)
        }
        // Vocal cancellation works best with stereo audio (2 channels)
        inputFormat = format
        outputFormat = format
        return outputFormat
    }

    override fun isActive(): Boolean = isEliminating && inputFormat.channelCount == 2

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position

        if (outputBuffer.capacity() < size) {
            outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        // Stereo 16-bit PCM: Left (2 bytes), Right (2 bytes), Left (2 bytes), Right (2 bytes) ...
        while (inputBuffer.hasRemaining()) {
            val left = inputBuffer.short
            val right = inputBuffer.short

            // Center Channel Cancellation: Diff = (Left - Right) / 2
            val diff = ((left.toInt() - right.toInt()) / 2).coerceIn(-32768, 32767).toShort()

            // Write mono difference to both left and right output channels
            outputBuffer.putShort(diff)
            outputBuffer.putShort(diff)
        }

        inputBuffer.position(limit)
        outputBuffer.flip()
        buffer = outputBuffer
    }

    override fun getOutput(): ByteBuffer {
        val output = buffer
        buffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun isEnded(): Boolean = inputEnded && buffer == AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        buffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
    }
}

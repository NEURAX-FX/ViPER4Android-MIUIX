package com.llsl.viper4android.utils

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodedWav(
    val samples: FloatArray,
    val frameCount: Int,
    val channels: Int,
    val sampleRate: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedWav) return false
        return samples.contentEquals(other.samples) &&
            frameCount == other.frameCount &&
            channels == other.channels &&
            sampleRate == other.sampleRate
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + frameCount
        result = 31 * result + channels
        result = 31 * result + sampleRate
        return result
    }
}

object WavDecoder {
    private const val MAX_SAMPLES = 4 * 1024 * 1024
    private const val FORMAT_PCM = 1
    private const val FORMAT_IEEE_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE

    fun decode(stream: InputStream): DecodedWav = decode(stream.readBytes())

    fun decode(bytes: ByteArray): DecodedWav {
        if (bytes.size < 12) {
            throw IllegalArgumentException("WAV too small: ${bytes.size} bytes")
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.int != 0x46464952) throw IllegalArgumentException("Not a RIFF file")
        buffer.int
        if (buffer.int != 0x45564157) throw IllegalArgumentException("Not a WAVE file")

        var formatCode = -1
        var channels = -1
        var sampleRate = -1
        var blockAlign = -1
        var bitsPerSample = -1
        var dataOffset = -1
        var dataSize = -1

        while (buffer.remaining() >= 8) {
            val chunkId = buffer.int
            val chunkSizeUnsigned = buffer.int.toLong() and 0xFFFF_FFFFL
            val chunkStart = buffer.position()
            val chunkEndLong = chunkStart.toLong() + chunkSizeUnsigned
            if (chunkEndLong > bytes.size || chunkEndLong < chunkStart) {
                throw IllegalArgumentException("WAV chunk exceeds file bounds")
            }
            val chunkEnd = chunkEndLong.toInt()

            when (chunkId) {
                0x20746D66 -> {
                    if (chunkSizeUnsigned < 16) {
                        throw IllegalArgumentException("fmt chunk too small: $chunkSizeUnsigned")
                    }
                    formatCode = buffer.short.toInt() and 0xFFFF
                    channels = buffer.short.toInt() and 0xFFFF
                    sampleRate = buffer.int
                    buffer.int
                    blockAlign = buffer.short.toInt() and 0xFFFF
                    bitsPerSample = buffer.short.toInt() and 0xFFFF

                    if (formatCode == FORMAT_EXTENSIBLE) {
                        if (chunkSizeUnsigned < 40) {
                            throw IllegalArgumentException("Extensible fmt chunk too small")
                        }
                        val extensionSize = buffer.short.toInt() and 0xFFFF
                        buffer.short
                        buffer.int
                        val subFormat = buffer.int
                        val guidData2 = buffer.short.toInt() and 0xFFFF
                        val guidData3 = buffer.short.toInt() and 0xFFFF
                        val guidTail = ByteArray(8).also(buffer::get)
                        val expectedTail = byteArrayOf(
                            0x80.toByte(),
                            0,
                            0,
                            0xAA.toByte(),
                            0,
                            0x38,
                            0x9B.toByte(),
                            0x71,
                        )
                        if (extensionSize < 22 || guidData2 != 0 || guidData3 != 0x0010 ||
                            !guidTail.contentEquals(expectedTail)
                        ) {
                            throw IllegalArgumentException("Unsupported extensible WAV subformat")
                        }
                        formatCode = subFormat
                    }
                }

                0x61746164 -> {
                    dataOffset = chunkStart
                    dataSize = chunkSizeUnsigned.toInt()
                }
            }

            val paddedEnd = chunkEndLong + (chunkSizeUnsigned and 1L)
            if (paddedEnd > bytes.size) {
                throw IllegalArgumentException("WAV chunk padding exceeds file bounds")
            }
            buffer.position(paddedEnd.toInt())
            if (formatCode != -1 && dataOffset != -1) break
        }

        if (formatCode == -1) throw IllegalArgumentException("WAV missing fmt chunk")
        if (dataOffset == -1) throw IllegalArgumentException("WAV missing data chunk")
        if (channels != 1 && channels != 2 && channels != 4) {
            throw IllegalArgumentException(
                "Unsupported channel count: $channels (expected 1, 2, or 4)",
            )
        }
        if (formatCode != FORMAT_PCM && formatCode != FORMAT_IEEE_FLOAT) {
            throw IllegalArgumentException(
                "Unsupported WAV format code: $formatCode (expected PCM or IEEE float)",
            )
        }
        if (formatCode == FORMAT_PCM && bitsPerSample !in setOf(16, 24, 32)) {
            throw IllegalArgumentException("Unsupported PCM bit depth: $bitsPerSample")
        }
        if (formatCode == FORMAT_IEEE_FLOAT && bitsPerSample != 32) {
            throw IllegalArgumentException("Unsupported float bit depth: $bitsPerSample")
        }

        val bytesPerSample = bitsPerSample / 8
        val expectedBlockAlign = channels * bytesPerSample
        if (blockAlign != expectedBlockAlign || dataSize <= 0 || dataSize % expectedBlockAlign != 0) {
            throw IllegalArgumentException("Invalid WAV data alignment")
        }
        val frameCount = dataSize / expectedBlockAlign
        val totalSamplesLong = frameCount.toLong() * channels
        if (frameCount <= 0 || totalSamplesLong > MAX_SAMPLES) {
            throw IllegalArgumentException("WAV has invalid frame count")
        }
        val totalSamples = totalSamplesLong.toInt()
        val samples = FloatArray(totalSamples)
        buffer.position(dataOffset)
        when {
            formatCode == FORMAT_IEEE_FLOAT -> {
                for (index in samples.indices) samples[index] = buffer.float
            }

            bitsPerSample == 16 -> {
                val scale = 1.0f / 32768.0f
                for (index in samples.indices) samples[index] = buffer.short.toInt() * scale
            }

            bitsPerSample == 24 -> {
                val scale = 1.0f / (1 shl 23).toFloat()
                for (index in samples.indices) {
                    val b0 = buffer.get().toInt() and 0xFF
                    val b1 = buffer.get().toInt() and 0xFF
                    val b2 = buffer.get().toInt()
                    samples[index] = ((b2 shl 16) or (b1 shl 8) or b0) * scale
                }
            }

            else -> {
                val scale = 1.0f / (1L shl 31).toFloat()
                for (index in samples.indices) samples[index] = buffer.int * scale
            }
        }

        return DecodedWav(samples, frameCount, channels, sampleRate)
    }
}

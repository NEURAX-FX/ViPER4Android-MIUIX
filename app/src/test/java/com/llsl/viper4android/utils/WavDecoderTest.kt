package com.llsl.viper4android.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WavDecoderTest {
    @Test
    fun decodesExtensibleFourChannelFloatInCanonicalOrder() {
        val decoded = WavDecoder.decode(extensibleFloatWav())

        assertEquals(4, decoded.channels)
        assertEquals(16, decoded.frameCount)
        assertEquals(48_000, decoded.sampleRate)
        assertArrayEquals(floatArrayOf(0.25f, 0.5f, 0.75f, 1f), decoded.samples.copyOf(4), 0f)
    }

    @Test
    fun rejectsUnsupportedChannelCount() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            WavDecoder.decode(pcm16Wav(channels = 3))
        }

        assertEquals("Unsupported channel count: 3 (expected 1, 2, or 4)", error.message)
    }

    private fun extensibleFloatWav(): ByteArray {
        val channels = 4
        val frames = 16
        val dataSize = channels * frames * Float.SIZE_BYTES
        return ByteBuffer.allocate(12 + 8 + 40 + 8 + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putTag("RIFF")
                putInt(capacity() - 8)
                putTag("WAVE")
                putTag("fmt ")
                putInt(40)
                putShort(0xFFFE.toShort())
                putShort(channels.toShort())
                putInt(48_000)
                putInt(48_000 * channels * Float.SIZE_BYTES)
                putShort((channels * Float.SIZE_BYTES).toShort())
                putShort(32.toShort())
                putShort(22.toShort())
                putShort(32.toShort())
                putInt(0)
                putInt(3)
                putShort(0.toShort())
                putShort(0x0010.toShort())
                put(
                    byteArrayOf(
                        0x80.toByte(),
                        0,
                        0,
                        0xAA.toByte(),
                        0,
                        0x38,
                        0x9B.toByte(),
                        0x71,
                    ),
                )
                putTag("data")
                putInt(dataSize)
                repeat(frames) {
                    putFloat(0.25f)
                    putFloat(0.5f)
                    putFloat(0.75f)
                    putFloat(1f)
                }
            }
            .array()
    }

    private fun pcm16Wav(channels: Int): ByteArray {
        val frames = 16
        val dataSize = channels * frames * Short.SIZE_BYTES
        return ByteBuffer.allocate(44 + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putTag("RIFF")
                putInt(capacity() - 8)
                putTag("WAVE")
                putTag("fmt ")
                putInt(16)
                putShort(1.toShort())
                putShort(channels.toShort())
                putInt(48_000)
                putInt(48_000 * channels * Short.SIZE_BYTES)
                putShort((channels * Short.SIZE_BYTES).toShort())
                putShort(16.toShort())
                putTag("data")
                putInt(dataSize)
                repeat(channels * frames) { putShort(0.toShort()) }
            }
            .array()
    }

    private fun ByteBuffer.putTag(value: String) {
        put(value.toByteArray(Charsets.US_ASCII))
    }
}

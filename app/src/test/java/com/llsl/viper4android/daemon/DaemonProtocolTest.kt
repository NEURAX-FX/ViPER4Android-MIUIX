package com.llsl.viper4android.daemon

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DaemonProtocolTest {
    private fun header(
        messageType: Int = DaemonProtocol.CMD_SNAPSHOT_BEGIN,
        requestId: Long = 7,
        sequence: Long = 11,
    ) = DaemonProtocol.FrameHeader(messageType = messageType, requestId = requestId, sequence = sequence)

    @Test
    fun `frame round trip preserves header and payload`() {
        val payload = ByteArray(128) { (it * 3).toByte() }
        val encoded = DaemonProtocol.encodeFrame(header(), payload)

        assertEquals(DaemonProtocol.FRAME_HEADER_SIZE + payload.size, encoded.size)
        val decoded = DaemonProtocol.decodeFrame(encoded)
        assertEquals(DaemonProtocol.CMD_SNAPSHOT_BEGIN, decoded.header.messageType)
        assertEquals(7L, decoded.header.requestId)
        assertEquals(11L, decoded.header.sequence)
        assertEquals(payload.size, decoded.header.payloadLength)
        assertArrayEquals(payload, decoded.payload)

        // Encoding is deterministic, so the daemon sees stable bytes.
        assertArrayEquals(encoded, DaemonProtocol.encodeFrame(header(), payload))
    }

    @Test
    fun `empty payload frame is valid`() {
        val encoded = DaemonProtocol.encodeFrame(header(), ByteArray(0))
        assertEquals(DaemonProtocol.FRAME_HEADER_SIZE, encoded.size)
        val decoded = DaemonProtocol.decodeFrame(encoded)
        assertEquals(0, decoded.header.payloadLength)
        assertEquals(0, decoded.payload.size)
    }

    @Test
    fun `header layout matches the native wire format`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val encoded = DaemonProtocol.encodeFrame(header(messageType = 0x1234, requestId = 0x1122334455667788L, sequence = 9), payload)
        val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)

        // Magic "V4AD" at offset 0.
        assertEquals('V'.code.toByte(), encoded[0])
        assertEquals('4'.code.toByte(), encoded[1])
        assertEquals('A'.code.toByte(), encoded[2])
        assertEquals('D'.code.toByte(), encoded[3])
        assertEquals(DaemonProtocol.PROTOCOL_VERSION, buffer.getShort(4).toInt())
        assertEquals(0x1234, buffer.getShort(6).toInt() and 0xFFFF)
        assertEquals(0, buffer.getInt(8))
        assertEquals(0x1122334455667788L, buffer.getLong(12))
        assertEquals(9L, buffer.getLong(20))
        assertEquals(payload.size, buffer.getInt(28))
        assertEquals(DaemonProtocol.crc32(payload), buffer.getInt(32))
    }

    @Test
    fun `crc32 matches the native polynomial`() {
        // Reference values for the standard CRC-32 used by the native Crc32().
        assertEquals(0, DaemonProtocol.crc32(ByteArray(0)))
        assertEquals(0xCBF43926.toInt(), DaemonProtocol.crc32("123456789".toByteArray()))
        assertEquals(0x414FA339, DaemonProtocol.crc32("The quick brown fox jumps over the lazy dog".toByteArray()))
    }

    @Test
    fun `corrupted payload is rejected by crc`() {
        val payload = ByteArray(64) { it.toByte() }
        val encoded = DaemonProtocol.encodeFrame(header(), payload)
        // Flip a payload byte, leaving the stored CRC stale.
        encoded[DaemonProtocol.FRAME_HEADER_SIZE + 5] = (encoded[DaemonProtocol.FRAME_HEADER_SIZE + 5] + 1).toByte()

        val failure = expectFrameError(encoded)
        assertEquals(DaemonProtocol.FrameError.CRC_MISMATCH, failure)
    }

    @Test
    fun `malformed frames are rejected with distinct reasons`() {
        val payload = ByteArray(32) { it.toByte() }
        val encoded = DaemonProtocol.encodeFrame(header(), payload)

        assertEquals(
            DaemonProtocol.FrameError.FRAME_TOO_SMALL,
            expectFrameError(encoded.copyOfRange(0, DaemonProtocol.FRAME_HEADER_SIZE - 1)),
        )

        val badMagic = encoded.copyOf()
        badMagic[1] = 'X'.code.toByte()
        assertEquals(DaemonProtocol.FrameError.BAD_MAGIC, expectFrameError(badMagic))

        val badVersion = encoded.copyOf()
        ByteBuffer.wrap(badVersion).order(ByteOrder.LITTLE_ENDIAN).putShort(4, 2)
        assertEquals(DaemonProtocol.FrameError.UNSUPPORTED_VERSION, expectFrameError(badVersion))

        val unknownFlags = encoded.copyOf()
        ByteBuffer.wrap(unknownFlags).order(ByteOrder.LITTLE_ENDIAN).putInt(8, 0x10000)
        assertEquals(DaemonProtocol.FrameError.UNKNOWN_FLAGS, expectFrameError(unknownFlags))

        // Declared length longer than the frame body.
        val longLength = encoded.copyOf()
        ByteBuffer.wrap(longLength).order(ByteOrder.LITTLE_ENDIAN).putInt(28, payload.size + 8)
        assertEquals(DaemonProtocol.FrameError.LENGTH_MISMATCH, expectFrameError(longLength))

        // Declared length shorter, leaving trailing bytes.
        val shortLength = encoded.copyOf()
        ByteBuffer.wrap(shortLength).order(ByteOrder.LITTLE_ENDIAN).putInt(28, payload.size - 8)
        assertEquals(DaemonProtocol.FrameError.TRAILING_BYTES, expectFrameError(shortLength))

        // A negative length must not wrap into a small positive size.
        val negativeLength = encoded.copyOf()
        ByteBuffer.wrap(negativeLength).order(ByteOrder.LITTLE_ENDIAN).putInt(28, -1)
        assertEquals(DaemonProtocol.FrameError.PAYLOAD_TOO_LARGE, expectFrameError(negativeLength))

        val hugeLength = encoded.copyOf()
        ByteBuffer.wrap(hugeLength).order(ByteOrder.LITTLE_ENDIAN).putInt(28, DaemonProtocol.MAX_PAYLOAD_SIZE + 1)
        assertEquals(DaemonProtocol.FrameError.PAYLOAD_TOO_LARGE, expectFrameError(hugeLength))
    }

    @Test
    fun `oversized payload is refused at encode time`() {
        val tooBig = ByteArray(DaemonProtocol.MAX_PAYLOAD_SIZE + 1)
        val failure =
            try {
                DaemonProtocol.encodeFrame(header(), tooBig)
                null
            } catch (e: DaemonProtocol.FrameException) {
                e.error
            }
        assertEquals(DaemonProtocol.FrameError.PAYLOAD_TOO_LARGE, failure)
    }

    @Test
    fun `unknown flags are refused at encode time`() {
        val failure =
            try {
                DaemonProtocol.encodeFrame(
                    DaemonProtocol.FrameHeader(messageType = 1, flags = 0x20000),
                    ByteArray(4),
                )
                null
            } catch (e: DaemonProtocol.FrameException) {
                e.error
            }
        assertEquals(DaemonProtocol.FrameError.UNKNOWN_FLAGS, failure)
    }

    @Test
    fun `device key normalization matches the native canonical form`() {
        val identity =
            DaemonProtocol.DeviceIdentity(
                routeType = "  Bluetooth_A2DP ",
                stableAddressOrPort = "AA:BB:CC:DD:EE:FF",
                productName = "  My   Buds  ",
                sampleRate = 44100,
                channelMask = 3,
                encoding = "PCM_16",
            )
        // Lowercased, whitespace collapsed, pipe-delimited, output flags last.
        assertEquals(
            "bluetooth_a2dp|aa:bb:cc:dd:ee:ff|my buds|44100|3|pcm_16|0",
            DaemonProtocol.normalizeDeviceKey(identity),
        )
    }

    @Test
    fun `device key hash is stable sha256 hex`() {
        val key = "speaker|builtin|internal|48000|3|pcm_16|0"
        val hash = DaemonProtocol.hashDeviceKey(key)
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
        // Same key, same hash; a different key must not collide.
        assertEquals(hash, DaemonProtocol.hashDeviceKey(key))
        assertNotEquals(hash, DaemonProtocol.hashDeviceKey("speaker|builtin|internal|44100|3|pcm_16|0"))
        assertEquals("", DaemonProtocol.hashDeviceKey(""))
    }

    @Test
    fun `volatile and malformed identities are rejected`() {
        val base =
            DaemonProtocol.DeviceIdentity(
                routeType = "speaker",
                stableAddressOrPort = "builtin",
                productName = "internal",
                sampleRate = 48000,
                channelMask = 3,
                encoding = "pcm_16",
            )
        assertTrue(DaemonProtocol.isValidDeviceIdentity(base))

        // A session/pid-derived address would change every playback session.
        assertFalse(DaemonProtocol.isValidDeviceIdentity(base.copy(stableAddressOrPort = "session-42")))
        assertFalse(DaemonProtocol.isValidDeviceIdentity(base.copy(stableAddressOrPort = "pid1234")))
        assertFalse(DaemonProtocol.isValidDeviceIdentity(base.copy(stableAddressOrPort = "track-7")))
        assertFalse(DaemonProtocol.isValidDeviceIdentity(base.copy(stableAddressOrPort = "process9")))

        // The pipe is the field delimiter, so it cannot appear inside a field.
        assertFalse(DaemonProtocol.isValidDeviceIdentity(base.copy(productName = "a|b")))
        assertFalse(DaemonProtocol.isValidDeviceIdentity(base.copy(productName = "a\u0001b")))

        assertFalse(DaemonProtocol.isValidDeviceIdentity(base.copy(sampleRate = 0)))
        assertFalse(DaemonProtocol.isValidDeviceIdentity(base.copy(channelMask = 0)))
        assertFalse(DaemonProtocol.isValidDeviceIdentity(base.copy(routeType = "   ")))
        assertFalse(DaemonProtocol.isValidDeviceIdentity(base.copy(encoding = "")))

        // An invalid identity yields no key at all rather than a partial one.
        assertNull(DaemonProtocol.normalizeDeviceKey(base.copy(sampleRate = 0)))
    }

    @Test
    fun `command and event type ranges do not overlap`() {
        val commands =
            listOf(
                DaemonProtocol.CMD_SNAPSHOT_BEGIN,
                DaemonProtocol.CMD_SNAPSHOT_CHUNK,
                DaemonProtocol.CMD_SNAPSHOT_COMMIT,
                DaemonProtocol.CMD_SNAPSHOT_ABORT,
            )
        val events = listOf(DaemonProtocol.EVENT_SNAPSHOT_APPLIED_ACK, DaemonProtocol.EVENT_SNAPSHOT_APPLIED_NACK)
        assertTrue(commands.none { it in events })
        assertTrue(commands.all { it >= 100 })
        assertTrue(events.all { it < 100 })
    }

    private fun expectFrameError(bytes: ByteArray): DaemonProtocol.FrameError? =
        try {
            DaemonProtocol.decodeFrame(bytes)
            null
        } catch (e: DaemonProtocol.FrameException) {
            e.error
        }
}

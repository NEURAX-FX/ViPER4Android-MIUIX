package com.llsl.viper4android.daemon

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Kotlin mirror of the native `@viper4android.driver.v1` / `@viper4android.app.v1`
 * wire format.
 *
 * Byte-for-byte compatible with `protocol/ViperDaemonProtocol.{h,cpp}`,
 * `protocol/SnapshotCommand.{h,cpp}`, `protocol/ParameterStream.{h,cpp}` and
 * `protocol/DeviceKey.cpp` in the ViPERFX_RE repository. Every field is
 * little-endian, every length is explicit, and every payload is CRC32-checked,
 * because the daemon runs as root and must not trust App input.
 */
object DaemonProtocol {
    const val PROTOCOL_VERSION = 1
    const val FRAME_HEADER_SIZE = 36
    const val MAX_FRAME_SIZE = 1024 * 1024
    const val MAX_PAYLOAD_SIZE = MAX_FRAME_SIZE - FRAME_HEADER_SIZE
    const val KNOWN_FRAME_FLAGS = 0x0000FFFF

    const val DRIVER_SOCKET_NAME = "viper4android.driver.v1"
    const val APP_SOCKET_NAME = "viper4android.app.v1"

    private val MAGIC = byteArrayOf('V'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte())

    // Driver-to-daemon event types. Only the apply results are consumed by the App.
    const val EVENT_SNAPSHOT_APPLIED_ACK = 10
    const val EVENT_SNAPSHOT_APPLIED_NACK = 11

    // Daemon-to-driver snapshot commands.
    const val CMD_SNAPSHOT_BEGIN = 100
    const val CMD_SNAPSHOT_CHUNK = 101
    const val CMD_SNAPSHOT_COMMIT = 102
    const val CMD_SNAPSHOT_ABORT = 103

    const val MAX_SNAPSHOT_CHUNK_BYTES = 64 * 1024

    data class FrameHeader(
        val messageType: Int,
        val requestId: Long = 0,
        val sequence: Long = 0,
        val flags: Int = 0,
        val protocolVersion: Int = PROTOCOL_VERSION,
        val payloadLength: Int = 0,
        val payloadCrc32: Int = 0,
    )

    data class Frame(
        val header: FrameHeader,
        val payload: ByteArray,
    ) {
        // ByteArray identity would make frame comparison useless in tests.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return header == other.header && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int = 31 * header.hashCode() + payload.contentHashCode()
    }

    /** Why a frame or payload was refused. Mirrors the native `FrameError`. */
    enum class FrameError {
        PAYLOAD_TOO_LARGE,
        FRAME_TOO_SMALL,
        BAD_MAGIC,
        UNSUPPORTED_VERSION,
        UNKNOWN_FLAGS,
        LENGTH_MISMATCH,
        TRAILING_BYTES,
        CRC_MISMATCH,
    }

    class FrameException(
        val error: FrameError,
    ) : IllegalArgumentException("frame rejected: $error")

    /** Same polynomial and seed as the native `Crc32`. */
    fun crc32(bytes: ByteArray): Int {
        var crc = -1 // 0xFFFFFFFF
        for (byte in bytes) {
            crc = crc xor (byte.toInt() and 0xFF)
            for (bit in 0 until 8) {
                val mask = -(crc and 1)
                crc = (crc ushr 1) xor (0xEDB88320.toInt() and mask)
            }
        }
        return crc.inv()
    }

    fun encodeFrame(
        header: FrameHeader,
        payload: ByteArray,
    ): ByteArray {
        if (payload.size > MAX_PAYLOAD_SIZE) throw FrameException(FrameError.PAYLOAD_TOO_LARGE)
        if (header.protocolVersion != PROTOCOL_VERSION) {
            throw FrameException(FrameError.UNSUPPORTED_VERSION)
        }
        if (header.flags and KNOWN_FRAME_FLAGS.inv() != 0) {
            throw FrameException(FrameError.UNKNOWN_FLAGS)
        }

        val buffer =
            ByteBuffer
                .allocate(FRAME_HEADER_SIZE + payload.size)
                .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(MAGIC)
        buffer.putShort(header.protocolVersion.toShort())
        buffer.putShort(header.messageType.toShort())
        buffer.putInt(header.flags)
        buffer.putLong(header.requestId)
        buffer.putLong(header.sequence)
        buffer.putInt(payload.size)
        buffer.putInt(crc32(payload))
        buffer.put(payload)
        return buffer.array()
    }

    fun decodeFrame(bytes: ByteArray): Frame {
        if (bytes.size < FRAME_HEADER_SIZE) throw FrameException(FrameError.FRAME_TOO_SMALL)
        for (index in MAGIC.indices) {
            if (bytes[index] != MAGIC[index]) throw FrameException(FrameError.BAD_MAGIC)
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(MAGIC.size)
        val version = buffer.short.toInt() and 0xFFFF
        val messageType = buffer.short.toInt() and 0xFFFF
        val flags = buffer.int
        val requestId = buffer.long
        val sequence = buffer.long
        val payloadLength = buffer.int
        val payloadCrc = buffer.int

        if (version != PROTOCOL_VERSION) throw FrameException(FrameError.UNSUPPORTED_VERSION)
        if (flags and KNOWN_FRAME_FLAGS.inv() != 0) throw FrameException(FrameError.UNKNOWN_FLAGS)
        // A negative length means the peer declared more than 2 GiB; treat it as
        // oversized rather than letting it wrap into a small positive size.
        if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_SIZE) {
            throw FrameException(FrameError.PAYLOAD_TOO_LARGE)
        }

        val expectedSize = FRAME_HEADER_SIZE + payloadLength
        if (bytes.size < expectedSize) throw FrameException(FrameError.LENGTH_MISMATCH)
        if (bytes.size > expectedSize) throw FrameException(FrameError.TRAILING_BYTES)

        val payload = bytes.copyOfRange(FRAME_HEADER_SIZE, expectedSize)
        if (crc32(payload) != payloadCrc) throw FrameException(FrameError.CRC_MISMATCH)

        return Frame(
            FrameHeader(
                messageType = messageType,
                requestId = requestId,
                sequence = sequence,
                flags = flags,
                protocolVersion = version,
                payloadLength = payloadLength,
                payloadCrc32 = payloadCrc,
            ),
            payload,
        )
    }

    /** Normalized route identity; mirrors the native `DeviceIdentity`. */
    data class DeviceIdentity(
        val routeType: String,
        val stableAddressOrPort: String,
        val productName: String,
        val sampleRate: Int,
        val channelMask: Int,
        val encoding: String,
        val outputFlags: Int = 0,
    )

    private val VOLATILE_TOKENS = listOf("session", "process", "pid", "track")

    private fun normalizeField(value: String): String {
        val builder = StringBuilder(value.length)
        var pendingSpace = false
        for (char in value) {
            if (char.isWhitespace()) {
                pendingSpace = builder.isNotEmpty()
                continue
            }
            if (pendingSpace) builder.append(' ')
            pendingSpace = false
            builder.append(char.lowercaseChar())
        }
        return builder.toString().trimEnd()
    }

    private fun hasInvalidDelimiter(value: String): Boolean =
        value.contains('|') || value.any { it.code < 0x20 || it.code == 0x7F }

    fun isValidDeviceIdentity(identity: DeviceIdentity): Boolean {
        if (identity.sampleRate == 0 || identity.channelMask == 0) return false
        if (normalizeField(identity.routeType).isEmpty()) return false
        if (normalizeField(identity.stableAddressOrPort).isEmpty()) return false
        if (normalizeField(identity.productName).isEmpty()) return false
        if (normalizeField(identity.encoding).isEmpty()) return false
        if (hasInvalidDelimiter(identity.routeType)) return false
        if (hasInvalidDelimiter(identity.stableAddressOrPort)) return false
        if (hasInvalidDelimiter(identity.productName)) return false
        if (hasInvalidDelimiter(identity.encoding)) return false
        // A volatile identity would make the snapshot key change every session.
        val lowerAddress = identity.stableAddressOrPort.lowercase()
        return VOLATILE_TOKENS.none { lowerAddress.contains(it) }
    }

    /** Returns the canonical key, or null when the identity is not usable. */
    fun normalizeDeviceKey(identity: DeviceIdentity): String? {
        if (!isValidDeviceIdentity(identity)) return null
        return listOf(
            normalizeField(identity.routeType),
            normalizeField(identity.stableAddressOrPort),
            normalizeField(identity.productName),
            identity.sampleRate.toString(),
            identity.channelMask.toString(),
            normalizeField(identity.encoding),
            identity.outputFlags.toString(),
        ).joinToString("|")
    }

    fun hashDeviceKey(normalizedKey: String): String {
        if (normalizedKey.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(normalizedKey.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

package com.llsl.viper4android.daemon

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Kotlin mirror of the `@viper4android.app.v1` payload codecs.
 *
 * Byte-for-byte compatible with `protocol/AppCommand.{h,cpp}` in the ViPERFX_RE
 * repository. Framing is shared with [DaemonProtocol]; only the payloads live
 * here. Snapshot streaming on this socket reuses [DaemonSnapshotCodec] unchanged.
 *
 * The decoders reject exactly what the native decoders reject. The daemon runs as
 * root and caches whatever route the App reports, so a field that survives one
 * side's validation but not the other's would produce divergent state.
 */
object AppProtocol {
    const val PROTOCOL_VERSION = 1
    const val APP_SOCKET_NAME = DaemonProtocol.APP_SOCKET_NAME

    const val MSG_APP_HELLO = 200
    const val MSG_APP_HELLO_ACK = 201
    const val MSG_APP_ROUTE_REPORT = 202
    const val MSG_APP_ROUTE_ACK = 203
    const val MSG_APP_APPLY_RESULT = 204

    const val APP_HELLO_WIRE_SIZE = 24
    const val APP_HELLO_ACK_WIRE_SIZE = 96
    const val APP_ROUTE_REPORT_HEADER_SIZE = 32
    const val APP_ROUTE_ACK_WIRE_SIZE = 96
    const val APP_APPLY_RESULT_WIRE_SIZE = 48

    // Bounded so a buggy App cannot make the root daemon allocate freely.
    const val MAX_APP_ROUTE_FIELD_BYTES = 256
    const val APP_DEVICE_HASH_SIZE = 64

    const val FLAG_RESTORE_ENABLED = 1 shl 0
    const val FLAG_DRIVER_CONNECTED = 1 shl 1
    const val FLAG_ROUTE_KNOWN = 1 shl 2

    fun isAppMessageType(value: Int): Boolean = value in MSG_APP_HELLO..MSG_APP_APPLY_RESULT

    data class AppHello(
        val appGeneration: Long,
        val version: Int = PROTOCOL_VERSION,
    )

    data class AppHelloAck(
        val flags: Int,
        val daemonGeneration: Long,
        val routeEpoch: Long,
        /** Empty when the daemon has no route yet. */
        val routeKeyHash: String,
        val version: Int = PROTOCOL_VERSION,
    ) {
        val restoreEnabled: Boolean get() = flags and FLAG_RESTORE_ENABLED != 0
        val driverConnected: Boolean get() = flags and FLAG_DRIVER_CONNECTED != 0
        val routeKnown: Boolean get() = flags and FLAG_ROUTE_KNOWN != 0
    }

    data class AppRouteReport(
        val routeType: String,
        val stableAddressOrPort: String,
        val productName: String,
        val encoding: String,
        val sampleRate: Int,
        val channelMask: Int,
        val outputFlags: Int = 0,
        val version: Int = PROTOCOL_VERSION,
    )

    data class AppRouteAck(
        val accepted: Boolean,
        val daemonGeneration: Long,
        val routeEpoch: Long,
        val routeKeyHash: String,
    )

    data class AppApplyResult(
        val accepted: Boolean,
        val errorCode: Int,
        val appGeneration: Long,
        val daemonGeneration: Long,
        val resourceGeneration: Long,
        val graphGeneration: Long,
    )

    fun encodeAppHello(hello: AppHello): ByteArray {
        if (hello.version != PROTOCOL_VERSION) {
            throw DaemonSnapshotCodec.CodecException("unsupported app protocol version")
        }
        val buffer = allocate(APP_HELLO_WIRE_SIZE)
        buffer.putShort(hello.version.toShort())
        buffer.putShort(0)
        buffer.putLong(hello.appGeneration)
        buffer.putLong(0)
        buffer.putInt(0)
        return buffer.array()
    }

    fun decodeAppHello(bytes: ByteArray): AppHello {
        val buffer = reader(bytes, APP_HELLO_WIRE_SIZE, "app hello size mismatch")
        val version = readVersion(buffer)
        requireReservedZero(readU16(buffer) == 0)
        val appGeneration = buffer.long
        requireReservedZero(buffer.long == 0L && buffer.int == 0)
        return AppHello(appGeneration = appGeneration, version = version)
    }

    fun encodeAppHelloAck(ack: AppHelloAck): ByteArray {
        requireHash(ack.routeKeyHash)
        val buffer = allocate(APP_HELLO_ACK_WIRE_SIZE)
        buffer.putShort(ack.version.toShort())
        buffer.putShort(ack.flags.toShort())
        buffer.putLong(ack.daemonGeneration)
        buffer.putLong(ack.routeEpoch)
        buffer.putLong(0)
        buffer.putInt(0)
        putHash(buffer, ack.routeKeyHash)
        return buffer.array()
    }

    fun decodeAppHelloAck(bytes: ByteArray): AppHelloAck {
        val buffer = reader(bytes, APP_HELLO_ACK_WIRE_SIZE, "app hello ack size mismatch")
        val version = readVersion(buffer)
        val flags = readU16(buffer)
        val daemonGeneration = buffer.long
        val routeEpoch = buffer.long
        requireReservedZero(buffer.long == 0L && buffer.int == 0)
        return AppHelloAck(
            flags = flags,
            daemonGeneration = daemonGeneration,
            routeEpoch = routeEpoch,
            routeKeyHash = readHash(buffer),
            version = version,
        )
    }

    fun encodeAppRouteReport(report: AppRouteReport): ByteArray {
        if (report.version != PROTOCOL_VERSION) {
            throw DaemonSnapshotCodec.CodecException("unsupported app protocol version")
        }
        val routeType = routeField(report.routeType)
        val address = routeField(report.stableAddressOrPort)
        val product = routeField(report.productName)
        val encoding = routeField(report.encoding)
        requireRouteFormat(report.sampleRate, report.channelMask)

        val buffer =
            allocate(
                APP_ROUTE_REPORT_HEADER_SIZE + routeType.size + address.size +
                    product.size + encoding.size,
            )
        buffer.putShort(report.version.toShort())
        buffer.putShort(0)
        buffer.putInt(report.sampleRate)
        buffer.putInt(report.channelMask)
        buffer.putInt(report.outputFlags)
        buffer.putInt(routeType.size)
        buffer.putInt(address.size)
        buffer.putInt(product.size)
        buffer.putInt(encoding.size)
        buffer.put(routeType)
        buffer.put(address)
        buffer.put(product)
        buffer.put(encoding)
        return buffer.array()
    }

    fun decodeAppRouteReport(bytes: ByteArray): AppRouteReport {
        if (bytes.size < APP_ROUTE_REPORT_HEADER_SIZE) {
            throw DaemonSnapshotCodec.CodecException("app route report is truncated")
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = readVersion(buffer)
        requireReservedZero(readU16(buffer) == 0)
        val sampleRate = buffer.int
        val channelMask = buffer.int
        val outputFlags = buffer.int
        val routeTypeSize = readFieldLength(buffer)
        val addressSize = readFieldLength(buffer)
        val productSize = readFieldLength(buffer)
        val encodingSize = readFieldLength(buffer)
        val total = routeTypeSize + addressSize + productSize + encodingSize
        if (bytes.size != APP_ROUTE_REPORT_HEADER_SIZE + total) {
            throw DaemonSnapshotCodec.CodecException("app route report length mismatch")
        }

        val report =
            AppRouteReport(
                routeType = readField(buffer, routeTypeSize),
                stableAddressOrPort = readField(buffer, addressSize),
                productName = readField(buffer, productSize),
                encoding = readField(buffer, encodingSize),
                sampleRate = sampleRate,
                channelMask = channelMask,
                outputFlags = outputFlags,
                version = version,
            )
        routeField(report.routeType)
        routeField(report.stableAddressOrPort)
        routeField(report.productName)
        routeField(report.encoding)
        requireRouteFormat(sampleRate, channelMask)
        return report
    }

    fun encodeAppRouteAck(ack: AppRouteAck): ByteArray {
        requireHash(ack.routeKeyHash)
        val buffer = allocate(APP_ROUTE_ACK_WIRE_SIZE)
        buffer.putShort(if (ack.accepted) 1 else 0)
        buffer.putShort(0)
        buffer.putLong(ack.daemonGeneration)
        buffer.putLong(ack.routeEpoch)
        buffer.putLong(0)
        buffer.putInt(0)
        putHash(buffer, ack.routeKeyHash)
        return buffer.array()
    }

    fun decodeAppRouteAck(bytes: ByteArray): AppRouteAck {
        val buffer = reader(bytes, APP_ROUTE_ACK_WIRE_SIZE, "app route ack size mismatch")
        val accepted = readU16(buffer) != 0
        requireReservedZero(readU16(buffer) == 0)
        val daemonGeneration = buffer.long
        val routeEpoch = buffer.long
        requireReservedZero(buffer.long == 0L && buffer.int == 0)
        return AppRouteAck(
            accepted = accepted,
            daemonGeneration = daemonGeneration,
            routeEpoch = routeEpoch,
            routeKeyHash = readHash(buffer),
        )
    }

    fun encodeAppApplyResult(result: AppApplyResult): ByteArray {
        val buffer = allocate(APP_APPLY_RESULT_WIRE_SIZE)
        buffer.putShort(if (result.accepted) 1 else 0)
        buffer.putShort(0)
        buffer.putInt(result.errorCode)
        buffer.putLong(result.appGeneration)
        buffer.putLong(result.daemonGeneration)
        buffer.putLong(result.resourceGeneration)
        buffer.putLong(result.graphGeneration)
        buffer.putLong(0)
        return buffer.array()
    }

    fun decodeAppApplyResult(bytes: ByteArray): AppApplyResult {
        val buffer = reader(bytes, APP_APPLY_RESULT_WIRE_SIZE, "app apply result size mismatch")
        val accepted = readU16(buffer) != 0
        requireReservedZero(readU16(buffer) == 0)
        val result =
            AppApplyResult(
                accepted = accepted,
                errorCode = buffer.int,
                appGeneration = buffer.long,
                daemonGeneration = buffer.long,
                resourceGeneration = buffer.long,
                graphGeneration = buffer.long,
            )
        requireReservedZero(buffer.long == 0L)
        return result
    }

    private fun allocate(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun reader(
        bytes: ByteArray,
        expectedSize: Int,
        sizeError: String,
    ): ByteBuffer {
        if (bytes.size != expectedSize) throw DaemonSnapshotCodec.CodecException(sizeError)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun readU16(buffer: ByteBuffer): Int = buffer.short.toInt() and 0xFFFF

    private fun readVersion(buffer: ByteBuffer): Int {
        val version = readU16(buffer)
        if (version != PROTOCOL_VERSION) {
            throw DaemonSnapshotCodec.CodecException("unsupported app protocol version")
        }
        return version
    }

    private fun requireReservedZero(isZero: Boolean) {
        if (!isZero) throw DaemonSnapshotCodec.CodecException("reserved field must be zero")
    }

    private fun requireRouteFormat(
        sampleRate: Int,
        channelMask: Int,
    ) {
        if (sampleRate == 0 || channelMask == 0) {
            throw DaemonSnapshotCodec.CodecException("route format fields must be non-zero")
        }
    }

    private fun isLowerHexHash(value: String): Boolean =
        value.length == APP_DEVICE_HASH_SIZE && value.all { it in '0'..'9' || it in 'a'..'f' }

    private fun requireHash(hash: String) {
        if (hash.isNotEmpty() && !isLowerHexHash(hash)) {
            throw DaemonSnapshotCodec.CodecException(
                "route_key_hash must be 64 lowercase hex characters",
            )
        }
    }

    // A hash field is fixed width on the wire, so an absent hash is all zero bytes
    // rather than a length prefix. "No route yet" must stay distinguishable from a
    // real hash, or the App would cache an empty key as if it were valid.
    private fun putHash(
        buffer: ByteBuffer,
        hash: String,
    ) {
        for (index in 0 until APP_DEVICE_HASH_SIZE) {
            buffer.put(if (index < hash.length) hash[index].code.toByte() else 0)
        }
    }

    private fun readHash(buffer: ByteBuffer): String {
        val raw = ByteArray(APP_DEVICE_HASH_SIZE)
        buffer.get(raw)
        val terminator = raw.indexOf(0)
        val length = if (terminator < 0) raw.size else terminator
        val hash = String(raw, 0, length, Charsets.ISO_8859_1)
        requireHash(hash)
        return hash
    }

    /** Validates a route field the way the native `ValidateRouteField` does. */
    private fun routeField(value: String): ByteArray {
        val bytes = value.toByteArray()
        if (bytes.isEmpty()) {
            throw DaemonSnapshotCodec.CodecException("route field must not be empty")
        }
        if (bytes.size > MAX_APP_ROUTE_FIELD_BYTES) {
            throw DaemonSnapshotCodec.CodecException("route field is too large")
        }
        for (byte in bytes) {
            val code = byte.toInt() and 0xFF
            // '|' is the device-key delimiter; a field carrying it could forge a key.
            if (code == '|'.code) {
                throw DaemonSnapshotCodec.CodecException("route field contains the key delimiter")
            }
            if (code < 0x20 || code == 0x7F) {
                throw DaemonSnapshotCodec.CodecException(
                    "route field contains a control character",
                )
            }
        }
        return bytes
    }

    private fun readFieldLength(buffer: ByteBuffer): Int {
        val size = buffer.int
        // A negative length means the peer declared more than 2 GiB; the native
        // decoder sees an unsigned value above the bound and rejects it too.
        if (size < 0 || size > MAX_APP_ROUTE_FIELD_BYTES) {
            throw DaemonSnapshotCodec.CodecException("route field is too large")
        }
        return size
    }

    private fun readField(
        buffer: ByteBuffer,
        size: Int,
    ): String {
        val bytes = ByteArray(size)
        buffer.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}

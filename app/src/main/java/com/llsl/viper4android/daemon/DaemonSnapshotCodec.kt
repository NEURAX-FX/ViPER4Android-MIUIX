package com.llsl.viper4android.daemon

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Kotlin mirror of `protocol/SnapshotSchema.{h,cpp}`, `protocol/ParameterStream.{h,cpp}`
 * and `protocol/SnapshotCommand.{h,cpp}`.
 *
 * The App produces snapshots; the daemon stores them and the driver applies them,
 * so every encoder here must agree byte-for-byte with the native decoders.
 */
object DaemonSnapshotCodec {
    const val SNAPSHOT_SCHEMA_VERSION = 1
    const val SNAPSHOT_HEADER_SIZE = 80
    const val MAX_SNAPSHOT_SIZE = 4 * 1024 * 1024
    const val MAX_PARAMETER_BYTES = 1024 * 1024
    const val MAX_SNAPSHOT_STRING = 4096
    const val MAX_SNAPSHOT_RESOURCES = 128

    const val PARAMETER_STREAM_VERSION = 1
    const val PARAMETER_STREAM_HEADER_SIZE = 12
    const val PARAMETER_RECORD_HEADER_SIZE = 24
    const val MAX_PARAMETER_RECORDS = 512
    const val MAX_RECORD_PAYLOAD_BYTES = 8192

    const val SNAPSHOT_BEGIN_WIRE_SIZE = 96
    const val SNAPSHOT_CHUNK_HEADER_SIZE = 16
    const val SNAPSHOT_COMMIT_WIRE_SIZE = 24
    const val SNAPSHOT_ABORT_WIRE_SIZE = 16

    private val SNAPSHOT_MAGIC =
        byteArrayOf('V'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), 'S'.code.toByte())
    private val PARAM_MAGIC =
        byteArrayOf('V'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), 'P'.code.toByte())

    // Raw parameter ids that consume an array payload. Kept in sync with
    // `RequiredPayloadBytes` in protocol/ParameterStream.cpp.
    private const val PARAM_CONVOLVER_SET_BUFFER = 0x101B3
    private const val PARAM_DDC_COEFFICIENTS = 0x101C1
    private const val PARAM_EQUALIZER_BAND_LEVELS = 0x101A3

    class CodecException(
        message: String,
    ) : IllegalArgumentException(message)

    data class RawParamRecord(
        val param: Int,
        val val1: Int = 0,
        val val2: Int = 0,
        val val3: Int = 0,
        val arrSize: Int = 0,
        val payload: ByteArray = ByteArray(0),
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RawParamRecord) return false
            return param == other.param &&
                val1 == other.val1 &&
                val2 == other.val2 &&
                val3 == other.val3 &&
                arrSize == other.arrSize &&
                payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = param
            result = 31 * result + val1
            result = 31 * result + val2
            result = 31 * result + val3
            result = 31 * result + arrSize
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    data class ResourceReference(
        val resourceId: String,
        val contentSha256: String,
        val size: Long = 0,
        val kind: Int = 0,
        val format: Int = 0,
        val channels: Int = 0,
        val order: Int = 0,
    )

    data class Snapshot(
        val deviceKey: String,
        val deviceKeyHash: String,
        val bootId: Long,
        val daemonGeneration: Long,
        val appGeneration: Long,
        val createdAtMillis: Long,
        val masterEnabled: Boolean,
        val globalMode: Boolean = false,
        val parameters: ByteArray = ByteArray(0),
        val iemParameters: ByteArray = ByteArray(0),
        val resources: List<ResourceReference> = emptyList(),
        val resourceGeneration: Long = 0,
        val graphGeneration: Long = 0,
        val schemaVersion: Int = SNAPSHOT_SCHEMA_VERSION,
        val driverProtocolVersion: Int = DaemonProtocol.PROTOCOL_VERSION,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Snapshot) return false
            return deviceKey == other.deviceKey &&
                deviceKeyHash == other.deviceKeyHash &&
                bootId == other.bootId &&
                daemonGeneration == other.daemonGeneration &&
                appGeneration == other.appGeneration &&
                createdAtMillis == other.createdAtMillis &&
                masterEnabled == other.masterEnabled &&
                globalMode == other.globalMode &&
                parameters.contentEquals(other.parameters) &&
                iemParameters.contentEquals(other.iemParameters) &&
                resources == other.resources &&
                resourceGeneration == other.resourceGeneration &&
                graphGeneration == other.graphGeneration &&
                schemaVersion == other.schemaVersion &&
                driverProtocolVersion == other.driverProtocolVersion
        }

        override fun hashCode(): Int {
            var result = deviceKey.hashCode()
            result = 31 * result + deviceKeyHash.hashCode()
            result = 31 * result + bootId.hashCode()
            result = 31 * result + daemonGeneration.hashCode()
            result = 31 * result + appGeneration.hashCode()
            result = 31 * result + createdAtMillis.hashCode()
            result = 31 * result + masterEnabled.hashCode()
            result = 31 * result + parameters.contentHashCode()
            return result
        }
    }

    /** Bytes the driver will read for `param` given `arrSize`; 0 means scalar. */
    fun requiredPayloadBytes(
        param: Int,
        arrSize: Int,
    ): Int =
        when (param) {
            PARAM_CONVOLVER_SET_BUFFER -> arrSize * 4
            // Two coefficient banks (44100/48000) of arrSize floats each.
            PARAM_DDC_COEFFICIENTS -> arrSize * 2 * 4
            PARAM_EQUALIZER_BAND_LEVELS -> arrSize * 4
            else -> 0
        }

    fun validateRawParamRecord(record: RawParamRecord) {
        if (record.payload.size > MAX_RECORD_PAYLOAD_BYTES) {
            throw CodecException("parameter record payload is too large")
        }
        if (record.arrSize < 0 || record.arrSize > MAX_RECORD_PAYLOAD_BYTES) {
            throw CodecException("parameter record arr_size is out of range")
        }
        val required = requiredPayloadBytes(record.param, record.arrSize)
        if (required == 0) {
            if (record.payload.isNotEmpty() || record.arrSize != 0) {
                throw CodecException("scalar parameter must not carry an array payload")
            }
            return
        }
        if (required > MAX_RECORD_PAYLOAD_BYTES) {
            throw CodecException("parameter record requires more payload than the driver accepts")
        }
        // The driver reads `required` bytes unconditionally; a short payload would
        // make it read past the buffer.
        if (record.payload.size != required) {
            throw CodecException("parameter record payload does not match arr_size")
        }
    }

    fun encodeParameterStream(records: List<RawParamRecord>): ByteArray {
        if (records.size > MAX_PARAMETER_RECORDS) throw CodecException("too many parameter records")
        var total = PARAMETER_STREAM_HEADER_SIZE
        for (record in records) {
            validateRawParamRecord(record)
            total += PARAMETER_RECORD_HEADER_SIZE + record.payload.size
        }
        if (total > MAX_PARAMETER_BYTES) throw CodecException("parameter stream is too large")

        val buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(PARAM_MAGIC)
        buffer.putShort(PARAMETER_STREAM_VERSION.toShort())
        buffer.putShort(0) // reserved
        buffer.putInt(records.size)
        for (record in records) {
            buffer.putInt(record.param)
            buffer.putInt(record.val1)
            buffer.putInt(record.val2)
            buffer.putInt(record.val3)
            buffer.putInt(record.arrSize)
            buffer.putInt(record.payload.size)
            buffer.put(record.payload)
        }
        return buffer.array()
    }

    fun decodeParameterStream(bytes: ByteArray): List<RawParamRecord> {
        // An absent stream is valid and means "no parameters".
        if (bytes.isEmpty()) return emptyList()
        if (bytes.size > MAX_PARAMETER_BYTES) throw CodecException("parameter stream is too large")
        if (bytes.size < PARAMETER_STREAM_HEADER_SIZE) {
            throw CodecException("parameter stream is truncated")
        }
        for (index in PARAM_MAGIC.indices) {
            if (bytes[index] != PARAM_MAGIC[index]) {
                throw CodecException("bad parameter stream magic")
            }
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(PARAM_MAGIC.size)
        if ((buffer.short.toInt() and 0xFFFF) != PARAMETER_STREAM_VERSION) {
            throw CodecException("unsupported parameter stream version")
        }
        if ((buffer.short.toInt() and 0xFFFF) != 0) {
            throw CodecException("reserved parameter stream field must be zero")
        }
        val count = buffer.int
        if (count < 0 || count > MAX_PARAMETER_RECORDS) {
            throw CodecException("too many parameter records")
        }

        val records = ArrayList<RawParamRecord>(count)
        repeat(count) {
            if (buffer.remaining() < PARAMETER_RECORD_HEADER_SIZE) {
                throw CodecException("parameter record header is truncated")
            }
            val param = buffer.int
            val val1 = buffer.int
            val val2 = buffer.int
            val val3 = buffer.int
            val arrSize = buffer.int
            val payloadLength = buffer.int
            if (payloadLength < 0 || payloadLength > MAX_RECORD_PAYLOAD_BYTES) {
                throw CodecException("parameter record payload is too large")
            }
            if (buffer.remaining() < payloadLength) {
                throw CodecException("parameter record payload is truncated")
            }
            val payload = ByteArray(payloadLength)
            buffer.get(payload)
            val record = RawParamRecord(param, val1, val2, val3, arrSize, payload)
            validateRawParamRecord(record)
            records.add(record)
        }
        if (buffer.hasRemaining()) throw CodecException("parameter stream has trailing bytes")
        return records
    }

    private fun requireLowerHex64(
        value: String,
        field: String,
    ) {
        if (value.length != 64 || value.any { it !in '0'..'9' && it !in 'a'..'f' }) {
            throw CodecException("$field must be 64 lowercase hex characters")
        }
    }

    fun validateSnapshot(snapshot: Snapshot) {
        if (snapshot.schemaVersion != SNAPSHOT_SCHEMA_VERSION) {
            throw CodecException("unsupported snapshot schema")
        }
        if (snapshot.driverProtocolVersion != DaemonProtocol.PROTOCOL_VERSION) {
            throw CodecException("unsupported driver protocol version")
        }
        if (snapshot.deviceKey.isEmpty() || snapshot.deviceKey.length > MAX_SNAPSHOT_STRING) {
            throw CodecException("device_key is invalid")
        }
        if (!snapshot.deviceKey.contains('|')) throw CodecException("device_key is not canonical")
        requireLowerHex64(snapshot.deviceKeyHash, "device_key_hash")
        if (DaemonProtocol.hashDeviceKey(snapshot.deviceKey) != snapshot.deviceKeyHash) {
            throw CodecException("device_key_hash mismatch")
        }
        if (snapshot.bootId == 0L) throw CodecException("boot_id must be non-zero")
        if (snapshot.daemonGeneration == 0L) throw CodecException("daemon_generation must be non-zero")
        if (snapshot.appGeneration == 0L) throw CodecException("app_generation must be non-zero")
        if (snapshot.createdAtMillis == 0L) throw CodecException("created_at_millis must be non-zero")
        if (snapshot.parameters.size > MAX_PARAMETER_BYTES ||
            snapshot.iemParameters.size > MAX_PARAMETER_BYTES
        ) {
            throw CodecException("parameter payload is too large")
        }
        if (snapshot.resources.size > MAX_SNAPSHOT_RESOURCES) {
            throw CodecException("too many resources")
        }
        val ids = HashSet<String>()
        val hashes = HashSet<String>()
        for (resource in snapshot.resources) {
            if (resource.resourceId.isEmpty() || resource.resourceId.length > MAX_SNAPSHOT_STRING) {
                throw CodecException("resource_id is invalid")
            }
            requireLowerHex64(resource.contentSha256, "resource content_sha256")
            if (!ids.add(resource.resourceId)) throw CodecException("duplicate resource_id")
            if (!hashes.add(resource.contentSha256)) throw CodecException("duplicate resource hash")
        }
    }

    fun encodeSnapshot(snapshot: Snapshot): ByteArray {
        validateSnapshot(snapshot)

        val deviceKeyBytes = snapshot.deviceKey.toByteArray()
        val deviceHashBytes = snapshot.deviceKeyHash.toByteArray()
        var size =
            SNAPSHOT_HEADER_SIZE + deviceKeyBytes.size + deviceHashBytes.size +
                snapshot.parameters.size + snapshot.iemParameters.size
        for (resource in snapshot.resources) {
            size += 32 + resource.resourceId.toByteArray().size + resource.contentSha256.toByteArray().size
        }
        if (size > MAX_SNAPSHOT_SIZE) throw CodecException("snapshot is too large")

        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(SNAPSHOT_MAGIC)
        buffer.putShort(snapshot.schemaVersion.toShort())
        buffer.putShort(snapshot.driverProtocolVersion.toShort())
        buffer.putLong(snapshot.bootId)
        buffer.putLong(snapshot.daemonGeneration)
        buffer.putLong(snapshot.appGeneration)
        buffer.putLong(snapshot.createdAtMillis)
        buffer.put(if (snapshot.masterEnabled) 1 else 0)
        buffer.put(if (snapshot.globalMode) 1 else 0)
        buffer.putShort(0) // reserved
        buffer.putInt(deviceKeyBytes.size)
        buffer.putInt(deviceHashBytes.size)
        buffer.putInt(snapshot.parameters.size)
        buffer.putInt(snapshot.iemParameters.size)
        buffer.putInt(snapshot.resources.size)
        buffer.putLong(snapshot.resourceGeneration)
        buffer.putLong(snapshot.graphGeneration)
        // Header ends here: 4 + 2 + 2 + 32 + 4 + 20 + 16 == SNAPSHOT_HEADER_SIZE.
        buffer.put(deviceKeyBytes)
        buffer.put(deviceHashBytes)
        buffer.put(snapshot.parameters)
        buffer.put(snapshot.iemParameters)
        for (resource in snapshot.resources) {
            val idBytes = resource.resourceId.toByteArray()
            val hashBytes = resource.contentSha256.toByteArray()
            buffer.putInt(idBytes.size)
            buffer.putInt(hashBytes.size)
            buffer.putLong(resource.size)
            buffer.putInt(resource.kind)
            buffer.putInt(resource.format)
            buffer.putInt(resource.channels)
            buffer.putInt(resource.order)
            buffer.put(idBytes)
            buffer.put(hashBytes)
        }
        val encoded = buffer.array()
        if (buffer.position() != encoded.size) {
            throw CodecException("snapshot encoder size mismatch")
        }
        return encoded
    }

    data class SnapshotBegin(
        val appGeneration: Long,
        val daemonGeneration: Long,
        val totalSize: Int,
        val crc32: Int,
        val deviceKeyHash: String,
        val version: Int = 1,
    )

    fun encodeSnapshotBegin(begin: SnapshotBegin): ByteArray {
        if (begin.version != 1) throw CodecException("unsupported snapshot command version")
        requireLowerHex64(begin.deviceKeyHash, "device_key_hash")
        if (begin.totalSize <= 0 || begin.totalSize > MAX_SNAPSHOT_SIZE) {
            throw CodecException("snapshot size is out of range")
        }
        if (begin.appGeneration == 0L || begin.daemonGeneration == 0L) {
            throw CodecException("snapshot generations must be non-zero")
        }

        val buffer = ByteBuffer.allocate(SNAPSHOT_BEGIN_WIRE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(begin.version.toShort())
        buffer.putShort(0) // reserved
        buffer.putInt(begin.totalSize)
        buffer.putLong(begin.appGeneration)
        buffer.putLong(begin.daemonGeneration)
        buffer.putInt(begin.crc32)
        buffer.putInt(0) // reserved
        buffer.put(begin.deviceKeyHash.toByteArray())
        return buffer.array()
    }

    fun encodeSnapshotChunk(
        offset: Int,
        data: ByteArray,
    ): ByteArray {
        if (data.isEmpty() || data.size > DaemonProtocol.MAX_SNAPSHOT_CHUNK_BYTES) {
            throw CodecException("snapshot chunk size is out of range")
        }
        if (offset < 0) throw CodecException("snapshot chunk offset is negative")
        val buffer =
            ByteBuffer
                .allocate(SNAPSHOT_CHUNK_HEADER_SIZE + data.size)
                .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(offset)
        buffer.putInt(data.size)
        buffer.putLong(0) // reserved
        buffer.put(data)
        return buffer.array()
    }

    fun encodeSnapshotCommit(
        appGeneration: Long,
        daemonGeneration: Long,
    ): ByteArray {
        if (appGeneration == 0L || daemonGeneration == 0L) {
            throw CodecException("snapshot generations must be non-zero")
        }
        val buffer = ByteBuffer.allocate(SNAPSHOT_COMMIT_WIRE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putLong(appGeneration)
        buffer.putLong(daemonGeneration)
        buffer.putLong(0) // reserved
        return buffer.array()
    }

    fun encodeSnapshotAbort(reason: Int): ByteArray {
        val buffer = ByteBuffer.allocate(SNAPSHOT_ABORT_WIRE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(reason)
        buffer.putInt(0) // reserved
        buffer.putLong(0) // reserved
        return buffer.array()
    }

    /** Splits a snapshot into `SNAPSHOT_CHUNK` payloads in the required order. */
    fun chunkSnapshot(
        bytes: ByteArray,
        chunkSize: Int = DaemonProtocol.MAX_SNAPSHOT_CHUNK_BYTES,
    ): List<ByteArray> {
        if (chunkSize <= 0 || chunkSize > DaemonProtocol.MAX_SNAPSHOT_CHUNK_BYTES) {
            throw CodecException("chunk size is out of range")
        }
        val chunks = ArrayList<ByteArray>((bytes.size + chunkSize - 1) / chunkSize)
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            chunks.add(encodeSnapshotChunk(offset, bytes.copyOfRange(offset, end)))
            offset = end
        }
        return chunks
    }
}

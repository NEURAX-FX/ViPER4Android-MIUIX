package com.llsl.viper4android.daemon

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Kotlin mirror of the driver event payload in `protocol/DriverEvent.{h,cpp}`.
 *
 * The App only consumes `SNAPSHOT_APPLIED_ACK`/`NACK`, but the whole 72-byte
 * record is decoded so a size or field-order drift fails loudly instead of
 * silently misreading a generation.
 */
data class DaemonDriverEvent(
    val type: Int,
    val enabled: Boolean,
    val bootId: Long,
    val eventSequence: Long,
    val contextInstanceId: Long,
    val audioSessionId: Int,
    val ioId: Int,
    val sampleRate: Int,
    val channelMask: Int,
    val sessionGeneration: Long,
    val resourceGeneration: Long,
    val graphGeneration: Long,
    val bypassReason: Int,
) {
    companion object {
        const val WIRE_SIZE = 72

        fun decode(payload: ByteArray): DaemonDriverEvent {
            if (payload.size != WIRE_SIZE) {
                throw DaemonSnapshotCodec.CodecException(
                    "driver event size mismatch: ${payload.size} != $WIRE_SIZE",
                )
            }
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            val type = buffer.short.toInt() and 0xFFFF
            val enabled = (buffer.short.toInt() and 0xFFFF) != 0
            return DaemonDriverEvent(
                type = type,
                enabled = enabled,
                bootId = buffer.long,
                eventSequence = buffer.long,
                contextInstanceId = buffer.long,
                audioSessionId = buffer.int,
                ioId = buffer.int,
                sampleRate = buffer.int,
                channelMask = buffer.int,
                sessionGeneration = buffer.long,
                resourceGeneration = buffer.long,
                graphGeneration = buffer.long,
                bypassReason = buffer.int,
            )
        }
    }
}

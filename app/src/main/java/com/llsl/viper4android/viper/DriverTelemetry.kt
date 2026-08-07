package com.llsl.viper4android.viper

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DriverTelemetry(
    val sequence: Int,
    val sampleRate: Int,
    val fftSize: Int,
    val validMask: Int,
    val overrunCount: Int,
    val spectrumDb: List<Float>,
    val meterDb: List<Float>,
) {
    val hasSpectrum: Boolean
        get() = validMask and SPECTRUM_VALID != 0

    val hasMeters: Boolean
        get() = validMask and METERS_VALID != 0

    companion object {
        const val VERSION = 1
        const val SPECTRUM_COUNT = 64
        const val METER_COUNT = 8
        const val WIRE_SIZE = 320
        const val SPECTRUM_VALID = 1 shl 0
        const val METERS_VALID = 1 shl 1

        fun parse(payload: ByteArray): DriverTelemetry? {
            if (payload.size != WIRE_SIZE) return null
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.int != VERSION) return null

            val sequence = buffer.int
            val sampleRate = buffer.int
            val fftSize = buffer.int
            val spectrumCount = buffer.int
            val meterCount = buffer.int
            val validMask = buffer.int
            val overrunCount = buffer.int
            if (sampleRate <= 0 || fftSize <= 0) return null
            if (spectrumCount != SPECTRUM_COUNT || meterCount != METER_COUNT) return null

            val spectrum = List(SPECTRUM_COUNT) { buffer.float }
            val meters = List(METER_COUNT) { buffer.float }
            if (spectrum.any { !it.isFinite() } || meters.any { !it.isFinite() }) return null

            return DriverTelemetry(
                sequence = sequence,
                sampleRate = sampleRate,
                fftSize = fftSize,
                validMask = validMask,
                overrunCount = overrunCount,
                spectrumDb = spectrum,
                meterDb = meters,
            )
        }
    }
}

fun mergeDriverTelemetry(
    current: DriverTelemetry?,
    next: DriverTelemetry?,
): DriverTelemetry? =
    when {
        next == null -> null
        current?.sequence == next.sequence -> current
        else -> next
    }

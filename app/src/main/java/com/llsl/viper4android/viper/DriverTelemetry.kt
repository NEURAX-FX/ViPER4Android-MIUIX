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

data class IemDriverTelemetry(
    val sequence: Long,
    val processedFrames: Long,
    val latestProcessNs: Long,
    val averageProcessNs: Long,
    val maxProcessNs: Long,
    val deadlineMisses: Long,
    val outputUnderflows: Long,
    val inputOverflows: Long,
    val outputOverflows: Long,
    val grainPoolExhaustions: Long,
    val graphGeneration: Long,
    val hostSampleRate: Int,
    val internalSampleRate: Int,
    val latencyFrames: Int,
    val bypassReason: Int,
    val enabled: Boolean,
    val prepared: Boolean,
    val activeGrains: Int,
    val encoderMode: Int,
    val renderMode: Int,
    val ambisonicsOrder: Int,
    val haloPrepared: Boolean,
    val haloStftLatencyFrames: Int,
    val dialogNetResult: Int,
    val faultCode: Int,
    val preparationResult: Int,
    val latencyMs: Float,
    val limiterGainReductionDb: Float,
    val audioSessionId: Int,
    val session0Active: Boolean,
    val session0CacheGeneration: Long,
    val contextInstanceId: Long,
    val session0LiveContextCount: Int,
) {
    companion object {
        const val VERSION = 4
        const val WIRE_SIZE = 200

        fun parse(payload: ByteArray): IemDriverTelemetry? {
            if (payload.size != WIRE_SIZE) return null
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.int != VERSION || buffer.int != WIRE_SIZE) return null
            val telemetry =
                IemDriverTelemetry(
                    sequence = buffer.long,
                    processedFrames = buffer.long,
                    latestProcessNs = buffer.long,
                    averageProcessNs = buffer.long,
                    maxProcessNs = buffer.long,
                    deadlineMisses = buffer.long,
                    outputUnderflows = buffer.long,
                    inputOverflows = buffer.long,
                    outputOverflows = buffer.long,
                    grainPoolExhaustions = buffer.long,
                    graphGeneration = buffer.long,
                    hostSampleRate = buffer.int,
                    internalSampleRate = buffer.int,
                    latencyFrames = buffer.int,
                    bypassReason = buffer.int,
                    enabled = buffer.int != 0,
                    prepared = buffer.int != 0,
                    activeGrains = buffer.int,
                    encoderMode = buffer.int,
                    renderMode = buffer.int,
                    ambisonicsOrder = buffer.int,
                    haloPrepared = buffer.int != 0,
                    haloStftLatencyFrames = buffer.int,
                    dialogNetResult = buffer.int,
                    faultCode = buffer.int,
                    preparationResult = buffer.int,
                    latencyMs = buffer.float,
                    limiterGainReductionDb = buffer.float,
                    audioSessionId = buffer.int,
                    session0Active = buffer.int != 0,
                    session0CacheGeneration = buffer.long,
                    contextInstanceId = buffer.long,
                    session0LiveContextCount = buffer.int,
                )
            buffer.int // reserved_session0
            if (telemetry.hostSampleRate <= 0 || telemetry.internalSampleRate != 96_000) return null
            if (telemetry.encoderMode !in 0..3 || telemetry.renderMode !in 0..2 || telemetry.ambisonicsOrder !in 1..3) return null
            if (!telemetry.latencyMs.isFinite() || !telemetry.limiterGainReductionDb.isFinite()) return null
            return telemetry
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

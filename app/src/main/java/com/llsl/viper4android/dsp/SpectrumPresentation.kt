package com.llsl.viper4android.dsp

import androidx.compose.ui.geometry.Offset
import com.llsl.viper4android.viper.DriverTelemetry
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

internal const val SPECTRUM_FLOOR_DB = -96f
private const val SPECTRUM_CEILING_DB = 0f
internal const val SPECTRUM_DISPLAY_MIN_DB = -72.0
internal const val SPECTRUM_DISPLAY_MAX_DB = 24.0
private const val SPECTRUM_ATTACK_SECONDS = 0.015
private const val SPECTRUM_RELEASE_SECONDS = 0.300
private const val SPECTRUM_STALE_GRACE_NANOS = 150_000_000L
private const val SPECTRUM_PEAK_HOLD_NANOS = 500_000_000L
private const val SPECTRUM_PEAK_DECAY_DB_PER_SECOND = 20f
private const val SPECTRUM_FLOOR_SNAP_DB = 0.05f

internal class SpectrumBallisticsState(
    val sampleRate: Int = 0,
    val sequence: Int? = null,
    val targetDb: FloatArray = FloatArray(DriverTelemetry.SPECTRUM_COUNT) { SPECTRUM_FLOOR_DB },
    val envelopeDb: FloatArray = FloatArray(DriverTelemetry.SPECTRUM_COUNT) { SPECTRUM_FLOOR_DB },
    val peakDb: FloatArray = FloatArray(DriverTelemetry.SPECTRUM_COUNT) { SPECTRUM_FLOOR_DB },
    val peakHoldUntilNanos: LongArray = LongArray(DriverTelemetry.SPECTRUM_COUNT),
    val lastInputNanos: Long? = null,
    val lastFrameNanos: Long? = null,
    val hasInput: Boolean = false,
)

internal fun advanceSpectrumBallistics(
    previous: SpectrumBallisticsState,
    telemetry: DriverTelemetry?,
    frameTimeNanos: Long,
): SpectrumBallisticsState {
    val validSpectrum =
        telemetry?.hasSpectrum == true &&
            telemetry.sampleRate > 0 &&
            telemetry.spectrumDb.size == DriverTelemetry.SPECTRUM_COUNT
    if (!previous.hasInput && !validSpectrum) return previous

    val sampleRateChanged = validSpectrum && previous.hasInput && telemetry.sampleRate != previous.sampleRate
    val base = if (sampleRateChanged) SpectrumBallisticsState() else previous
    val hasNewInput = validSpectrum && telemetry.sequence != base.sequence
    val lastInputNanos = if (hasNewInput) frameTimeNanos else base.lastInputNanos
    val hasInvalidInput = telemetry != null && !validSpectrum
    val isStale =
        hasInvalidInput ||
            lastInputNanos == null ||
            frameTimeNanos - lastInputNanos > SPECTRUM_STALE_GRACE_NANOS
    val isSettled =
        base.targetDb.all { it == SPECTRUM_FLOOR_DB } &&
            base.envelopeDb.all { it == SPECTRUM_FLOOR_DB } &&
            base.peakDb.all { it == SPECTRUM_FLOOR_DB }
    if (!hasNewInput && isSettled) return base
    val target =
        when {
            hasNewInput -> {
                FloatArray(DriverTelemetry.SPECTRUM_COUNT) { index ->
                    sanitizeSpectrumDb(telemetry.spectrumDb[index])
                }
            }

            isStale -> FloatArray(DriverTelemetry.SPECTRUM_COUNT) { SPECTRUM_FLOOR_DB }
            else -> base.targetDb.copyOf()
        }
    val elapsedSeconds =
        if (hasNewInput && isSettled) {
            0.0
        } else {
            base.lastFrameNanos
                ?.let { ((frameTimeNanos - it).coerceAtLeast(0L) / 1_000_000_000.0) }
                ?: 0.0
        }
    val envelope = base.envelopeDb.copyOf()
    if (elapsedSeconds > 0.0) {
        val attackAmount = (1.0 - exp(-elapsedSeconds / SPECTRUM_ATTACK_SECONDS)).toFloat()
        val releaseAmount = (1.0 - exp(-elapsedSeconds / SPECTRUM_RELEASE_SECONDS)).toFloat()
        for (index in envelope.indices) {
            val amount = if (target[index] > envelope[index]) attackAmount else releaseAmount
            envelope[index] += (target[index] - envelope[index]) * amount
            if (
                target[index] == SPECTRUM_FLOOR_DB &&
                envelope[index] - SPECTRUM_FLOOR_DB <= SPECTRUM_FLOOR_SNAP_DB
            ) {
                envelope[index] = SPECTRUM_FLOOR_DB
            }
        }
    }
    val peaks = base.peakDb.copyOf()
    val peakHoldUntilNanos = base.peakHoldUntilNanos.copyOf()
    for (index in peaks.indices) {
        if (envelope[index] > peaks[index]) {
            peaks[index] = envelope[index]
            peakHoldUntilNanos[index] = frameTimeNanos + SPECTRUM_PEAK_HOLD_NANOS
        } else if (frameTimeNanos > peakHoldUntilNanos[index]) {
            val decayStart = max(base.lastFrameNanos ?: frameTimeNanos, peakHoldUntilNanos[index])
            val decaySeconds = (frameTimeNanos - decayStart).coerceAtLeast(0L) / 1_000_000_000f
            peaks[index] =
                max(
                    envelope[index],
                    peaks[index] - SPECTRUM_PEAK_DECAY_DB_PER_SECOND * decaySeconds,
                )
        }
    }

    return SpectrumBallisticsState(
        sampleRate = if (validSpectrum) telemetry.sampleRate else base.sampleRate,
        sequence = if (hasNewInput) telemetry.sequence else base.sequence,
        targetDb = target,
        envelopeDb = envelope,
        peakDb = peaks,
        peakHoldUntilNanos = peakHoldUntilNanos,
        lastInputNanos = lastInputNanos,
        lastFrameNanos = frameTimeNanos,
        hasInput = base.hasInput || validSpectrum,
    )
}

internal fun spectrumBandCenterFrequency(
    index: Int,
    count: Int,
    sampleRate: Int,
): Double {
    require(count > 0) { "count must be positive" }
    require(index in 0 until count) { "index must be within the band count" }
    require(sampleRate > 0) { "sampleRate must be positive" }
    val maximumFrequency = minOf(20_000.0, sampleRate / 2.0).coerceAtLeast(20.0)
    val centerExponent = (index + 0.5) / count.toDouble()
    return 20.0 * (maximumFrequency / 20.0).pow(centerExponent)
}

fun fitSpectrumDb(
    buckets: List<Float>,
    subdivisions: Int = 3,
): List<Float> = fitSpectrumDb(buckets.toFloatArray(), subdivisions).asList()

private fun fitSpectrumDb(
    buckets: FloatArray,
    subdivisions: Int,
): FloatArray {
    if (buckets.isEmpty()) return FloatArray(0)
    if (buckets.size == 1) return floatArrayOf(sanitizeSpectrumDb(buckets.single()))
    val steps = subdivisions.coerceAtLeast(1)
    val values = FloatArray(buckets.size) { index -> sanitizeSpectrumDb(buckets[index]) }
    val deltas = FloatArray(values.lastIndex) { index -> values[index + 1] - values[index] }
    val tangents = FloatArray(values.size)
    tangents[0] = deltas.first()
    tangents[tangents.lastIndex] = deltas.last()
    for (index in 1 until values.lastIndex) {
        val before = deltas[index - 1]
        val after = deltas[index]
        tangents[index] =
            if (before == 0f || after == 0f || before * after <= 0f) {
                0f
            } else {
                2f * before * after / (before + after)
            }
    }

    val fitted = FloatArray(values.lastIndex * steps + 1)
    for (segment in 0 until values.lastIndex) {
        val start = values[segment]
        val end = values[segment + 1]
        val minimum = minOf(start, end)
        val maximum = maxOf(start, end)
        for (step in 0 until steps) {
            val t = step.toFloat() / steps
            val t2 = t * t
            val t3 = t2 * t
            val value =
                (2f * t3 - 3f * t2 + 1f) * start +
                    (t3 - 2f * t2 + t) * tangents[segment] +
                    (-2f * t3 + 3f * t2) * end +
                    (t3 - t2) * tangents[segment + 1]
            fitted[segment * steps + step] = value.coerceIn(minimum, maximum)
        }
    }
    fitted[fitted.lastIndex] = values.last()
    return fitted
}

internal fun spectrumCurvePoints(
    buckets: List<Float>,
    sampleRate: Int,
    minDb: Double = SPECTRUM_DISPLAY_MIN_DB,
    maxDb: Double = SPECTRUM_DISPLAY_MAX_DB,
    subdivisions: Int = 3,
): List<Offset> = spectrumCurvePoints(buckets.toFloatArray(), sampleRate, minDb, maxDb, subdivisions)

internal fun spectrumCurvePoints(
    buckets: FloatArray,
    sampleRate: Int,
    minDb: Double = SPECTRUM_DISPLAY_MIN_DB,
    maxDb: Double = SPECTRUM_DISPLAY_MAX_DB,
    subdivisions: Int = 3,
): List<Offset> {
    if (buckets.isEmpty()) return emptyList()
    val steps = subdivisions.coerceAtLeast(1)
    val fitted = fitSpectrumDb(buckets, steps)
    val bandXs =
        FloatArray(buckets.size) { index ->
            graphFrequencyToX(
                frequency = spectrumBandCenterFrequency(index, buckets.size, sampleRate),
                sampleRate = sampleRate,
            )
        }
    if (fitted.size == 1) {
        return listOf(Offset(bandXs.single(), graphDbToY(fitted.single().toDouble(), minDb, maxDb)))
    }
    return List(fitted.size) { index ->
        val db = fitted[index]
        if (index == fitted.lastIndex) {
            Offset(bandXs.last(), graphDbToY(db.toDouble(), minDb, maxDb))
        } else {
            val segment = index / steps
            val progress = (index % steps).toFloat() / steps
            val x = bandXs[segment] + (bandXs[segment + 1] - bandXs[segment]) * progress
            Offset(x, graphDbToY(db.toDouble(), minDb, maxDb))
        }
    }
}

private fun sanitizeSpectrumDb(value: Float): Float =
    if (value.isFinite()) {
        value.coerceIn(SPECTRUM_FLOOR_DB, SPECTRUM_CEILING_DB)
    } else {
        SPECTRUM_FLOOR_DB
    }

package com.llsl.viper4android.dsp

import androidx.compose.ui.geometry.Offset
import kotlin.math.ln
import kotlin.math.pow

/**
 * Shared sampling layer between the driver-sourced response models and every graph.
 *
 * The main screen preview and the dedicated editors must draw the same data. Anything that
 * samples a response goes through here so the two can never drift apart again.
 */

/** Default sample count. Dense enough that straight segments read as a smooth curve. */
const val DEFAULT_CURVE_RESOLUTION = 192

/** Normalized x for [frequency] on the log axis used by every graph. */
fun graphFrequencyToX(frequency: Double, sampleRate: Int): Float {
    val max = graphMaxFrequency(sampleRate)
    val span = ln(max / GRAPH_MIN_FREQUENCY)
    return (ln(frequency / GRAPH_MIN_FREQUENCY) / span).toFloat().coerceIn(0f, 1f)
}

/** Frequency in Hz for normalized [x] on the log axis. */
fun graphXToFrequency(x: Float, sampleRate: Int): Double {
    val max = graphMaxFrequency(sampleRate)
    return GRAPH_MIN_FREQUENCY * (max / GRAPH_MIN_FREQUENCY).pow(x.coerceIn(0f, 1f).toDouble())
}

/** Normalized y for [db], flipped so louder is nearer the top. */
fun graphDbToY(db: Double, minDb: Double, maxDb: Double): Float {
    require(maxDb > minDb) { "maxDb must exceed minDb" }
    return ((maxDb - db) / (maxDb - minDb)).toFloat().coerceIn(0f, 1f)
}

/** Inverse of [graphDbToY]. */
fun graphYToDb(y: Float, minDb: Double, maxDb: Double): Double {
    require(maxDb > minDb) { "maxDb must exceed minDb" }
    return maxDb - y.coerceIn(0f, 1f).toDouble() * (maxDb - minDb)
}

private inline fun sampleCurve(
    sampleRate: Int,
    minDb: Double,
    maxDb: Double,
    resolution: Int,
    magnitudeDb: (Double) -> Double,
): List<Offset> {
    require(resolution >= 2) { "resolution must be at least 2, was $resolution" }
    return List(resolution) { index ->
        val x = index / (resolution - 1).toFloat()
        val frequency = graphXToFrequency(x, sampleRate)
        Offset(x, graphDbToY(magnitudeDb(frequency), minDb, maxDb))
    }
}

/** Samples the FIR equalizer's parallel-IIR response. */
fun firCurvePoints(
    bandCount: Int,
    gainsDb: List<Double>,
    sampleRate: Int,
    minDb: Double,
    maxDb: Double,
    resolution: Int = DEFAULT_CURVE_RESOLUTION,
): List<Offset> {
    if (gainsDb.isEmpty()) return emptyList()
    require(resolution >= 2) { "resolution must be at least 2, was $resolution" }
    val response = FirEqualizerResponse(bandCount, sanitizeGraphSampleRate(sampleRate))
    return sampleCurve(sampleRate, minDb, maxDb, resolution) { frequency ->
        response.magnitudeDb(gainsDb, frequency)
    }
}

/**
 * Samples the Dynamic EQ's *target* response.
 *
 * The driver scales each band by an envelope follower at runtime, so this is the maximum
 * the band can reach, not the instantaneous response.
 */
fun dynamicEqCurvePoints(
    bands: List<DynamicEqBandSpec>,
    sampleRate: Int,
    minDb: Double,
    maxDb: Double,
    resolution: Int = DEFAULT_CURVE_RESOLUTION,
): List<Offset> {
    if (bands.isEmpty()) return emptyList()
    require(resolution >= 2) { "resolution must be at least 2, was $resolution" }
    val response = DynamicEqResponse(sanitizeGraphSampleRate(sampleRate))
    return sampleCurve(sampleRate, minDb, maxDb, resolution) { frequency ->
        response.magnitudeDb(bands, frequency)
    }
}

/**
 * Samples one curve per multiband compressor band.
 *
 * This is the static crossover structure only. Returns an empty list when the crossovers
 * cannot be evaluated at the current sample rate, so callers fall back to plain band
 * regions instead of drawing something wrong.
 */
fun multibandCrossoverCurves(
    crossovers: List<Double>,
    sampleRate: Int,
    minDb: Double,
    maxDb: Double,
    bandGainsDb: List<Double> = emptyList(),
    resolution: Int = DEFAULT_CURVE_RESOLUTION,
): List<List<Offset>> {
    require(resolution >= 2) { "resolution must be at least 2, was $resolution" }
    val safeRate = sanitizeGraphSampleRate(sampleRate)
    val response = try {
        MultibandCrossoverResponse(crossovers, safeRate)
    } catch (invalid: IllegalArgumentException) {
        return emptyList()
    }
    return (0 until response.bandCount).map { band ->
        sampleCurve(safeRate, minDb, maxDb, resolution) { frequency ->
            response.bandMagnitudeDb(band, frequency) + bandGainsDb.getOrElse(band) { 0.0 }
        }
    }
}

fun multibandCrossoverSumCurve(
    crossovers: List<Double>,
    sampleRate: Int,
    minDb: Double,
    maxDb: Double,
    resolution: Int = DEFAULT_CURVE_RESOLUTION,
): List<Offset> {
    require(resolution >= 2) { "resolution must be at least 2, was $resolution" }
    val safeRate = sanitizeGraphSampleRate(sampleRate)
    val response =
        try {
            MultibandCrossoverResponse(crossovers, safeRate)
        } catch (invalid: IllegalArgumentException) {
            return emptyList()
        }
    return sampleCurve(safeRate, minDb, maxDb, resolution, response::sumMagnitudeDb)
}

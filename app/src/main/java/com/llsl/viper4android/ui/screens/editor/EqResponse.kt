package com.llsl.viper4android.ui.screens.editor

import androidx.compose.ui.geometry.Offset
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** One equalizer band: a fixed center [frequency] in Hz with a [gainDb] offset. */
data class EqBand(
    val frequency: Double,
    val gainDb: Double,
)

private const val TRANSITION_OCTAVES = 0.5

/** Chosen so that a distance of one half-width lands on half the band gain. */
private val LOG_HALF_SCALE = ln(2.0)

private fun log2(value: Double): Double = ln(value) / ln(2.0)

private fun smoothStep(t: Double): Double {
    val x = t.coerceIn(0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)
}

/**
 * Weight of a single band at [frequency], in 0..1.
 *
 * Mirrors the band layout used by LSP's graphic equalizer: the first band behaves as a
 * low shelf, the last as a high shelf, and every band in between as a ladder pass whose
 * plateau spans the geometric means with its neighbours.
 */
private fun bandWeight(
    bands: List<EqBand>,
    index: Int,
    frequency: Double,
): Double {
    val octave = log2(frequency)
    val lowerEdge = if (index == 0) {
        null
    } else {
        log2(sqrt(bands[index - 1].frequency * bands[index].frequency))
    }
    val upperEdge = if (index == bands.lastIndex) {
        null
    } else {
        log2(sqrt(bands[index].frequency * bands[index + 1].frequency))
    }

    if (lowerEdge != null && octave < lowerEdge) {
        return smoothStep(1.0 - (lowerEdge - octave) / TRANSITION_OCTAVES)
    }
    if (upperEdge != null && octave > upperEdge) {
        return smoothStep(1.0 - (octave - upperEdge) / TRANSITION_OCTAVES)
    }
    return 1.0
}

/** Combined magnitude of the whole band set at [frequency], in dB. */
fun equalizerMagnitudeDb(
    bands: List<EqBand>,
    frequency: Double,
): Double {
    if (bands.isEmpty()) return 0.0
    if (bands.size == 1) return bands[0].gainDb
    require(frequency > 0.0) { "frequency must be positive" }
    var total = 0.0
    bands.forEachIndexed { index, band ->
        if (band.gainDb != 0.0) {
            total += band.gainDb * bandWeight(bands, index, frequency)
        }
    }
    return total
}

/**
 * One parametric band: a bell centred on [frequency] with [gainDb] of boost/cut and
 * bandwidth controlled by [q].
 */
data class ParametricBand(
    val frequency: Double,
    val gainDb: Double,
    val q: Double,
)

/**
 * Combined magnitude of a parametric band set at [frequency], in dB.
 *
 * Each band uses a Gaussian bell in log-frequency space whose half-gain width follows
 * 1/Q, which tracks the perceived shape of a peaking biquad closely enough for a preview
 * while staying cheap enough to sample per frame.
 */
fun parametricMagnitudeDb(
    bands: List<ParametricBand>,
    frequency: Double,
): Double {
    if (bands.isEmpty()) return 0.0
    require(frequency > 0.0) { "frequency must be positive" }
    val octave = log2(frequency)
    var total = 0.0
    bands.forEach { band ->
        if (band.gainDb == 0.0) return@forEach
        require(band.frequency > 0.0) { "band frequency must be positive" }
        val halfWidth = 1.0 / band.q.coerceAtLeast(0.05)
        val distance = (octave - log2(band.frequency)) / halfWidth
        total += band.gainDb * exp(-LOG_HALF_SCALE * distance * distance)
    }
    return total
}

/** Samples [parametricMagnitudeDb] into normalized graph coordinates. */
fun parametricCurvePoints(
    bands: List<ParametricBand>,
    minFrequency: Double,
    maxFrequency: Double,
    minDb: Double,
    maxDb: Double,
    resolution: Int = 96,
): List<Offset> {
    if (bands.isEmpty()) return emptyList()
    require(resolution >= 2) { "resolution must be at least 2" }
    return List(resolution) { index ->
        val x = index / (resolution - 1).toFloat()
        val frequency = xToFrequency(x, minFrequency, maxFrequency)
        Offset(x, dbToY(parametricMagnitudeDb(bands, frequency), minDb, maxDb))
    }
}

/**
 * Samples the combined response into normalized graph coordinates, with x on a
 * logarithmic frequency axis and y flipped so that boosts sit near the top.
 */
fun equalizerCurvePoints(
    bands: List<EqBand>,
    minFrequency: Double,
    maxFrequency: Double,
    minDb: Double,
    maxDb: Double,
    resolution: Int = 96,
): List<Offset> {
    if (bands.isEmpty()) return emptyList()
    require(resolution >= 2) { "resolution must be at least 2" }
    return List(resolution) { index ->
        val x = index / (resolution - 1).toFloat()
        val frequency = xToFrequency(x, minFrequency, maxFrequency)
        Offset(x, dbToY(equalizerMagnitudeDb(bands, frequency), minDb, maxDb))
    }
}

/**
 * Structural band regions for a multiband effect, derived only from its crossover
 * frequencies. This is the honest geometry for a multiband compressor graph: it shows
 * where each band lives on the frequency axis without pretending to be a magnitude
 * response.
 */
fun mbcBandRegions(
    crossovers: List<Double>,
    minFrequency: Double,
    maxFrequency: Double,
): List<GraphBandRegion> {
    require(minFrequency > 0.0 && maxFrequency > minFrequency)
    val boundaries = listOf(minFrequency) + crossovers.sorted() + listOf(maxFrequency)
    return boundaries.zipWithNext { start, end ->
        GraphBandRegion(
            startX = frequencyToX(start, minFrequency, maxFrequency),
            endX = frequencyToX(end, minFrequency, maxFrequency),
        )
    }
}

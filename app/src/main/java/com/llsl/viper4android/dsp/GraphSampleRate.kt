package com.llsl.viper4android.dsp

import com.llsl.viper4android.effect.MULTIBAND_MAX_FREQUENCY
import kotlin.math.floor

/**
 * Sample rate the graph falls back to when the driver has not reported one yet.
 *
 * The driver returns 0 for its sampling-rate parameter before the first stream is attached,
 * and the preview still has to draw something. 48 kHz is the most common Android output
 * rate, so it is the least surprising placeholder.
 */
const val DEFAULT_GRAPH_SAMPLE_RATE = 48_000

/** Lowest frequency shown on the graph axis. */
const val GRAPH_MIN_FREQUENCY = 20.0

/** Highest frequency shown when Nyquist allows the full audible range. */
const val GRAPH_NOMINAL_MAX_FREQUENCY = 20_000.0

private const val PLAUSIBLE_MIN_SAMPLE_RATE = 4_000
private const val PLAUSIBLE_MAX_SAMPLE_RATE = 768_000

/** Keeps the graph away from the exact Nyquist point where the response is undefined. */
private const val NYQUIST_MARGIN = 0.96

/**
 * Clamps a driver-reported sample rate into something the response models can use.
 *
 * `0` means "driver has not told us yet"; anything outside a plausible audio range is
 * treated the same way rather than propagating a nonsensical rate into filter coefficients.
 */
fun sanitizeGraphSampleRate(reported: Int): Int =
    if (reported in PLAUSIBLE_MIN_SAMPLE_RATE..PLAUSIBLE_MAX_SAMPLE_RATE) {
        reported
    } else {
        DEFAULT_GRAPH_SAMPLE_RATE
    }

/**
 * Upper edge of the frequency axis for [sampleRate].
 *
 * Low sample rates put Nyquist below 20 kHz, and the response evaluators reject frequencies
 * at or above Nyquist. Shrinking the axis is honest: there is genuinely no response to show
 * above Nyquist.
 */
fun graphMaxFrequency(sampleRate: Int): Double {
    val safeRate = sanitizeGraphSampleRate(sampleRate)
    val nyquistLimit = safeRate / 2.0 * NYQUIST_MARGIN
    return minOf(GRAPH_NOMINAL_MAX_FREQUENCY, nyquistLimit)
}

fun safeMultibandCrossoverMax(sampleRate: Int): Int =
    minOf(MULTIBAND_MAX_FREQUENCY, floor(graphMaxFrequency(sampleRate)).toInt())

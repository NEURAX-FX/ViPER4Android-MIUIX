package com.llsl.viper4android.dsp

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphSampleRateTest {
    @Test
    fun validRatesArePassedThrough() {
        assertEquals(48_000, sanitizeGraphSampleRate(48_000))
        assertEquals(44_100, sanitizeGraphSampleRate(44_100))
        assertEquals(192_000, sanitizeGraphSampleRate(192_000))
        assertEquals(8_000, sanitizeGraphSampleRate(8_000))
    }

    @Test
    fun unreportedRateFallsBackToTheDefault() {
        assertEquals(DEFAULT_GRAPH_SAMPLE_RATE, sanitizeGraphSampleRate(0))
        assertEquals(DEFAULT_GRAPH_SAMPLE_RATE, sanitizeGraphSampleRate(-1))
    }

    @Test
    fun implausibleRatesFallBackToTheDefault() {
        assertEquals(DEFAULT_GRAPH_SAMPLE_RATE, sanitizeGraphSampleRate(100))
        assertEquals(DEFAULT_GRAPH_SAMPLE_RATE, sanitizeGraphSampleRate(2_000_000))
    }

    @Test
    fun graphUpperFrequencyStaysBelowNyquist() {
        assertEquals(20_000.0, graphMaxFrequency(48_000), 0.001)
        assertEquals(20_000.0, graphMaxFrequency(44_100), 0.001)
        // At 8 kHz Nyquist is 4 kHz, so the axis must shrink instead of asking the
        // response model for a frequency it cannot evaluate.
        assertEquals(3_840.0, graphMaxFrequency(8_000), 0.001)
        assertEquals(7_680.0, graphMaxFrequency(16_000), 0.001)
    }

    @Test
    fun graphUpperFrequencyAlwaysExceedsTheLowerBound() {
        listOf(8_000, 16_000, 44_100, 48_000, 96_000).forEach { rate ->
            val max = graphMaxFrequency(rate)
            assert(max > GRAPH_MIN_FREQUENCY) { "max $max must exceed ${GRAPH_MIN_FREQUENCY}" }
            assert(max < rate / 2.0) { "max $max must stay below Nyquist of $rate" }
        }
    }

    @Test
    fun multibandCrossoverMaximumRespectsGraphNyquistAndCanonicalLimit() {
        assertEquals(3_840, safeMultibandCrossoverMax(8_000))
        assertEquals(15_360, safeMultibandCrossoverMax(32_000))
        assertEquals(16_000, safeMultibandCrossoverMax(48_000))
        assertEquals(16_000, safeMultibandCrossoverMax(0))
    }
}

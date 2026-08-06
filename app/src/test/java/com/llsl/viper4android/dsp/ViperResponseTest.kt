package com.llsl.viper4android.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden tests pinning the Kotlin response model to the ViPER driver.
 *
 * Expected values were produced by compiling the driver's own DSP sources, feeding a unit
 * impulse through them and measuring the magnitude response with a DFT. See
 * `ViPERFX_RE/ViPERDSP/viper/utils/MinPhaseIIRCoeffs.cpp`,
 * `ViPERFX_RE/ViPERDSP/viper/effects/IIRFilter.cpp` and
 * `ViPERFX_RE/ViPERDSP/viper/utils/MultiBiquad.cpp`.
 *
 * Tolerance is 0.02 dB, which is far tighter than anything visible on a graph but loose
 * enough to absorb float/double rounding differences between the two implementations.
 */
class ViperResponseTest {
    private val tolerance = 0.02

    @Test
    fun firFlatResponseMatchesDriver() {
        val response = FirEqualizerResponse(bandCount = 10, sampleRate = 48_000)
        val gains = List(10) { 0.0 }

        assertEquals(-3.458208, response.magnitudeDb(gains, 20.0), tolerance)
        assertEquals(-1.794552, response.magnitudeDb(gains, 100.0), tolerance)
        assertEquals(-1.830466, response.magnitudeDb(gains, 200.0), tolerance)
        assertEquals(-0.675229, response.magnitudeDb(gains, 1000.0), tolerance)
        assertEquals(-0.655271, response.magnitudeDb(gains, 4000.0), tolerance)
        assertEquals(-2.266529, response.magnitudeDb(gains, 16000.0), tolerance)
    }

    @Test
    fun firSingleBoostedBandMatchesDriver() {
        val response = FirEqualizerResponse(bandCount = 10, sampleRate = 48_000)
        val gains = List(10) { if (it == 5) 12.0 else 0.0 }

        assertEquals(-3.147695, response.magnitudeDb(gains, 20.0), tolerance)
        assertEquals(-0.587148, response.magnitudeDb(gains, 200.0), tolerance)
        assertEquals(9.008553, response.magnitudeDb(gains, 1000.0), tolerance)
        assertEquals(0.601883, response.magnitudeDb(gains, 4000.0), tolerance)
        assertEquals(-2.004272, response.magnitudeDb(gains, 16000.0), tolerance)
    }

    @Test
    fun firMixedGainsAt44100MatchDriver() {
        val response = FirEqualizerResponse(bandCount = 10, sampleRate = 44_100)
        val gains = listOf(6.0, -6.0, 0.0, 0.0, 3.0, 0.0, 0.0, -3.0, 0.0, 9.0)

        assertEquals(-0.108810, response.magnitudeDb(gains, 20.0), tolerance)
        assertEquals(-2.336504, response.magnitudeDb(gains, 100.0), tolerance)
        assertEquals(-0.242218, response.magnitudeDb(gains, 1000.0), tolerance)
        assertEquals(-1.860439, response.magnitudeDb(gains, 4000.0), tolerance)
        assertEquals(5.397730, response.magnitudeDb(gains, 16000.0), tolerance)
    }

    @Test
    fun firThirtyOneBandsMatchDriver() {
        val response = FirEqualizerResponse(bandCount = 31, sampleRate = 48_000)
        val gains = List(31) { if (it == 17) 10.0 else 0.0 }

        assertEquals(1.354082, response.magnitudeDb(gains, 20.0), tolerance)
        assertEquals(0.386781, response.magnitudeDb(gains, 100.0), tolerance)
        assertEquals(7.651622, response.magnitudeDb(gains, 1000.0), tolerance)
        assertEquals(0.337333, response.magnitudeDb(gains, 4000.0), tolerance)
        assertEquals(-0.497868, response.magnitudeDb(gains, 16000.0), tolerance)
    }

    @Test
    fun firSampleRateChangesTheResponse() {
        val gains = List(10) { if (it == 9) 9.0 else 0.0 }
        val at48k = FirEqualizerResponse(10, 48_000).magnitudeDb(gains, 16000.0)
        val at44k = FirEqualizerResponse(10, 44_100).magnitudeDb(gains, 16000.0)

        assertTrue("sample rate must affect the curve", kotlin.math.abs(at48k - at44k) > 0.05)
    }

    @Test
    fun peakBiquadMatchesDriver() {
        val filter = BiquadResponse.peak(gainDb = 9.0, frequency = 1000.0, q = 1.0, sampleRate = 48_000)

        assertEquals(0.004268, filter.magnitudeDb(20.0), tolerance)
        assertEquals(0.433608, filter.magnitudeDb(200.0), tolerance)
        assertEquals(9.000000, filter.magnitudeDb(1000.0), tolerance)
        assertEquals(0.404849, filter.magnitudeDb(5000.0), tolerance)
        assertEquals(0.015330, filter.magnitudeDb(16000.0), tolerance)
    }

    @Test
    fun lowShelfBiquadMatchesDriver() {
        val filter = BiquadResponse.lowShelf(gainDb = 6.0, frequency = 200.0, q = 0.7, sampleRate = 48_000)

        assertEquals(5.971821, filter.magnitudeDb(20.0), tolerance)
        assertEquals(5.162887, filter.magnitudeDb(100.0), tolerance)
        // The driver's shelf reaches half the gain at the corner frequency.
        assertEquals(3.000000, filter.magnitudeDb(200.0), tolerance)
        assertEquals(0.116551, filter.magnitudeDb(1000.0), tolerance)
    }

    @Test
    fun highShelfBiquadMatchesDriver() {
        val filter = BiquadResponse.highShelf(gainDb = -6.0, frequency = 5000.0, q = 1.5, sampleRate = 44_100)

        assertEquals(0.003162, filter.magnitudeDb(200.0), tolerance)
        assertEquals(-1.141785, filter.magnitudeDb(4000.0), tolerance)
        assertEquals(-3.000000, filter.magnitudeDb(5000.0), tolerance)
        assertEquals(-6.058977, filter.magnitudeDb(16000.0), tolerance)
    }

    @Test
    fun butterworthLowPassMatchesDriver() {
        val filter = BiquadResponse.lowPass(frequency = 500.0, q = 0.70710678, sampleRate = 48_000)

        assertEquals(-0.006934, filter.magnitudeDb(100.0), tolerance)
        assertEquals(-0.109650, filter.magnitudeDb(200.0), tolerance)
        assertEquals(-12.322023, filter.magnitudeDb(1000.0), tolerance)
        assertEquals(-36.521717, filter.magnitudeDb(4000.0), tolerance)
    }

    @Test
    fun butterworthHighPassMatchesDriver() {
        val filter = BiquadResponse.highPass(frequency = 500.0, q = 0.70710678, sampleRate = 48_000)

        assertEquals(-55.923804, filter.magnitudeDb(20.0), tolerance)
        assertEquals(-16.032461, filter.magnitudeDb(200.0), tolerance)
        assertEquals(-0.262196, filter.magnitudeDb(1000.0), tolerance)
        assertEquals(-0.000968, filter.magnitudeDb(4000.0), tolerance)
    }

    @Test
    fun dynamicEqCascadesBandsAsTheDriverDoes() {
        val bands = listOf(
            DynamicEqBandSpec(frequency = 1000.0, gainDb = 9.0, q = 1.0, filterType = 0),
            DynamicEqBandSpec(frequency = 200.0, gainDb = 6.0, q = 0.7, filterType = 1),
        )
        val response = DynamicEqResponse(sampleRate = 48_000)

        // Driver chains the per-band biquads, so magnitudes add in dB.
        assertEquals(
            BiquadResponse.peak(9.0, 1000.0, 1.0, 48_000).magnitudeDb(1000.0) +
                BiquadResponse.lowShelf(6.0, 200.0, 0.7, 48_000).magnitudeDb(1000.0),
            response.magnitudeDb(bands, 1000.0),
            tolerance,
        )
    }

    @Test
    fun dynamicEqIgnoresBandsWithoutGain() {
        val response = DynamicEqResponse(sampleRate = 48_000)
        val single = listOf(DynamicEqBandSpec(1000.0, 9.0, 1.0, 0))
        val withInert = single + DynamicEqBandSpec(4000.0, 0.0, 2.0, 0)

        assertEquals(
            response.magnitudeDb(single, 1000.0),
            response.magnitudeDb(withInert, 1000.0),
            1e-9,
        )
    }

    @Test
    fun dynamicEqSelectsFilterTypeByDriverIndex() {
        val response = DynamicEqResponse(sampleRate = 48_000)
        val peak = response.magnitudeDb(listOf(DynamicEqBandSpec(1000.0, 9.0, 1.0, 0)), 1000.0)
        val lowShelf = response.magnitudeDb(listOf(DynamicEqBandSpec(1000.0, 9.0, 1.0, 1)), 20.0)
        val highShelf = response.magnitudeDb(listOf(DynamicEqBandSpec(1000.0, 9.0, 1.0, 2)), 16000.0)

        assertEquals(9.0, peak, tolerance)
        assertEquals(9.0, lowShelf, 0.2)
        assertEquals(9.0, highShelf, 0.2)
    }

    @Test
    fun multibandCrossoverBandsUseFourthOrderSlopes() {
        val response = MultibandCrossoverResponse(
            crossovers = listOf(500.0),
            sampleRate = 48_000,
        )

        // Band 0 is two cascaded Butterworth low passes, so twice the single-stage dB.
        val lowPass = BiquadResponse.lowPass(500.0, 0.70710678, 48_000)
        assertEquals(2 * lowPass.magnitudeDb(1000.0), response.bandMagnitudeDb(0, 1000.0), tolerance)

        val highPass = BiquadResponse.highPass(500.0, 0.70710678, 48_000)
        assertEquals(2 * highPass.magnitudeDb(200.0), response.bandMagnitudeDb(1, 200.0), tolerance)
    }

    @Test
    fun multibandMiddleBandIsBandLimited() {
        val response = MultibandCrossoverResponse(
            crossovers = listOf(200.0, 2000.0),
            sampleRate = 48_000,
        )

        val middle = response.bandMagnitudeDb(1, 600.0)
        assertTrue("middle band should pass its own range, got $middle", middle > -3.0)
        assertTrue(response.bandMagnitudeDb(1, 20.0) < -40.0)
        assertTrue(response.bandMagnitudeDb(1, 18000.0) < -40.0)
    }

    @Test
    fun multibandSumIsCloseToUnityInsideEachBand() {
        val response = MultibandCrossoverResponse(
            crossovers = listOf(200.0, 2000.0, 8000.0),
            sampleRate = 48_000,
        )

        listOf(50.0, 600.0, 4000.0, 15000.0).forEach { frequency ->
            val sum = response.sumMagnitudeDb(frequency)
            assertTrue("sum at $frequency Hz was $sum dB", kotlin.math.abs(sum) < 6.0)
        }
    }

    @Test
    fun multibandWithoutCrossoversIsASingleFullRangeBand() {
        val response = MultibandCrossoverResponse(crossovers = emptyList(), sampleRate = 48_000)

        assertEquals(1, response.bandCount)
        assertEquals(0.0, response.bandMagnitudeDb(0, 1000.0), 1e-9)
    }
}

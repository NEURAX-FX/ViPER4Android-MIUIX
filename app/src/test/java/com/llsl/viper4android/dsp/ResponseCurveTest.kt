package com.llsl.viper4android.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseCurveTest {
    @Test
    fun samplesSpanTheAxisAndStayNormalized() {
        val points = firCurvePoints(
            bandCount = 10,
            gainsDb = List(10) { 0.0 },
            sampleRate = 48_000,
            minDb = -12.0,
            maxDb = 12.0,
            resolution = 64,
        )

        assertEquals(64, points.size)
        assertEquals(0f, points.first().x, 1e-5f)
        assertEquals(1f, points.last().x, 1e-5f)
        assertTrue(points.zipWithNext().all { (a, b) -> a.x < b.x })
        assertTrue(points.all { it.y in 0f..1f })
    }

    @Test
    fun boostedBandPullsTheCurveTowardTheTop() {
        val points = firCurvePoints(
            bandCount = 10,
            gainsDb = List(10) { if (it == 5) 12.0 else 0.0 },
            sampleRate = 48_000,
            minDb = -12.0,
            maxDb = 12.0,
            resolution = 256,
        )

        val peak = points.minBy { it.y }
        val peakFrequency = graphXToFrequency(peak.x, 48_000)
        assertTrue("peak at $peakFrequency Hz", peakFrequency in 700.0..1400.0)
    }

    @Test
    fun lowSampleRatesShrinkTheAxisInsteadOfCrashing() {
        val points = firCurvePoints(
            bandCount = 10,
            gainsDb = List(10) { 0.0 },
            sampleRate = 8_000,
            minDb = -12.0,
            maxDb = 12.0,
            resolution = 32,
        )

        assertEquals(32, points.size)
        assertTrue(graphXToFrequency(1f, 8_000) < 4_000.0)
    }

    @Test
    fun dynamicEqCurveTracksTheTargetGain() {
        val bands = listOf(DynamicEqBandSpec(1000.0, 12.0, 2.0, 0))
        val points = dynamicEqCurvePoints(
            bands = bands,
            sampleRate = 48_000,
            minDb = -12.0,
            maxDb = 12.0,
            resolution = 256,
        )

        val peak = points.minBy { it.y }
        assertTrue(graphXToFrequency(peak.x, 48_000) in 800.0..1300.0)
        assertTrue(points.all { it.y in 0f..1f })
    }

    @Test
    fun emptyInputsProduceEmptyCurves() {
        assertTrue(
            dynamicEqCurvePoints(emptyList(), 48_000, -12.0, 12.0).isEmpty(),
        )
        assertTrue(
            firCurvePoints(10, emptyList(), 48_000, -12.0, 12.0).isEmpty(),
        )
    }

    @Test
    fun multibandCurvesCoverEveryBand() {
        val curves = multibandCrossoverCurves(
            crossovers = listOf(200.0, 2000.0),
            sampleRate = 48_000,
            minDb = -48.0,
            maxDb = 6.0,
            resolution = 64,
        )

        assertEquals(3, curves.size)
        curves.forEach { curve ->
            assertEquals(64, curve.size)
            assertTrue(curve.all { it.y in 0f..1f })
        }
    }

    @Test
    fun multibandCurvesApplyOneManualGainOffsetPerBand() {
        val baseCurves =
            multibandCrossoverCurves(
                crossovers = listOf(200.0, 2000.0),
                sampleRate = 48_000,
                minDb = -48.0,
                maxDb = 24.0,
                resolution = 128,
            )
        val boostedCurves =
            multibandCrossoverCurves(
                crossovers = listOf(200.0, 2000.0),
                sampleRate = 48_000,
                minDb = -48.0,
                maxDb = 24.0,
                bandGainsDb = listOf(3.0, 6.0, 9.0),
                resolution = 128,
            )

        listOf(3.0, 6.0, 9.0).forEachIndexed { band, expectedGain ->
            val peakIndex = baseCurves[band].indices.minBy { baseCurves[band][it].y }
            val baseDb = graphYToDb(baseCurves[band][peakIndex].y, -48.0, 24.0)
            val boostedDb = graphYToDb(boostedCurves[band][peakIndex].y, -48.0, 24.0)
            assertEquals(expectedGain, boostedDb - baseDb, 1e-5)
        }
    }

    @Test
    fun multibandUnitySumCurveUsesTheUngainedCrossoverNetwork() {
        val curve =
            multibandCrossoverSumCurve(
                crossovers = listOf(200.0, 2000.0, 8000.0),
                sampleRate = 48_000,
                minDb = -48.0,
                maxDb = 24.0,
                resolution = 64,
            )

        assertEquals(64, curve.size)
        assertTrue(curve.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun multibandCurvesAreEmptyWhenCrossoversAreInvalid() {
        // Crossovers above Nyquist for an 8 kHz stream must not throw.
        val curves = multibandCrossoverCurves(
            crossovers = listOf(200.0, 20_000.0),
            sampleRate = 8_000,
            minDb = -48.0,
            maxDb = 6.0,
            resolution = 16,
        )

        assertTrue(curves.isEmpty())
    }

    @Test
    fun frequencyMappingRoundTrips() {
        listOf(20.0, 100.0, 1000.0, 10_000.0, 19_000.0).forEach { frequency ->
            val x = graphFrequencyToX(frequency, 48_000)
            assertEquals(frequency, graphXToFrequency(x, 48_000), frequency * 1e-6)
        }
    }

    @Test
    fun resolutionIsValidated() {
        try {
            firCurvePoints(10, List(10) { 0.0 }, 48_000, -12.0, 12.0, resolution = 1)
            throw AssertionError("expected an exception for resolution < 2")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}

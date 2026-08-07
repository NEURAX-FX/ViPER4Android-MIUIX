package com.llsl.viper4android.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumPresentationTest {
    @Test
    fun smoothingUsesFastAttackAndSlowRelease() {
        val previous = listOf(-60f, -40f)
        val current = listOf(-20f, -80f)

        val smoothed = smoothSpectrum(previous, current, attack = 0.6f, release = 0.2f)

        assertEquals(-36f, smoothed[0], 0.001f)
        assertEquals(-48f, smoothed[1], 0.001f)
    }

    @Test
    fun monotoneFitDoesNotInventPeaksBetweenBuckets() {
        val buckets = listOf(-96f, -40f, -20f, -50f, -96f)

        val fitted = fitSpectrumDb(buckets, subdivisions = 4)

        assertEquals(17, fitted.size)
        for (segment in 0 until buckets.lastIndex) {
            val low = minOf(buckets[segment], buckets[segment + 1])
            val high = maxOf(buckets[segment], buckets[segment + 1])
            val segmentValues = fitted.subList(segment * 4, segment * 4 + 5)
            assertTrue(segmentValues.all { it in low..high })
        }
    }

    @Test
    fun graphPointsAreBoundedAndCoverTheFullLogAxis() {
        val points = spectrumCurvePoints(listOf(-96f, -48f, 0f), subdivisions = 2)

        assertEquals(0f, points.first().x, 0f)
        assertEquals(1f, points.last().x, 0f)
        assertTrue(points.zipWithNext().all { (left, right) -> left.x < right.x })
        assertTrue(points.all { it.y in 0f..1f })
        assertEquals(1f, points.first().y, 0.001f)
        assertEquals(0f, points.last().y, 0.001f)
    }

    @Test
    fun frameInterpolationClampsProgressAndSanitizesMismatchedInput() {
        assertEquals(
            listOf(-96f, -30f),
            interpolateSpectrum(listOf(-90f), listOf(-96f, -30f), progress = -1f),
        )
        assertEquals(
            listOf(-60f, -20f),
            interpolateSpectrum(listOf(-80f, -40f), listOf(-60f, -20f), progress = 2f),
        )
    }
}

package com.llsl.viper4android.ui.screens.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqResponseTest {
    private fun octaveBands(gains: List<Double>): List<EqBand> {
        val freqs = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        return freqs.mapIndexed { index, f -> EqBand(f, gains.getOrElse(index) { 0.0 }) }
    }

    @Test
    fun flatBandsProduceZeroDecibelsEverywhere() {
        val bands = octaveBands(List(10) { 0.0 })

        listOf(20.0, 100.0, 1000.0, 10000.0, 20000.0).forEach { frequency ->
            assertEquals(0.0, equalizerMagnitudeDb(bands, frequency), 1e-9)
        }
    }

    @Test
    fun singleBellBandReachesItsGainAtCenterFrequency() {
        val bands = octaveBands(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 12.0))

        assertEquals(12.0, equalizerMagnitudeDb(bands, 1000.0), 0.05)
    }

    @Test
    fun singleBellBandDecaysFarFromCenterFrequency() {
        val bands = octaveBands(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 12.0))

        assertTrue(equalizerMagnitudeDb(bands, 40.0) < 1.0)
        assertTrue(equalizerMagnitudeDb(bands, 16000.0) < 1.0)
    }

    @Test
    fun cutIsMirrorOfBoostAtCenterFrequency() {
        val boost = octaveBands(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 9.0))
        val cut = octaveBands(listOf(0.0, 0.0, 0.0, 0.0, 0.0, -9.0))

        assertEquals(
            equalizerMagnitudeDb(boost, 1000.0),
            -equalizerMagnitudeDb(cut, 1000.0),
            0.05,
        )
    }

    @Test
    fun adjacentBoostsAddUpBetweenCenters() {
        val single = octaveBands(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 6.0))
        val pair = octaveBands(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 6.0, 6.0))

        val betweenSingle = equalizerMagnitudeDb(single, 1414.0)
        val betweenPair = equalizerMagnitudeDb(pair, 1414.0)
        assertTrue(betweenPair > betweenSingle)
        assertTrue(betweenPair > 6.0)
    }

    @Test
    fun lowestBandActsAsLowShelf() {
        val bands = octaveBands(listOf(10.0))

        assertEquals(10.0, equalizerMagnitudeDb(bands, 20.0), 0.6)
        assertTrue(equalizerMagnitudeDb(bands, 8000.0) < 0.5)
    }

    @Test
    fun highestBandActsAsHighShelf() {
        val bands = octaveBands(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 10.0))

        assertEquals(10.0, equalizerMagnitudeDb(bands, 20000.0), 0.6)
        assertTrue(equalizerMagnitudeDb(bands, 100.0) < 0.5)
    }

    @Test
    fun curvePointsAreNormalizedAndMonotoneInX() {
        val points = equalizerCurvePoints(
            bands = octaveBands(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 12.0)),
            minFrequency = 20.0,
            maxFrequency = 20000.0,
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
    fun curveDipsTowardTopOfGraphWhereBoosted() {
        val points = equalizerCurvePoints(
            bands = octaveBands(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 12.0)),
            minFrequency = 20.0,
            maxFrequency = 20000.0,
            minDb = -12.0,
            maxDb = 12.0,
            resolution = 128,
        )

        val boosted = points.minBy { it.y }
        val boostedFrequency = xToFrequency(boosted.x, 20.0, 20000.0)
        assertTrue(boostedFrequency in 700.0..1400.0)
        assertTrue(boosted.y < 0.1f)
    }

    @Test
    fun emptyBandsProduceEmptyCurve() {
        val points = equalizerCurvePoints(
            bands = emptyList(),
            minFrequency = 20.0,
            maxFrequency = 20000.0,
            minDb = -12.0,
            maxDb = 12.0,
            resolution = 32,
        )

        assertTrue(points.isEmpty())
    }

    @Test
    fun parametricBellPeaksAtItsCenterFrequency() {
        val bands = listOf(ParametricBand(frequency = 1000.0, gainDb = 9.0, q = 1.0))

        assertEquals(9.0, parametricMagnitudeDb(bands, 1000.0), 1e-6)
        assertTrue(parametricMagnitudeDb(bands, 1000.0) > parametricMagnitudeDb(bands, 1500.0))
        assertTrue(parametricMagnitudeDb(bands, 1000.0) > parametricMagnitudeDb(bands, 700.0))
    }

    @Test
    fun parametricBellIsSymmetricInLogFrequency() {
        val bands = listOf(ParametricBand(frequency = 1000.0, gainDb = 9.0, q = 2.0))

        assertEquals(
            parametricMagnitudeDb(bands, 2000.0),
            parametricMagnitudeDb(bands, 500.0),
            1e-6,
        )
    }

    @Test
    fun higherQNarrowsTheParametricBell() {
        val wide = listOf(ParametricBand(frequency = 1000.0, gainDb = 9.0, q = 0.7))
        val narrow = listOf(ParametricBand(frequency = 1000.0, gainDb = 9.0, q = 6.0))

        assertTrue(parametricMagnitudeDb(wide, 2000.0) > parametricMagnitudeDb(narrow, 2000.0))
        assertEquals(
            parametricMagnitudeDb(wide, 1000.0),
            parametricMagnitudeDb(narrow, 1000.0),
            1e-6,
        )
    }

    @Test
    fun parametricBandsSumAndZeroGainBandsAreInert() {
        val single = listOf(ParametricBand(1000.0, 6.0, 1.0))
        val withInert = single + ParametricBand(4000.0, 0.0, 1.0)

        assertEquals(
            parametricMagnitudeDb(single, 1000.0),
            parametricMagnitudeDb(withInert, 1000.0),
            1e-9,
        )

        val two = single + ParametricBand(1200.0, 6.0, 1.0)
        assertTrue(parametricMagnitudeDb(two, 1100.0) > parametricMagnitudeDb(single, 1100.0))
    }

    @Test
    fun parametricCurveIsNormalizedAndPeaksNearBandFrequency() {
        val points = parametricCurvePoints(
            bands = listOf(ParametricBand(1000.0, 12.0, 2.0)),
            minFrequency = 20.0,
            maxFrequency = 20000.0,
            minDb = -12.0,
            maxDb = 12.0,
            resolution = 128,
        )

        assertEquals(128, points.size)
        assertTrue(points.all { it.y in 0f..1f })
        assertTrue(points.zipWithNext().all { (a, b) -> a.x < b.x })
        val peak = points.minBy { it.y }
        assertTrue(xToFrequency(peak.x, 20.0, 20000.0) in 800.0..1300.0)
    }

    @Test
    fun parametricCurveOfEmptyBandsIsEmpty() {
        val points = parametricCurvePoints(
            bands = emptyList(),
            minFrequency = 20.0,
            maxFrequency = 20000.0,
            minDb = -12.0,
            maxDb = 12.0,
        )

        assertTrue(points.isEmpty())
    }




    @Test
    fun mbcRegionsUseLogFrequencyBoundaries() {
        val regions = mbcBandRegions(listOf(200.0, 2000.0), 20.0, 20000.0)

        assertEquals(3, regions.size)
        assertEquals(0f, regions.first().startX, 0.0001f)
        assertEquals(1f, regions.last().endX, 0.0001f)
        assertTrue(regions.zipWithNext().all { (a, b) -> a.endX == b.startX })
    }

    @Test
    fun mbcRegionsWithoutCrossoversCoverTheWholeRange() {
        val regions = mbcBandRegions(emptyList(), 20.0, 20000.0)

        assertEquals(1, regions.size)
        assertEquals(0f, regions.first().startX, 0.0001f)
        assertEquals(1f, regions.first().endX, 0.0001f)
    }

    @Test
    fun mbcRegionBoundariesMatchFrequencyMapping() {
        val regions = mbcBandRegions(listOf(1000.0), 20.0, 20000.0)

        assertEquals(frequencyToX(1000.0, 20.0, 20000.0), regions[0].endX, 1e-6f)
        assertEquals(frequencyToX(1000.0, 20.0, 20000.0), regions[1].startX, 1e-6f)
    }
}

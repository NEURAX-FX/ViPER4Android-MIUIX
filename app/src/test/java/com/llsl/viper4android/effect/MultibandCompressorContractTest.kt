package com.llsl.viper4android.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultibandCompressorContractTest {
    @Test
    fun normalizeStatePadsEveryBandListAndTrimsExtras() {
        val normalized =
            normalizeMultibandCompressorState(
                MultibandCompressorState(
                    bandEnables = listOf(false),
                    crossovers = listOf(120),
                    thresholds = listOf(-60, -12, 4, -24, -18, -9),
                    ratios = emptyList(),
                    gains = listOf(-8, 30),
                    knees = listOf(24),
                    kneeMultis = listOf(-1),
                    attacks = listOf(0),
                    maxAttacks = listOf(101),
                    releases = listOf(1),
                    maxReleases = listOf(501),
                    crests = listOf(1),
                    adapts = listOf(201),
                    kneeAutos = listOf(false),
                    gainAutos = listOf(false),
                    attackAutos = listOf(false),
                    releaseAutos = listOf(false),
                    noClips = listOf(false),
                ),
            )

        assertEquals(listOf(false, true, true, true, true), normalized.bandEnables)
        assertEquals(listOf(120, 500, 4000, 8000), normalized.crossovers)
        assertEquals(listOf(-48, -12, 0, -24, -18), normalized.thresholds)
        assertEquals(listOf(50, 50, 50, 50, 50), normalized.ratios)
        assertEquals(listOf(0, 24, 0, 0, 0), normalized.gains)
        assertEquals(listOf(12, 0, 0, 0, 0), normalized.knees)
        assertEquals(listOf(0, 0, 0, 0, 0), normalized.kneeMultis)
        assertEquals(listOf(1, 1, 1, 1, 1), normalized.attacks)
        assertEquals(listOf(100, 44, 44, 44, 44), normalized.maxAttacks)
        assertEquals(listOf(5, 100, 100, 100, 100), normalized.releases)
        assertEquals(listOf(500, 200, 200, 200, 200), normalized.maxReleases)
        assertEquals(listOf(5, 100, 100, 100, 100), normalized.crests)
        assertEquals(listOf(200, 50, 50, 50, 50), normalized.adapts)
        assertEquals(listOf(false, true, true, true, true), normalized.kneeAutos)
        assertEquals(listOf(false, true, true, true, true), normalized.gainAutos)
        assertEquals(listOf(false, true, true, true, true), normalized.attackAutos)
        assertEquals(listOf(false, true, true, true, true), normalized.releaseAutos)
        assertEquals(listOf(false, true, true, true, true), normalized.noClips)
    }

    @Test
    fun normalizeCrossoversKeepsCanonicalBoundsAndOneSemitoneSpacing() {
        val low = normalizeMultibandCrossovers(List(MULTIBAND_CROSSOVER_COUNT) { 30 })
        val high = normalizeMultibandCrossovers(List(MULTIBAND_CROSSOVER_COUNT) { 16_000 })

        assertCrossoverContract(low)
        assertCrossoverContract(high)
        assertEquals(MULTIBAND_MIN_FREQUENCY, low.first())
        assertEquals(MULTIBAND_MAX_FREQUENCY, high.last())
    }

    @Test
    fun normalizeCrossoversUsesDefaultsForMissingValuesAndHonorsRuntimeMaximum() {
        val normalized = normalizeMultibandCrossovers(listOf(120), maxFrequency = 8_000)

        assertEquals(MULTIBAND_CROSSOVER_COUNT, normalized.size)
        assertEquals(8_000, normalized.last())
        assertCrossoverContract(normalized, maxFrequency = 8_000)
    }

    private fun assertCrossoverContract(
        values: List<Int>,
        maxFrequency: Int = MULTIBAND_MAX_FREQUENCY,
    ) {
        assertEquals(MULTIBAND_CROSSOVER_COUNT, values.size)
        assertTrue(values.all { it in MULTIBAND_MIN_FREQUENCY..maxFrequency })
        assertTrue(
            values.zipWithNext().all { (left, right) ->
                right >= kotlin.math.ceil(left * MULTIBAND_SPACING_RATIO).toInt()
            },
        )
    }
}

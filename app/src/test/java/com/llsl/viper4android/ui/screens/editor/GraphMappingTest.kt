package com.llsl.viper4android.ui.screens.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class GraphMappingTest {
    @Test
    fun logarithmicFrequencyMappingRoundTripsAcrossAudioRange() {
        listOf(20.0, 1000.0, 20000.0).forEach { frequency ->
            val x = frequencyToX(frequency, 20.0, 20000.0)
            assertEquals(frequency, xToFrequency(x, 20.0, 20000.0), frequency * 0.0001)
        }
    }

    @Test
    fun dbMappingRoundTripsAndRejectsNonFiniteInput() {
        listOf(-48.0, -12.0, 0.0, 12.0).forEach { db ->
            val y = dbToY(db, -48.0, 12.0)
            assertEquals(db, yToDb(y, -48.0, 12.0), 0.0001)
        }
        assertEquals(20.0, xToFrequency(Float.NaN, 20.0, 20000.0), 0.001)
        assertEquals(-48.0, yToDb(Float.NaN, -48.0, 12.0), 0.001)
    }

    @Test
    fun firHorizontalPositionSelectsNearestFixedBand() {
        val frequencies = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        val x = frequencyToX(900.0, 20.0, 20000.0)

        assertEquals(5, nearestFixedBand(frequencies, x, 20.0, 20000.0))
    }

    @Test
    fun frequencyGridEmitsDecadeMajorsAndMultipleMinorsInRange() {
        val lines = frequencyGridLines(20.0, 20000.0)

        assertTrue(lines.all { it.position in 0f..1f })
        assertTrue(lines.zipWithNext().all { (left, right) -> left.position < right.position })
        val majors = lines.filter { it.major }.map { it.frequency }
        assertEquals(listOf(100.0, 1000.0, 10000.0), majors)
        assertTrue(lines.any { !it.major && it.frequency == 50.0 })
        assertTrue(lines.none { it.frequency < 20.0 || it.frequency > 20000.0 })
    }

    @Test
    fun frequencyGridLabelsUseKiloSuffixAboveOneKilohertz() {
        val lines = frequencyGridLines(20.0, 20000.0)

        assertEquals("100", lines.first { it.frequency == 100.0 }.label)
        assertEquals("1k", lines.first { it.frequency == 1000.0 }.label)
        assertEquals("10k", lines.first { it.frequency == 10000.0 }.label)
        assertEquals("2k", lines.first { it.frequency == 2000.0 }.label)
    }

    @Test
    fun decibelGridIsEvenlySpacedAndIncludesZeroWhenInRange() {
        val lines = decibelGridLines(-48.0, 12.0, step = 12.0)

        assertEquals(listOf(12.0, 0.0, -12.0, -24.0, -36.0, -48.0), lines.map { it.db })
        assertTrue(lines.first { it.db == 0.0 }.major)
        assertEquals(0f, lines.first { it.db == 12.0 }.position, 0.0001f)
        assertEquals(1f, lines.first { it.db == -48.0 }.position, 0.0001f)
        assertEquals("+12", lines.first { it.db == 12.0 }.label)
        assertEquals("0", lines.first { it.db == 0.0 }.label)
        assertEquals("-24", lines.first { it.db == -24.0 }.label)
    }

    @Test
    fun multibandCrossoversCannotCrossAndKeepOneSemitoneSpacing() {
        val ratio = 2.0.pow(1.0 / 12.0)
        val original = listOf(120, 500, 4000, 8000)

        val movedRight = constrainCrossovers(original, changedIndex = 1, requestedFrequency = 7000)
        val movedLeft = constrainCrossovers(original, changedIndex = 2, requestedFrequency = 200)

        assertTrue(movedRight[1] <= (movedRight[2] / ratio).toInt())
        assertTrue(movedLeft[2] >= (movedLeft[1] * ratio).toInt())
        assertTrue(movedRight.zipWithNext().all { (left, right) -> left < right })
        assertTrue(movedLeft.zipWithNext().all { (left, right) -> left < right })
    }

    @Test
    fun nearestGraphHandleReturnsNullOutsideTouchRadius() {
        val handles = listOf(GraphHandleModel("a", 0.2f, 0.4f))

        assertNull(nearestGraphHandle(handles, 0.8f, 0.4f, hitRadius = 0.08f))
    }

    @Test
    fun nearestGraphHandlePicksTheClosestHandleInRange() {
        val handles = listOf(
            GraphHandleModel("a", 0.2f, 0.4f),
            GraphHandleModel("b", 0.3f, 0.4f),
        )

        assertEquals(1, nearestGraphHandle(handles, 0.28f, 0.42f, hitRadius = 0.1f))
    }

    @Test
    fun nearestGraphHandleHandlesEmptyInputAndExactHit() {
        assertNull(nearestGraphHandle(emptyList(), 0.5f, 0.5f, hitRadius = 0.5f))
        assertEquals(
            0,
            nearestGraphHandle(
                listOf(GraphHandleModel("a", 0.5f, 0.5f)),
                0.5f,
                0.5f,
                hitRadius = 0.01f,
            ),
        )
    }

    @Test
    fun linearAxisMappingsRoundTripAndSanitizeNonFiniteInput() {
        listOf(-60.0, -18.0, 0.0, 24.0).forEach { value ->
            val x = linearValueToX(value, -60.0, 24.0)
            val y = linearValueToY(value, -60.0, 24.0)
            assertEquals(value, xToLinearValue(x, -60.0, 24.0), 2e-6)
            assertEquals(value, yToLinearValue(y, -60.0, 24.0), 2e-6)
        }
        assertEquals(-60.0, xToLinearValue(Float.NaN, -60.0, 24.0), 1e-6)
        assertEquals(-60.0, yToLinearValue(Float.NaN, -60.0, 24.0), 1e-6)
    }

    @Test
    fun bandRegionHitTestingUsesHalfOpenBoundariesAndIncludesFinalEdge() {
        val regions =
            listOf(
                GraphBandRegion(0f, 0.25f, "Band 1"),
                GraphBandRegion(0.25f, 0.75f, "Band 2"),
                GraphBandRegion(0.75f, 1f, "Band 3"),
            )

        assertEquals(0, bandRegionAt(regions, 0f))
        assertEquals(1, bandRegionAt(regions, 0.25f))
        assertEquals(2, bandRegionAt(regions, 1f))
        assertNull(bandRegionAt(regions, Float.NaN))
        assertNull(bandRegionAt(emptyList(), 0.5f))
    }
}

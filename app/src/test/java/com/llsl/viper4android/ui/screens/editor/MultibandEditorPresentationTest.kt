package com.llsl.viper4android.ui.screens.editor

import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.MultibandCompressorState
import com.llsl.viper4android.ui.components.viper.GraphDragAxis
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultibandEditorPresentationTest {
    @Test
    fun presentationContainsFiveBandsAndExactlyFourCrossoverHandles() {
        val presentation = multibandEditorPresentation(EffectState(), sampleRate = 48_000)

        assertEquals(5, presentation.bands.size)
        assertEquals(5, presentation.bandRegions.size)
        assertEquals(
            listOf("crossover-0", "crossover-1", "crossover-2", "crossover-3"),
            presentation.crossoverHandles.map { it.id },
        )
        assertEquals(listOf(0, 1, 2, 3), presentation.crossoverHandles.map { it.controlledBand })
        assertFalse(presentation.crossoverHandles.any { it.controlledBand == 4 })
    }

    @Test
    fun autoGainLocksOnlyTheVerticalAxisAndAddsAutoState() {
        val presentation =
            multibandEditorPresentation(
                EffectState(
                    multibandCompressor =
                        MultibandCompressorState(
                            gainAutos = listOf(false, true, false, true, false),
                        ),
                ),
                sampleRate = 48_000,
            )

        assertEquals(GraphDragAxis.FREE, presentation.crossoverHandles[0].dragAxis)
        assertNull(presentation.crossoverHandles[0].badge)
        assertEquals(GraphDragAxis.HORIZONTAL, presentation.crossoverHandles[1].dragAxis)
        assertEquals("AUTO", presentation.crossoverHandles[1].badge)
    }

    @Test
    fun bypassedBandRemainsSelectableAndKeepsItsStoredValues() {
        val presentation =
            multibandEditorPresentation(
                EffectState(
                    multibandCompressor =
                        MultibandCompressorState(
                            bandEnables = listOf(true, false, true, true, true),
                            thresholds = listOf(-18, -12, -18, -18, -18),
                            gains = listOf(0, 6, 0, 0, 0),
                        ),
                ),
                sampleRate = 48_000,
            )

        assertFalse(presentation.bands[1].compressionEnabled)
        assertEquals(-12, presentation.bands[1].thresholdDb)
        assertEquals(6, presentation.bands[1].gainDb)
        assertTrue(presentation.bandRegions[1].endX > presentation.bandRegions[1].startX)
    }

    @Test
    fun bandPaletteKeepsStableEndpointsAndFiveBandSlots() {
        val colors = multibandBandColors(Color.Red, Color.Blue)

        assertEquals(5, colors.size)
        assertEquals(Color.Red, colors.first())
        assertEquals(Color.Blue, colors.last())
        assertEquals(5, colors.distinct().size)
    }

    @Test
    fun manualTransferPresentationExposesThresholdRatioAndKneeHandles() {
        val presentation =
            multibandTransferPresentation(
                state =
                    EffectState(
                        multibandCompressor =
                            MultibandCompressorState(
                                kneeAutos = List(5) { false },
                                gainAutos = List(5) { false },
                            ),
                    ),
                band = 2,
            )

        assertEquals(listOf("threshold", "ratio", "knee"), presentation.handles.map { it.id })
        assertEquals(GraphDragAxis.HORIZONTAL, presentation.handles[0].dragAxis)
        assertEquals(GraphDragAxis.VERTICAL, presentation.handles[1].dragAxis)
        assertEquals(GraphDragAxis.HORIZONTAL, presentation.handles[2].dragAxis)
        assertTrue(presentation.handles.all { it.enabled })
        assertFalse(presentation.curveDashed)
    }

    @Test
    fun autoModesDisableIgnoredManualControlsWithoutHidingThreshold() {
        val presentation = multibandTransferPresentation(EffectState(), band = 0)

        assertTrue(presentation.controls.thresholdEnabled)
        assertFalse(presentation.controls.ratioEnabled)
        assertFalse(presentation.controls.kneeEnabled)
        assertFalse(presentation.controls.gainEnabled)
        assertFalse(presentation.controls.attackEnabled)
        assertFalse(presentation.controls.releaseEnabled)
        assertTrue(presentation.handles.first { it.id == "threshold" }.enabled)
        assertFalse(presentation.handles.first { it.id == "ratio" }.enabled)
        assertFalse(presentation.handles.first { it.id == "knee" }.enabled)
        assertTrue(presentation.curveDashed)
        assertEquals(0.0, presentation.transferSpec.makeupGainDb, 1e-9)
    }
}

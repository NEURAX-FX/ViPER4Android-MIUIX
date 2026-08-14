package com.llsl.viper4android.ui.screens.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IemEditorContractTest {
    @Test
    fun editorContractMatchesPhaseOne() {
        assertEquals(listOf("encoder", "rotation", "decoder", "output"), iemEditorTabs())
        assertEquals(listOf("spatial", "timing", "pitch", "window", "mix"), iemGranularSections())
        assertEquals(23, headphoneEqOptions().count { it.id >= 0 })
        assertEquals("AKG K1000 Closed", headphoneEqOptions().first { it.id == 0 }.label)
        assertEquals("Shure SRH940", headphoneEqOptions().first { it.id == 22 }.label)
        assertEquals("https://plugins.iem.at", IEM_PROJECT_URL)
        assertEquals(listOf("off", "simple", "ku100"), iemRenderModes())
        assertFalse(shouldEnableHeadphoneEq(renderMode = 0))
        assertFalse(shouldEnableHeadphoneEq(renderMode = 1))
        assertTrue(shouldEnableHeadphoneEq(renderMode = 2))
        assertEquals(18, iemHaloControls().size)
        assertTrue(iemHaloControls().containsAll(listOf("lfeEnable", "lfeFrequency", "lfeSplit", "lfeGain")))
    }

    @Test
    fun haloLfeUiMappingsMatchBinaryCurves() {
        assertEquals(10.0, haloLfeCutoffHz(0), 1.0e-9)
        assertEquals(200.0, haloLfeCutoffHz(1_000_000), 1.0e-9)
        assertEquals(94.5741609003176, haloLfeCutoffHz(750_000), 1.0e-9)
        assertEquals(750_000, haloLfeFrequencyMillionths(haloLfeCutoffHz(750_000)))
        assertEquals(-45.0, haloLfeGainDb(0), 1.0e-9)
        assertEquals(10.0, haloLfeGainDb(1_000_000), 1.0e-9)
        assertEquals(-30.000015, haloLfeGainDb(272_727), 1.0e-9)
        assertEquals(272_727, haloLfeGainMillionths(haloLfeGainDb(272_727)))
    }

    @Test
    fun telemetryLabelsCoverReleaseDiagnostics() {
        assertTrue(
            iemTelemetryKeys().containsAll(
                listOf("latency", "activeGrains", "queueFaults", "limiterReduction", "fault", "preparation"),
            ),
        )
    }
}

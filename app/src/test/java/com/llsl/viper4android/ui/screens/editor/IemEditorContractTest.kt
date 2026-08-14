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
        assertEquals(listOf("off", "haloDownmix", "ku100"), iemRenderModes())
        assertFalse(shouldEnableHeadphoneEq(renderMode = 0))
        assertFalse(shouldEnableHeadphoneEq(renderMode = 1))
        assertTrue(shouldEnableHeadphoneEq(renderMode = 2))
        assertEquals(18, iemHaloControls().size)
        assertTrue(iemHaloControls().containsAll(listOf("lfeEnable", "lfeFrequency", "lfeSplit", "lfeGain")))
        assertEquals(29, iemDownmixControls().size)
        assertTrue(iemDownmixControls().containsAll(listOf(
            "delayEnable", "sideShelfFrequency", "centerDivergence",
            "lfeLpfEnable", "outputHpfFrequency",
        )))
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
    fun haloDownmixUiMappingsMatchNativeCurves() {
        assertEquals(20.0, haloDownmixFrequencyHz(0), 1.0e-9)
        assertEquals(22000.0, haloDownmixFrequencyHz(1_000_000), 1.0e-8)
        assertEquals(100.0, haloDownmixFrequencyHz(229_819), 0.001)
        assertEquals(229_819, haloDownmixFrequencyMillionths(haloDownmixFrequencyHz(229_819)))
        assertEquals(-70.0, haloDownmixGainDb(0), 1.0e-9)
        assertEquals(20.0, haloDownmixGainDb(1_000_000), 1.0e-9)
        assertEquals(0.00002, haloDownmixGainDb(777_778), 1.0e-9)
        assertEquals(777_778, haloDownmixGainMillionths(haloDownmixGainDb(777_778)))
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

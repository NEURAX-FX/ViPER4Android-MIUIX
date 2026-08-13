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
        assertEquals(14, iemHaloControls().size)
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

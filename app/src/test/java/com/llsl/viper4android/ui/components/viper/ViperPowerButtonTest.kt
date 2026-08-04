package com.llsl.viper4android.ui.components.viper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViperPowerButtonTest {
    @Test
    fun ringBrightnessLeadsLinearActivation() {
        assertEquals(0f, powerRingProgress(-1f), 0.0001f)
        assertTrue(powerRingProgress(0.25f) > 0.25f)
        assertTrue(powerRingProgress(0.25f) < 1f)
        assertEquals(1f, powerRingProgress(1f), 0.0001f)
        assertEquals(1f, powerRingProgress(2f), 0.0001f)
    }

    @Test
    fun powerSymbolWaitsForOuterRing() {
        assertEquals(0f, powerIconProgress(0f), 0.0001f)
        assertEquals(0f, powerIconProgress(0.45f), 0.0001f)
        assertEquals(0.5f, powerIconProgress(0.725f), 0.0001f)
        assertEquals(1f, powerIconProgress(1f), 0.0001f)
    }
}

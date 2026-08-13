package com.llsl.viper4android.ui.screens.main

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectSectionSummaryTest {
    @Test
    fun convolverUsesBasenameAndExactDelayPrecision() {
        assertEquals(
            "Neurax08.wav · Wet 100% · 0.3125 ms",
            formatConvolverSummary(
                kernelFile = "/storage/emulated/0/Kernel/Neurax08.wav",
                wet = 100,
                crossDelay100Ns = 3125,
                noneLabel = "None",
                wetLabel = "Wet",
            ),
        )
    }

    @Test
    fun convolverUsesNoneForMissingKernel() {
        assertEquals(
            "None · Wet 65% · 10.0000 ms",
            formatConvolverSummary("", 65, 100000, "None", "Wet"),
        )
    }

    @Test
    fun outputConvertsRawValuesAndPan() {
        assertEquals(
            "0.0 dB · 50:50 · Limiter 0.0 dB",
            formatOutputSummary(100, 0, 100, "Limiter"),
        )
    }

    @Test
    fun multiplierUsesOneDecimalPlace() {
        assertEquals("2.5x", formatMultiplier(250))
    }
}

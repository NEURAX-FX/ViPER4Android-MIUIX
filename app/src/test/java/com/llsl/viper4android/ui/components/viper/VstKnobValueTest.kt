package com.llsl.viper4android.ui.components.viper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VstKnobValueTest {
    @Test
    fun exactInputUsesRawValueInsteadOfFormattedDisplayText() {
        assertEquals("12", knobExactInput(12.0) { it.toInt().toString() })
        assertEquals("50", knobExactInput(50.0) { it.toInt().toString() })
    }

    @Test
    fun exactInputParserTrimsAndRejectsNonFiniteValues() {
        assertEquals(12.5, parseKnobExactInput(" 12.5 ")!!, 1e-9)
        assertNull(parseKnobExactInput("12 ms"))
        assertNull(parseKnobExactInput("NaN"))
        assertNull(parseKnobExactInput("Infinity"))
    }
}

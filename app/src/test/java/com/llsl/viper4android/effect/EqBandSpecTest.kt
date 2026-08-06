package com.llsl.viper4android.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden tests pinning the app's FIR band table to the driver's fixed table.
 *
 * Driver reference: ViPERFX_RE/ViPERDSP/viper/utils/MinPhaseIIRCoeffs.cpp:4-37
 */
class EqBandSpecTest {
    @Test
    fun tenBandFrequenciesMatchDriver() {
        assertEquals(
            listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0),
            EqBandSpec.frequenciesFor(10),
        )
    }

    @Test
    fun fifteenBandFrequenciesMatchDriver() {
        assertEquals(
            listOf(
                25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0,
                1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 10000.0, 16000.0,
            ),
            EqBandSpec.frequenciesFor(15),
        )
    }

    @Test
    fun twentyFiveBandFrequenciesMatchDriver() {
        assertEquals(
            listOf(
                20.0, 31.5, 40.0, 50.0, 80.0, 100.0, 125.0, 160.0, 250.0,
                315.0, 400.0, 500.0, 800.0, 1000.0, 1250.0, 1600.0, 2500.0, 3150.0,
                4000.0, 5000.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0,
            ),
            EqBandSpec.frequenciesFor(25),
        )
    }

    @Test
    fun thirtyOneBandFrequenciesMatchDriver() {
        assertEquals(
            listOf(
                20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0,
                125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0,
                800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0,
                5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0,
            ),
            EqBandSpec.frequenciesFor(31),
        )
    }

    @Test
    fun everySupportedCountIsStrictlyIncreasingAndCorrectlySized() {
        listOf(10, 15, 25, 31).forEach { count ->
            val frequencies = EqBandSpec.frequenciesFor(count)
            assertEquals("band count $count", count, frequencies.size)
            assertTrue(
                "band count $count must be strictly increasing",
                frequencies.zipWithNext().all { (a, b) -> a < b },
            )
        }
    }

    @Test
    fun unsupportedCountFallsBackToTenBands() {
        assertEquals(EqBandSpec.frequenciesFor(10), EqBandSpec.frequenciesFor(7))
        assertEquals(EqBandSpec.frequenciesFor(10), EqBandSpec.frequenciesFor(0))
    }

    @Test
    fun labelsAreDerivedFromDriverFrequencies() {
        assertEquals(EqBandSpec.frequenciesFor(15).size, EqBandSpec.labelsFor(15).size)
        assertEquals("25", EqBandSpec.labelsFor(15).first())
        assertEquals("1k", EqBandSpec.labelsFor(15)[8])
        assertEquals("16k", EqBandSpec.labelsFor(15).last())
        assertEquals("31.5", EqBandSpec.labelsFor(31)[2])
        assertEquals("1.25k", EqBandSpec.labelsFor(31)[18])
        assertEquals("20k", EqBandSpec.labelsFor(31).last())
    }
}

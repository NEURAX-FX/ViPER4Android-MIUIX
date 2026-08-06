package com.llsl.viper4android.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

/**
 * Golden tests pinning [ParamRaw] time transforms to the driver's decode formulas.
 *
 * Driver reference (ViPERFX_RE/ViPERDSP/viper/effects/FETCompressor.cpp:99-135):
 *   attack_seconds  = exp(value * 7.600903 - 9.21034)
 *   release_seconds = exp(value * 5.991465 - 5.298317)
 * SetMaxAttack, SetMaxRelease and SetCrest reuse the same two formulas, so the
 * app must encode them with the same inverses rather than a linear ratio.
 */
class ParamRawTimeTransformTest {
    private fun driverAttackMs(raw: Float): Double = exp(raw * 7.600903 - 9.21034) * 1000.0

    private fun driverReleaseMs(raw: Float): Double = exp(raw * 5.991465 - 5.298317) * 1000.0

    @Test
    fun attackEncodingRoundTripsThroughDriverFormula() {
        listOf(1, 5, 10, 50, 100, 200).forEach { ms ->
            val raw = ParamRaw.fetCompressorAttackMsF(ms)
            assertEquals(
                "attack $ms ms must survive the driver round trip",
                ms.toDouble(),
                driverAttackMs(raw),
                ms * 0.01,
            )
        }
    }

    @Test
    fun releaseEncodingRoundTripsThroughDriverFormula() {
        listOf(5, 20, 100, 500, 1000, 2000).forEach { ms ->
            val raw = ParamRaw.fetCompressorReleaseMsF(ms)
            assertEquals(
                "release $ms ms must survive the driver round trip",
                ms.toDouble(),
                driverReleaseMs(raw),
                ms * 0.01,
            )
        }
    }

    @Test
    fun attackEncodingIsNotALinearRatio() {
        // A linear ms/500 encoding would map 10 ms to 0.02, which the driver
        // decodes to roughly 0.12 ms. Guard against that regression.
        val raw = ParamRaw.fetCompressorAttackMsF(10)
        assertTrue("expected non-linear encoding, got $raw", raw > 0.5f)
        assertEquals(10.0, driverAttackMs(raw), 0.2)
    }

    @Test
    fun releaseEncodingIsNotALinearRatio() {
        val raw = ParamRaw.fetCompressorReleaseMsF(100)
        assertTrue("expected non-linear encoding, got $raw", raw > 0.4f)
        assertEquals(100.0, driverReleaseMs(raw), 2.0)
    }

    @Test
    fun nonPositiveDurationsEncodeToZero() {
        assertEquals(0f, ParamRaw.fetCompressorAttackMsF(0), 0f)
        assertEquals(0f, ParamRaw.fetCompressorReleaseMsF(0), 0f)
        assertEquals(0f, ParamRaw.fetCompressorAttackMsF(-5), 0f)
        assertEquals(0f, ParamRaw.fetCompressorReleaseMsF(-5), 0f)
    }

    @Test
    fun encodedValuesStayInsideDriverAcceptedRange() {
        listOf(1, 100, 2000, 100_000).forEach { ms ->
            assertTrue(ParamRaw.fetCompressorAttackMsF(ms) in 0f..2f)
            assertTrue(ParamRaw.fetCompressorReleaseMsF(ms) in 0f..2f)
        }
    }
}

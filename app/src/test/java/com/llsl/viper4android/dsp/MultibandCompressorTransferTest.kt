package com.llsl.viper4android.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultibandCompressorTransferTest {
    @Test
    fun hardKneeRatioFiftyProducesTwoToOneSlope() {
        val spec =
            MultibandTransferSpec(
                thresholdDb = -18.0,
                ratioRaw = 50,
                kneeDb = 0.0,
                makeupGainDb = 0.0,
            )

        assertEquals(-24.0, compressorOutputDb(-24.0, spec), 1e-6)
        assertEquals(-18.0, compressorOutputDb(-18.0, spec), 1e-6)
        assertEquals(-12.0, compressorOutputDb(-6.0, spec), 1e-6)
    }

    @Test
    fun softKneeMatchesDriverPiecewiseReduction() {
        val spec =
            MultibandTransferSpec(
                thresholdDb = -18.0,
                ratioRaw = 50,
                kneeDb = 12.0,
                makeupGainDb = 0.0,
            )

        assertEquals(-24.0, compressorOutputDb(-24.0, spec), 1e-6)
        assertEquals(-18.75, compressorOutputDb(-18.0, spec), 1e-6)
        assertEquals(-15.0, compressorOutputDb(-12.0, spec), 1e-6)
    }

    @Test
    fun manualMakeupGainOffsetsTheWholeStaticCurve() {
        val spec =
            MultibandTransferSpec(
                thresholdDb = -18.0,
                ratioRaw = 50,
                kneeDb = 0.0,
                makeupGainDb = 6.0,
            )

        assertEquals(-18.0, compressorOutputDb(-24.0, spec), 1e-6)
        assertEquals(-6.0, compressorOutputDb(-6.0, spec), 1e-6)
    }

    @Test
    fun zeroKneeAndCanonicalExtremesAlwaysProduceFiniteOutput() {
        val specs =
            listOf(
                MultibandTransferSpec(-48.0, 0, 0.0, 0.0),
                MultibandTransferSpec(-48.0, 200, 12.0, 24.0),
                MultibandTransferSpec(0.0, 0, 12.0, 24.0),
                MultibandTransferSpec(0.0, 200, 0.0, 0.0),
            )

        specs.forEach { spec ->
            listOf(-60.0, -48.0, -18.0, 0.0).forEach { input ->
                assertTrue(compressorOutputDb(input, spec).isFinite())
            }
        }
    }

    @Test
    fun ratioCanBeRecoveredFromDraggedHighInputEndpoint() {
        val spec = MultibandTransferSpec(-18.0, 25, 0.0, 0.0)

        assertEquals(
            50,
            ratioCoefficientForOutput(
                inputDb = 0.0,
                outputDb = -9.0,
                spec = spec,
            ),
        )
    }

    @Test
    fun ratioInversionKeepsCurrentValueWhenReductionBasisIsZero() {
        val spec = MultibandTransferSpec(0.0, 75, 0.0, 0.0)

        assertEquals(
            75,
            ratioCoefficientForOutput(
                inputDb = 0.0,
                outputDb = 0.0,
                spec = spec,
            ),
        )
    }

    @Test
    fun ratioLabelsPreserveLimiterAndOvercompressionSemantics() {
        assertEquals(MultibandRatioLabel.Conventional(1.0), multibandRatioLabel(0))
        assertEquals(MultibandRatioLabel.Conventional(2.0), multibandRatioLabel(50))
        assertEquals(MultibandRatioLabel.Conventional(4.0), multibandRatioLabel(75))
        assertEquals(MultibandRatioLabel.Conventional(10.0), multibandRatioLabel(90))
        assertEquals(MultibandRatioLabel.Conventional(20.0), multibandRatioLabel(95))
        assertEquals(MultibandRatioLabel.Limit, multibandRatioLabel(100))
        assertEquals(MultibandRatioLabel.Over(1), multibandRatioLabel(101))
        assertEquals(MultibandRatioLabel.Over(100), multibandRatioLabel(200))
    }

    @Test
    fun transferCurveCoversTheWholeNormalizedGraph() {
        val curve =
            multibandTransferCurve(
                MultibandTransferSpec(-18.0, 50, 6.0, 3.0),
                sampleCount = 5,
            )

        assertEquals(5, curve.size)
        assertEquals(0f, curve.first().x, 1e-6f)
        assertEquals(1f, curve.last().x, 1e-6f)
        assertTrue(curve.all { it.x in 0f..1f && it.y in 0f..1f })
    }
}

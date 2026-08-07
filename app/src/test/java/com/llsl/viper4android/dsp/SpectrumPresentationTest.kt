package com.llsl.viper4android.dsp

import com.llsl.viper4android.viper.DriverTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumPresentationTest {
    private fun telemetry(
        sequence: Int,
        sampleRate: Int = 48_000,
        spectrumDb: List<Float> = List(DriverTelemetry.SPECTRUM_COUNT) { -24f },
        validMask: Int = DriverTelemetry.SPECTRUM_VALID,
    ) = DriverTelemetry(
        sequence = sequence,
        sampleRate = sampleRate,
        fftSize = 2_048,
        validMask = validMask,
        overrunCount = 0,
        spectrumDb = spectrumDb,
        meterDb = List(DriverTelemetry.METER_COUNT) { 0f },
    )

    @Test
    fun attackUsesElapsedTimeInsteadOfPollCount() {
        val frame = telemetry(sequence = 1)
        val accepted = advanceSpectrumBallistics(SpectrumBallisticsState(), frame, 0L)

        val attacked = advanceSpectrumBallistics(accepted, frame, 15_000_000L)

        assertEquals(-50.48732f, attacked.envelopeDb[0], 0.0001f)
    }

    @Test
    fun releaseUsesItsOwnElapsedTimeConstant() {
        val loud = telemetry(sequence = 1)
        var state = advanceSpectrumBallistics(SpectrumBallisticsState(), loud, 0L)
        state = advanceSpectrumBallistics(state, loud, 150_000_000L)
        val quiet = telemetry(sequence = 2, spectrumDb = List(64) { -96f })
        state = advanceSpectrumBallistics(state, quiet, 150_000_000L)

        val released = advanceSpectrumBallistics(state, quiet, 450_000_000L)

        assertEquals(-69.51388f, released.envelopeDb[0], 0.0001f)
    }

    @Test
    fun duplicateSequenceBecomesSilentAfterStaleGracePeriod() {
        val frame = telemetry(sequence = 9)
        var state = advanceSpectrumBallistics(SpectrumBallisticsState(), frame, 0L)
        state = advanceSpectrumBallistics(state, frame, 150_000_000L)

        assertEquals(-24f, state.targetDb[0], 0f)
        val stale = advanceSpectrumBallistics(state, frame, 151_000_000L)

        assertEquals(SPECTRUM_FLOOR_DB, stale.targetDb[0], 0f)
        assertTrue(stale.envelopeDb[0] < state.envelopeDb[0])
        assertEquals(0L, stale.lastInputNanos)
    }

    @Test
    fun peaksHoldThenDecayAtTwentyDecibelsPerSecond() {
        val loud = telemetry(sequence = 1)
        var state = advanceSpectrumBallistics(SpectrumBallisticsState(), loud, 0L)
        state = advanceSpectrumBallistics(state, loud, 150_000_000L)
        val heldPeak = state.peakDb[0]
        val quiet = telemetry(sequence = 2, spectrumDb = List(64) { -96f })
        state = advanceSpectrumBallistics(state, quiet, 150_000_000L)

        state = advanceSpectrumBallistics(state, quiet, 650_000_000L)
        assertEquals(heldPeak, state.peakDb[0], 0.0001f)

        state = advanceSpectrumBallistics(state, quiet, 750_000_000L)
        assertEquals(heldPeak - 2f, state.peakDb[0], 0.0001f)
        assertTrue(state.peakDb[0] >= state.envelopeDb[0])
    }

    @Test
    fun sampleRateChangeResetsBandStateBeforeRemapping() {
        val original = telemetry(sequence = 1, sampleRate = 48_000)
        var state = advanceSpectrumBallistics(SpectrumBallisticsState(), original, 0L)
        state = advanceSpectrumBallistics(state, original, 15_000_000L)
        assertTrue(state.envelopeDb[0] > SPECTRUM_FLOOR_DB)

        val changed = telemetry(sequence = 2, sampleRate = 44_100)
        state = advanceSpectrumBallistics(state, changed, 20_000_000L)

        assertEquals(44_100, state.sampleRate)
        assertEquals(SPECTRUM_FLOOR_DB, state.envelopeDb[0], 0f)
        assertEquals(SPECTRUM_FLOOR_DB, state.peakDb[0], 0f)
        assertEquals(0L, state.peakHoldUntilNanos[0])
    }

    @Test
    fun malformedSpectrumTargetsSilenceImmediately() {
        val valid = telemetry(sequence = 1)
        var state = advanceSpectrumBallistics(SpectrumBallisticsState(), valid, 0L)
        val malformed = telemetry(sequence = 2, spectrumDb = listOf(-12f))

        state = advanceSpectrumBallistics(state, malformed, 1_000_000L)

        assertEquals(SPECTRUM_FLOOR_DB, state.targetDb[0], 0f)
        assertTrue(state.envelopeDb[0] <= SPECTRUM_FLOOR_DB)
    }

    @Test
    fun releaseSnapsToTheExactFloorWhenSettled() {
        val loud = telemetry(sequence = 1)
        var state = advanceSpectrumBallistics(SpectrumBallisticsState(), loud, 0L)
        state = advanceSpectrumBallistics(state, loud, 150_000_000L)
        val quiet = telemetry(sequence = 2, spectrumDb = List(64) { -96f })
        state = advanceSpectrumBallistics(state, quiet, 150_000_000L)

        state = advanceSpectrumBallistics(state, quiet, 3_150_000_000L)

        assertEquals(SPECTRUM_FLOOR_DB, state.envelopeDb[0], 0f)
    }

    @Test
    fun settledSilenceReusesStateUntilANewSequenceArrives() {
        val quiet = telemetry(sequence = 4, spectrumDb = List(64) { -96f })
        val settled =
            SpectrumBallisticsState(
                sampleRate = 48_000,
                sequence = 4,
                lastInputNanos = 0L,
                lastFrameNanos = 1_000_000_000L,
                hasInput = true,
            )

        val unchanged = advanceSpectrumBallistics(settled, quiet, 2_000_000_000L)

        assertSame(settled, unchanged)
    }

    @Test
    fun bandCentersMatchDriverLogarithmicGeometry() {
        assertEquals(21.108992, spectrumBandCenterFrequency(0, 64, 48_000), 0.000001)
        assertEquals(18_949.270513, spectrumBandCenterFrequency(63, 64, 48_000), 0.000001)
        assertEquals(20.732659, spectrumBandCenterFrequency(0, 64, 4_000), 0.000001)
        assertEquals(1_929.323240, spectrumBandCenterFrequency(63, 64, 4_000), 0.000001)
    }

    @Test
    fun monotoneFitDoesNotInventPeaksBetweenBuckets() {
        val buckets = listOf(-96f, -40f, -20f, -50f, -96f)

        val fitted = fitSpectrumDb(buckets, subdivisions = 4)

        assertEquals(17, fitted.size)
        for (segment in 0 until buckets.lastIndex) {
            val low = minOf(buckets[segment], buckets[segment + 1])
            val high = maxOf(buckets[segment], buckets[segment + 1])
            val segmentValues = fitted.subList(segment * 4, segment * 4 + 5)
            assertTrue(segmentValues.all { it in low..high })
        }
    }

    @Test
    fun driverBandsUseTheirCentersOnTheSharedDisplayAxis() {
        val points =
            spectrumCurvePoints(
                buckets = List(64) { if (it == 63) 0f else -96f },
                sampleRate = 48_000,
                subdivisions = 2,
            )

        assertEquals(0.0078125f, points.first().x, 0.000001f)
        assertEquals(0.9921875f, points.last().x, 0.000001f)
        assertEquals(1f, points.first().y, 0.000001f)
        assertEquals(0.25f, points.last().y, 0.000001f)
        assertTrue(points.zipWithNext().all { (left, right) -> left.x < right.x })
    }

    @Test
    fun floatArrayPlottingSanitizesNonFiniteBands() {
        val buckets = FloatArray(64) { -24f }
        buckets[0] = Float.NaN

        val points = spectrumCurvePoints(buckets, sampleRate = 48_000, subdivisions = 1)

        assertEquals(1f, points[0].y, 0f)
        assertEquals(0.5f, points[1].y, 0.000001f)
    }

}

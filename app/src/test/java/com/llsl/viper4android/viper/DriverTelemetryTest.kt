package com.llsl.viper4android.viper

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverTelemetryTest {
    @Test
    fun parsesVersionedLittleEndianPayload() {
        val payload = telemetryPayload(
            sequence = 42,
            validMask = DriverTelemetry.SPECTRUM_VALID or DriverTelemetry.METERS_VALID,
            spectrum = List(64) { -96f + it },
            meters = List(8) { it + 0.5f },
        )

        val telemetry = DriverTelemetry.parse(payload)!!

        assertEquals(42, telemetry.sequence)
        assertEquals(48_000, telemetry.sampleRate)
        assertEquals(2048, telemetry.fftSize)
        assertEquals(7, telemetry.overrunCount)
        assertTrue(telemetry.hasSpectrum)
        assertTrue(telemetry.hasMeters)
        assertEquals(-96f, telemetry.spectrumDb.first(), 0f)
        assertEquals(-33f, telemetry.spectrumDb.last(), 0f)
        assertEquals(5.5f, telemetry.meterDb[5], 0f)
    }

    @Test
    fun preservesValuesButHonorsValidityMask() {
        val telemetry = DriverTelemetry.parse(telemetryPayload(validMask = 0))!!

        assertFalse(telemetry.hasSpectrum)
        assertFalse(telemetry.hasMeters)
        assertEquals(64, telemetry.spectrumDb.size)
        assertEquals(8, telemetry.meterDb.size)
    }

    @Test
    fun rejectsMalformedOrUnsupportedPayloads() {
        assertNull(DriverTelemetry.parse(ByteArray(319)))
        assertNull(DriverTelemetry.parse(telemetryPayload(version = 2)))
        assertNull(DriverTelemetry.parse(telemetryPayload(spectrumCount = 63)))
        assertNull(DriverTelemetry.parse(telemetryPayload(meterCount = 7)))
        assertNull(
            DriverTelemetry.parse(
                telemetryPayload(spectrum = List(64) { if (it == 12) Float.NaN else -40f }),
            ),
        )
        assertNull(
            DriverTelemetry.parse(
                telemetryPayload(meters = List(8) { if (it == 2) Float.POSITIVE_INFINITY else 0f }),
            ),
        )
    }

    @Test
    fun duplicateSequenceKeepsTheCurrentSnapshotWhileDisconnectClearsIt() {
        val current = DriverTelemetry.parse(telemetryPayload(sequence = 12))!!
        val duplicate =
            DriverTelemetry.parse(
                telemetryPayload(sequence = 12, spectrum = List(64) { -12f }),
            )!!
        val next = DriverTelemetry.parse(telemetryPayload(sequence = 13))!!

        assertTrue(mergeDriverTelemetry(current, duplicate) === current)
        assertTrue(mergeDriverTelemetry(current, next) === next)
        assertNull(mergeDriverTelemetry(current, null))
    }

    @Test
    fun parsesIemTelemetryV3AndRejectsMalformedPayloads() {
        val payload =
            ByteBuffer.allocate(IemDriverTelemetry.WIRE_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    putInt(IemDriverTelemetry.VERSION)
                    putInt(IemDriverTelemetry.WIRE_SIZE)
                    repeat(11) { putLong((it + 1).toLong()) }
                    putInt(48_000)
                    putInt(96_000)
                    putInt(1024)
                    putInt(0)
                    putInt(1)
                    putInt(1)
                    putInt(17)
                    putInt(3)
                    putInt(0)
                    putInt(3)
                    putInt(1)
                    putInt(1024)
                    putInt(1)
                    putInt(4)
                    putInt(1)
                    putFloat(21.333334f)
                    putFloat(2.5f)
                }.array()

        val telemetry = IemDriverTelemetry.parse(payload)!!
        assertEquals(1L, telemetry.sequence)
        assertEquals(11L, telemetry.graphGeneration)
        assertEquals(17, telemetry.activeGrains)
        assertEquals(3, telemetry.encoderMode)
        assertEquals(0, telemetry.renderMode)
        assertEquals(3, telemetry.ambisonicsOrder)
        assertTrue(telemetry.haloPrepared)
        assertEquals(1024, telemetry.haloStftLatencyFrames)
        assertEquals(1, telemetry.dialogNetResult)
        assertTrue(telemetry.enabled)
        assertEquals(21.333334f, telemetry.latencyMs, 0f)
        assertNull(IemDriverTelemetry.parse(ByteArray(IemDriverTelemetry.WIRE_SIZE - 1)))
        payload[0] = 1
        assertNull(IemDriverTelemetry.parse(payload))
    }

    private fun telemetryPayload(
        version: Int = 1,
        sequence: Int = 1,
        sampleRate: Int = 48_000,
        fftSize: Int = 2048,
        spectrumCount: Int = 64,
        meterCount: Int = 8,
        validMask: Int = DriverTelemetry.SPECTRUM_VALID,
        spectrum: List<Float> = List(64) { -48f },
        meters: List<Float> = List(8) { 0f },
    ): ByteArray =
        ByteBuffer.allocate(DriverTelemetry.WIRE_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(version)
                putInt(sequence)
                putInt(sampleRate)
                putInt(fftSize)
                putInt(spectrumCount)
                putInt(meterCount)
                putInt(validMask)
                putInt(7)
                spectrum.forEach(::putFloat)
                meters.forEach(::putFloat)
            }.array()
}

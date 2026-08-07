package com.llsl.viper4android.ui.components.viper

import androidx.compose.ui.geometry.Offset
import com.llsl.viper4android.dsp.SpectrumBallisticsState
import com.llsl.viper4android.viper.DriverTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VstSpectrumGraphTest {
    @Test
    fun spectrumAreaClosesAtItsOwnFrequencyBounds() {
        val area = spectrumAreaPolygon(listOf(Offset(0.1f, 0.6f), Offset(0.9f, 0.4f)))

        assertEquals(
            listOf(
                Offset(0.1f, 1f),
                Offset(0.1f, 0.6f),
                Offset(0.9f, 0.4f),
                Offset(0.9f, 1f),
            ),
            area,
        )
        assertEquals(emptyList<Offset>(), spectrumAreaPolygon(listOf(Offset(Float.NaN, 0.5f))))
    }

    @Test
    fun graphLayerExistsOnlyWhileSpectrumOrPeaksAreVisible() {
        assertNull(spectrumGraphLayer(SpectrumBallisticsState()))
        val settled =
            SpectrumBallisticsState(
                sampleRate = 48_000,
                envelopeDb = FloatArray(DriverTelemetry.SPECTRUM_COUNT) { -72f },
                peakDb = FloatArray(DriverTelemetry.SPECTRUM_COUNT) { -80f },
                hasInput = true,
            )
        assertNull(spectrumGraphLayer(settled))

        val active =
            SpectrumBallisticsState(
                sampleRate = 48_000,
                envelopeDb = FloatArray(DriverTelemetry.SPECTRUM_COUNT) { -48f },
                peakDb = FloatArray(DriverTelemetry.SPECTRUM_COUNT) { -36f },
                hasInput = true,
            )
        val layer = spectrumGraphLayer(active)
        assertNotNull(layer)

        assertEquals(190, layer!!.envelope.size)
        assertEquals(190, layer.peaks.size)
        assertEquals(0.75f, layer.envelope.first().y, 0.000001f)
        assertEquals(0.625f, layer.peaks.first().y, 0.000001f)
    }
}

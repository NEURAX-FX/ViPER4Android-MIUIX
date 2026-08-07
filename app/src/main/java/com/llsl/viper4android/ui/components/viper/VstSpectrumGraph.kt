package com.llsl.viper4android.ui.components.viper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import com.llsl.viper4android.dsp.SPECTRUM_DISPLAY_MIN_DB
import com.llsl.viper4android.dsp.SpectrumBallisticsState
import com.llsl.viper4android.dsp.advanceSpectrumBallistics
import com.llsl.viper4android.dsp.spectrumCurvePoints
import com.llsl.viper4android.viper.DriverTelemetry
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@Immutable
data class SpectrumGraphLayer(
    val envelope: List<Offset>,
    val peaks: List<Offset>,
)

internal fun spectrumGraphLayer(state: SpectrumBallisticsState): SpectrumGraphLayer? {
    if (!state.hasInput || state.sampleRate <= 0) return null
    val hasVisibleEnergy =
        state.envelopeDb.any { it > SPECTRUM_DISPLAY_MIN_DB } ||
            state.peakDb.any { it > SPECTRUM_DISPLAY_MIN_DB }
    if (!hasVisibleEnergy) return null
    return SpectrumGraphLayer(
        envelope = spectrumCurvePoints(state.envelopeDb, state.sampleRate),
        peaks = spectrumCurvePoints(state.peakDb, state.sampleRate),
    )
}

@Composable
internal fun rememberSpectrumGraphLayer(telemetry: DriverTelemetry?): SpectrumGraphLayer? {
    val latestTelemetry = rememberUpdatedState(telemetry)
    var ballistics by remember { mutableStateOf(SpectrumBallisticsState()) }

    LaunchedEffect(Unit) {
        while (currentCoroutineContext().isActive) {
            withFrameNanos { frameTimeNanos ->
                val next =
                    advanceSpectrumBallistics(
                        previous = ballistics,
                        telemetry = latestTelemetry.value,
                        frameTimeNanos = frameTimeNanos,
                    )
                if (next !== ballistics) ballistics = next
            }
        }
    }

    return remember(ballistics) { spectrumGraphLayer(ballistics) }
}

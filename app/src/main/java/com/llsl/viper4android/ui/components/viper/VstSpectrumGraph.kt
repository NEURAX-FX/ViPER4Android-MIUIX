package com.llsl.viper4android.ui.components.viper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.dsp.SPECTRUM_FLOOR_DB
import com.llsl.viper4android.dsp.interpolateSpectrum
import com.llsl.viper4android.dsp.smoothSpectrum
import com.llsl.viper4android.dsp.spectrumCurvePoints
import com.llsl.viper4android.viper.DriverTelemetry

@Composable
fun VstSpectrumGraph(
    telemetry: DriverTelemetry,
    verticalGridLines: List<GraphGridLine>,
    horizontalGridLines: List<GraphGridLine>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    graphHeight: Dp = 170.dp,
) {
    val emptySpectrum = remember { List(DriverTelemetry.SPECTRUM_COUNT) { SPECTRUM_FLOOR_DB } }
    var from by remember { mutableStateOf(emptySpectrum) }
    var target by remember { mutableStateOf(emptySpectrum) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(telemetry.sequence) {
        if (!telemetry.hasSpectrum || telemetry.spectrumDb.size != DriverTelemetry.SPECTRUM_COUNT) {
            return@LaunchedEffect
        }
        val current = interpolateSpectrum(from, target, progress.value)
        from = current
        target = smoothSpectrum(current, telemetry.spectrumDb)
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 80, easing = LinearEasing),
        )
    }

    val animated = interpolateSpectrum(from, target, progress.value)
    val curve = remember(animated) { spectrumCurvePoints(animated) }
    VstResponseGraph(
        handles = emptyList(),
        curve = curve,
        interactive = false,
        graphHeight = graphHeight,
        verticalGridLines = verticalGridLines,
        horizontalGridLines = horizontalGridLines,
        showGridLabels = true,
        contentDescription = contentDescription,
        onHandleDrag = { _, _, _ -> },
        modifier = modifier,
    )
}

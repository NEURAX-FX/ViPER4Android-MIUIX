package com.llsl.viper4android.ui.components.viper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import kotlin.math.cos
import kotlin.math.sin
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val KnobSize = 58.dp
private val KnobStroke = 4.dp
private val KnobDragTravel = 160.dp
private const val KnobStartAngle = 135f
private const val KnobSweepAngle = 270f

internal fun knobExactInput(
    value: Double,
    inputValue: (Double) -> String,
): String = inputValue(value)

internal fun parseKnobExactInput(
    text: String,
    parseInput: (String) -> Double? = { it.trim().toDoubleOrNull() },
): Double? = parseInput(text.trim())?.takeIf { it.isFinite() }

@Composable
fun VstKnob(
    label: String,
    value: Double,
    valueRange: ClosedFloatingPointRange<Double>,
    onValueChange: (Double) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    formatValue: (Double) -> String = { "%.1f".format(it) },
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledReason: String? = null,
    onValueChangeStarted: () -> Unit = {},
    inputValue: (Double) -> String = { it.toString() },
    parseInput: (String) -> Double? = { it.trim().toDoubleOrNull() },
) {
    var showInput by remember { mutableStateOf(false) }
    var input by remember(value) { mutableStateOf(TextFieldValue(knobExactInput(value, inputValue))) }
    var inputError by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnValueChangeStarted by rememberUpdatedState(onValueChangeStarted)
    val invalidValueText = stringResource(R.string.editor_invalid_value)
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0.0 } ?: 1.0
    val fraction = ((value - valueRange.start) / span).toFloat().coerceIn(0f, 1f)
    val knobSurface = MiuixTheme.colorScheme.surfaceContainerHigh
    val knobTrack = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f)
    val knobProgress = MiuixTheme.colorScheme.primary
    val summaryColor = MiuixTheme.colorScheme.outline
    val valueColor = MiuixTheme.colorScheme.primary

    Column(
        modifier = modifier.padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .size(KnobSize)
                 .semantics {
                     contentDescription = buildString {
                         append(label)
                         disabledReason?.let {
                             append(", ")
                             append(it)
                         }
                     }
                     if (!enabled) disabled()
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = value.toFloat(),
                        range = valueRange.start.toFloat()..valueRange.endInclusive.toFloat(),
                    )
                     setProgress { target ->
                         if (!enabled) return@setProgress false
                         latestOnValueChangeStarted()
                         latestOnValueChange(target.toDouble().coerceIn(valueRange))
                         latestOnValueChangeFinished()
                         true
                     }
                 }
                 .pointerInput(valueRange, enabled) {
                     if (!enabled) return@pointerInput
                     val travel = KnobDragTravel.toPx()
                     detectDragGestures(
                         onDragStart = {
                             dragging = true
                             dragValue = value
                             latestOnValueChangeStarted()
                         },
                        onDrag = { change, dragAmount ->
                            val delta = -dragAmount.y / travel * span
                            dragValue = (dragValue + delta).coerceIn(valueRange)
                            latestOnValueChange(dragValue)
                            change.consume()
                        },
                         onDragEnd = {
                             dragging = false
                             latestOnValueChangeFinished()
                         },
                         onDragCancel = {
                             dragging = false
                             latestOnValueChangeFinished()
                        },
                    )
                },
        ) {
            val stroke = KnobStroke.toPx()
            val inset = stroke / 2f
            val diameter = size.minDimension - stroke
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcSize = Size(diameter, diameter)
            val arcOffset = Offset(center.x - diameter / 2f, center.y - diameter / 2f)
            drawCircle(knobSurface, radius = diameter / 2f - inset, center = center)
            drawArc(
                color = knobTrack,
                startAngle = KnobStartAngle,
                sweepAngle = KnobSweepAngle,
                useCenter = false,
                topLeft = arcOffset,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = knobProgress.copy(alpha = if (enabled) 1f else 0.38f),
                startAngle = KnobStartAngle,
                sweepAngle = KnobSweepAngle * fraction,
                useCenter = false,
                topLeft = arcOffset,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val angle = Math.toRadians((KnobStartAngle + KnobSweepAngle * fraction).toDouble())
            val innerRadius = diameter / 2f - stroke * 2.4f
            val outerRadius = diameter / 2f - stroke * 0.9f
            drawLine(
                color = knobProgress,
                start = Offset(
                    center.x + (cos(angle) * innerRadius).toFloat(),
                    center.y + (sin(angle) * innerRadius).toFloat(),
                ),
                end = Offset(
                    center.x + (cos(angle) * outerRadius).toFloat(),
                    center.y + (sin(angle) * outerRadius).toFloat(),
                ),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = summaryColor,
        )
        Text(
            text = formatValue(if (dragging) dragValue else value),
            style = MiuixTheme.textStyles.body1,
            color = valueColor.copy(alpha = if (enabled) 1f else 0.50f),
            modifier = Modifier.clickable(
                enabled = enabled,
                onClickLabel = stringResource(R.string.editor_exact_value),
            ) {
                input = TextFieldValue(knobExactInput(value, inputValue))
                inputError = false
                showInput = true
            },
        )
    }

    if (showInput) {
        ViperTextFieldDialog(
            show = true,
            onDismissRequest = { showInput = false },
            title = label,
            value = input,
            onValueChange = {
                input = it
                inputError = false
            },
            confirmText = stringResource(R.string.action_update),
            onConfirm = {
                parseKnobExactInput(input.text, parseInput)?.let { parsed ->
                    latestOnValueChangeStarted()
                    latestOnValueChange(parsed.coerceIn(valueRange))
                    latestOnValueChangeFinished()
                    inputError = false
                    showInput = false
                } ?: run {
                    inputError = true
                }
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showInput = false },
            label = stringResource(R.string.editor_exact_value),
            summary = invalidValueText.takeIf { inputError },
        )
    }
}

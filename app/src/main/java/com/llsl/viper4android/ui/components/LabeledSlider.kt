package com.llsl.viper4android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlin.math.roundToInt

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    valueLabel: String? = null,
) {
    var showPrecisionInput by remember { mutableStateOf(false) }
    var precisionInput by remember { mutableStateOf(TextFieldValue(initialPrecisionInput(value))) }
    val parsedInputValue = parseSliderPrecisionInput(precisionInput.text, valueRange)
    val confirmEnabled = parsedInputValue != null

    fun openPrecisionInput() {
        precisionInput = TextFieldValue(initialPrecisionInput(value))
        showPrecisionInput = true
    }

    fun confirmPrecisionInput() {
        parsedInputValue?.let {
            onValueChange(it)
            showPrecisionInput = false
        }
    }

    val titleColor =
        if (enabled) {
            MiuixTheme.colorScheme.onSurface
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.62f)
        }
    val valueColor =
        if (enabled) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.62f)
        }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = titleColor,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                showKeyPoints = steps > 0 && steps < 10,
                height = 25.dp,
                modifier = Modifier
                    .weight(1f)
                    .offset(x = (-4).dp),
            )
            Text(
                text = valueLabel ?: value.roundToInt().toString(),
                style = MiuixTheme.textStyles.body2,
                color = valueColor,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .widthIn(min = 72.dp)
                    .clickable(enabled = enabled) { openPrecisionInput() },
            )
        }
    }

    WindowDialog(
        show = showPrecisionInput,
        onDismissRequest = { showPrecisionInput = false },
        title = label,
        summary = stringResource(
            R.string.slider_precision_summary,
            initialPrecisionInput(valueRange.start),
            initialPrecisionInput(valueRange.endInclusive),
        ),
    ) {
        TextField(
            value = precisionInput,
            onValueChange = { precisionInput = it },
            label = stringResource(R.string.slider_precision_hint),
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { confirmPrecisionInput() },
            ),
            textStyle = MiuixTheme.textStyles.body2.copy(
                color = MiuixTheme.colorScheme.onBackground,
            ),
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            labelColor = MiuixTheme.colorScheme.onBackground,
            borderColor = MiuixTheme.colorScheme.outline,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = stringResource(R.string.action_cancel),
                onClick = { showPrecisionInput = false },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.action_update),
                onClick = { confirmPrecisionInput() },
                enabled = confirmEnabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

fun parseSliderPrecisionInput(input: String, valueRange: ClosedFloatingPointRange<Float>): Float? {
    val match = Regex("[-+]?\\d+(?:[.,]\\d+)?").find(input.trim()) ?: return null
    val parsed = match.value.replace(',', '.').toFloatOrNull() ?: return null
    return parsed.coerceIn(valueRange.start, valueRange.endInclusive)
}

fun initialPrecisionInput(value: Float): String {
    val rounded = value.roundToInt()
    return if (value == rounded.toFloat()) rounded.toString() else value.toString()
}

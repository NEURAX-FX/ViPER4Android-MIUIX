package com.llsl.viper4android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.data.model.EqPreset
import com.llsl.viper4android.dsp.DEFAULT_GRAPH_SAMPLE_RATE
import com.llsl.viper4android.dsp.firGraphModel
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.EqState
import com.llsl.viper4android.ui.components.viper.GraphHandle
import com.llsl.viper4android.ui.components.viper.ViperDialog
import com.llsl.viper4android.ui.components.viper.ViperIconButton
import com.llsl.viper4android.ui.components.viper.ViperTextFieldDialog
import com.llsl.viper4android.ui.components.viper.VstResponseGraph
import com.llsl.viper4android.ui.theme.ViperType
import com.llsl.viper4android.viper.ViperDispatcher
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val DB_MIN = -12f
private const val DB_MAX = 12f
private val DB_GRID_LINES = listOf(-12f, -6f, 0f, 6f, 12f)

@Composable
private fun enabledIconTint(enabled: Boolean): Color =
    if (enabled) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

@Composable
fun EqCurveGraph(
    bands: List<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bandCount: Int = 10,
    sampleRate: Int = DEFAULT_GRAPH_SAMPLE_RATE,
) {
    // The home-screen preview and this dialog share the driver-derived FIR model so the
    // curve never diverges from the dedicated editor or from the actual DSP response.
    val model = remember(bands, bandCount, sampleRate) {
        firGraphModel(
            EffectState(
                eq = EqState(
                    bandCount = bandCount,
                    bands = bands.take(bandCount).map { it.toDouble() },
                ),
            ),
            sampleRate,
        )
    }
    val handleColors = listOf(MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.secondary)
    val handles = remember(model.handles, handleColors) {
        model.handles.mapIndexed { index, point ->
            GraphHandle(
                id = point.id,
                x = point.x,
                y = point.y,
                color = handleColors[index % handleColors.size],
                label = point.label,
                valueDescription = point.valueDescription,
            )
        }
    }
    val graphModifier =
        if (modifier == Modifier) {
            Modifier
                .fillMaxWidth()
                .height(180.dp)
        } else {
            modifier.fillMaxWidth()
        }

    Card(
        modifier = graphModifier.clip(RoundedCornerShape(12.dp)),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        onClick = onClick,
    ) {
        VstResponseGraph(
            handles = handles,
            curve = model.curve,
            interactive = false,
            graphHeight = 180.dp,
            onClick = onClick,
            onHandleDrag = { _, _, _ -> },
            modifier = Modifier.fillMaxSize().padding(4.dp),
        )
    }
}

@Composable
fun EqEditDialog(
    bands: List<Float>,
    onBandsChange: (List<Double>) -> Unit,
    presetId: Long?,
    presets: List<EqPreset>,
    onPresetSelect: (Long) -> Unit,
    onPresetAdd: (String) -> Unit,
    onPresetDelete: (Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    bandCount: Int = 10,
) {
    val localBands =
        remember(bandCount) {
            mutableStateListOf<Float>().apply { addAll(bands.take(bandCount)) }
        }

    LaunchedEffect(bands) {
        val incoming = bands.take(bandCount)
        if (incoming != localBands.toList()) {
            localBands.clear()
            localBands.addAll(incoming)
        }
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf(TextFieldValue("")) }

    ViperDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.section_equalizer),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = onDismiss,
        content = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                EqCurveGraph(
                    bands = localBands.toList(),
                    onClick = {},
                    modifier = Modifier.height(160.dp),
                    bandCount = bandCount,
                )

                Spacer(modifier = Modifier.height(12.dp))

                val presetNames = presets.map { resolvePresetName(it) }
                val selectedPresetName =
                    presets.find { it.id == presetId }?.let { resolvePresetName(it) }
                        ?: stringResource(R.string.label_custom)

                LabeledDropdown(
                    label = stringResource(R.string.label_preset),
                    selectedValue = selectedPresetName,
                    options = presetNames,
                    onOptionSelected = { index, _ -> onPresetSelect(presets[index].id) },
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.action_save),
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.action_delete),
                        onClick = { presetId?.let { onPresetDelete(it) } },
                        enabled = presetId != null,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.action_reset),
                        onClick = {
                            for (i in localBands.indices) {
                                localBands[i] = 0f
                            }
                            onReset()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val bandLabels = ViperDispatcher.eqBandLabelsForCount(bandCount)

                bandLabels.forEachIndexed { index, label ->
                    if (index < localBands.size) {
                        val atMin = localBands[index] <= DB_MIN
                        val atMax = localBands[index] >= DB_MAX

                        val applyBandChange = { newVal: Float ->
                            localBands[index] = newVal.coerceIn(DB_MIN, DB_MAX)
                            onBandsChange(localBands.map { it.toDouble() })
                        }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = label,
                                style = ViperType.caption,
                                modifier = Modifier.width(48.dp),
                            )
                            ViperIconButton(
                                onClick = {
                                    val stepped = ((localBands[index] * 10).roundToInt() - 1) / 10f
                                    applyBandChange(stepped)
                                },
                                enabled = !atMin,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = enabledIconTint(!atMin),
                                )
                            }
                            Slider(
                                value = localBands[index],
                                onValueChange = { applyBandChange(it) },
                                valueRange = DB_MIN..DB_MAX,
                                modifier = Modifier.weight(1f),
                            )
                            ViperIconButton(
                                onClick = {
                                    val stepped = ((localBands[index] * 10).roundToInt() + 1) / 10f
                                    applyBandChange(stepped)
                                },
                                enabled = !atMax,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = enabledIconTint(!atMax),
                                )
                            }
                            Text(
                                text = "${"%.1f".format(localBands[index])}dB",
                                style = ViperType.value,
                                modifier = Modifier.width(52.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        },
    )

    if (showSaveDialog) {
        ViperTextFieldDialog(
            show = true,
            onDismissRequest = { showSaveDialog = false },
            title = stringResource(R.string.preset_save_title),
            value = presetNameInput,
            onValueChange = { presetNameInput = it },
            label = stringResource(R.string.preset_name_hint),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {
                val name = presetNameInput.text.trim()
                if (name.isNotBlank()) {
                    onPresetAdd(name)
                    presetNameInput = TextFieldValue("")
                    showSaveDialog = false
                }
            },
            confirmEnabled = presetNameInput.text.isNotBlank(),
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { showSaveDialog = false },
        )
    }
}

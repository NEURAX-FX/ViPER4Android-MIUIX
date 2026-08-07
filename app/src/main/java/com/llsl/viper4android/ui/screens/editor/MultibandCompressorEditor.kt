package com.llsl.viper4android.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.dsp.graphXToFrequency
import com.llsl.viper4android.dsp.graphYToDb
import com.llsl.viper4android.dsp.MultibandRatioLabel
import com.llsl.viper4android.dsp.multibandRatioLabel
import com.llsl.viper4android.dsp.ratioCoefficientForOutput
import com.llsl.viper4android.dsp.safeMultibandCrossoverMax
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.MULTIBAND_MIN_FREQUENCY
import com.llsl.viper4android.effect.MultibandCompressorState
import com.llsl.viper4android.viper.DriverTelemetry
import com.llsl.viper4android.ui.components.viper.GraphGridLine
import com.llsl.viper4android.ui.components.LabeledSwitch
import com.llsl.viper4android.ui.components.viper.GraphHandle
import com.llsl.viper4android.ui.components.viper.VstBandItem
import com.llsl.viper4android.ui.components.viper.VstBandStrip
import com.llsl.viper4android.ui.components.viper.VstControlGroup
import com.llsl.viper4android.ui.components.viper.VstExpandableControlGroup
import com.llsl.viper4android.ui.components.viper.VstGraphWorkspace
import com.llsl.viper4android.ui.components.viper.VstKnob
import com.llsl.viper4android.ui.components.viper.VstResponseGraph
import com.llsl.viper4android.ui.components.viper.VstSpectrumGraph
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MultibandCompressorEditor(
    state: EffectState,
    sampleRate: Int,
    telemetry: DriverTelemetry? = null,
    onAction: (MultibandEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedBand by remember { mutableIntStateOf(0) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    val pendingCrossovers = remember { mutableStateMapOf<Int, Int>() }
    val presentation = remember(state.multibandCompressor, sampleRate) {
        multibandEditorPresentation(state, sampleRate)
    }
    val compressor = state.multibandCompressor
    val gainReduction =
        if (telemetry?.hasMeters == true) {
            telemetry.meterDb.take(5)
        } else {
            List(5) { 0f }
        }
    val band = selectedBand.coerceIn(presentation.bands.indices)
    val transfer = remember(state.multibandCompressor, band) {
        multibandTransferPresentation(state, band)
    }
    val primary = MiuixTheme.colorScheme.primary
    val secondary = MiuixTheme.colorScheme.secondary
    val context = LocalContext.current
    val ratioLimitText = stringResource(R.string.editor_ratio_limit)
    val bandColors = remember(primary, secondary) { multibandBandColors(primary, secondary) }
    val localizedRegions =
        presentation.bandRegions.mapIndexed { index, region ->
            region.copy(label = stringResource(R.string.editor_band_number, index + 1))
        }
    val handles =
        presentation.crossoverHandles.map { handle ->
            GraphHandle(
                id = handle.id,
                x = handle.x,
                y = handle.y,
                color = bandColors[handle.controlledBand],
                label = "${stringResource(R.string.editor_crossovers)} ${handle.crossoverIndex + 1}",
                dragAxis = handle.dragAxis,
                valueDescription = "${handle.frequencyHz} Hz, ${handle.gainDb} dB",
                badge = handle.badge,
            )
        }
    val ratioText =
        multibandRatioText(
            rawRatio = compressor.ratios[band],
            limitText = ratioLimitText,
            overText = { percent -> context.getString(R.string.editor_ratio_over, percent) },
        )
    val transferHandles =
        transfer.handles.map { handle ->
            GraphHandle(
                id = handle.id,
                x = handle.x,
                y = handle.y,
                color = bandColors[band],
                label =
                    when (handle.id) {
                        "threshold" -> stringResource(R.string.label_threshold)
                        "ratio" -> stringResource(R.string.label_fet_ratio)
                        else -> stringResource(R.string.label_fet_knee)
                    },
                dragAxis = handle.dragAxis,
                valueDescription =
                    when (handle.id) {
                        "threshold" -> "${compressor.thresholds[band]} dB"
                        "ratio" -> ratioText
                        else -> "${compressor.knees[band]} dB"
                    },
                enabled = handle.enabled,
                badge = handle.badge,
            )
        }
    val emitGraphValue: (String, Float, Float, Boolean) -> Unit = { id, x, y, last ->
        val index = id.removePrefix("crossover-").toIntOrNull()
        if (index != null && index in presentation.crossoverHandles.indices) {
            onAction(
                MultibandEditorAction.SetCrossoverHandle(
                    crossover = index,
                    frequency = graphXToFrequency(x, sampleRate).roundToInt(),
                    gain = graphYToDb(y, -48.0, 24.0).roundToInt().coerceIn(0, 24),
                    last = last,
                ),
            )
        }
    }
    val emitTransferValue: (String, Float, Float, Boolean) -> Unit = { id, x, y, last ->
        val control: MultibandIntControl
        val value: Int
        when (id) {
            "threshold" -> {
                control = MultibandIntControl.THRESHOLD
                value = xToLinearValue(x, -60.0, 0.0).roundToInt().coerceIn(-48, 0)
            }
            "ratio" -> {
                control = MultibandIntControl.RATIO
                value =
                    ratioCoefficientForOutput(
                        inputDb = 0.0,
                        outputDb = yToLinearValue(y, -60.0, 24.0),
                        spec = transfer.transferSpec,
                    )
            }
            else -> {
                control = MultibandIntControl.KNEE
                val inputDb = xToLinearValue(x, -60.0, 0.0)
                value =
                    (2.0 * abs(inputDb - transfer.transferSpec.thresholdDb))
                        .roundToInt()
                        .coerceIn(0, 12)
            }
        }
        onAction(MultibandEditorAction.SetInt(control, band, value, last))
    }

    VstGraphWorkspace(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        sideBySideAtWideWidth = true,
        scrollContent = true,
        graph = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (telemetry?.hasSpectrum == true) {
                    Text(
                        text = stringResource(R.string.editor_graph_live_spectrum_title),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    VstSpectrumGraph(
                        telemetry = telemetry,
                        verticalGridLines = editorFrequencyGrid(sampleRate),
                        horizontalGridLines = editorDecibelGrid(-96.0, 0.0, 24.0),
                        contentDescription = stringResource(R.string.editor_graph_live_spectrum),
                    )
                }
                Text(
                    text = stringResource(R.string.editor_graph_multiband_crossover_title),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                VstResponseGraph(
                    handles = handles,
                    bandCurves = presentation.graph.bandCurves,
                    referenceCurves = listOf(presentation.graph.unitySumCurve),
                    bandCurveColors = bandColors,
                    bandRegions = localizedRegions,
                    selectedBandRegionIndex = band,
                    selectedHandleId = "crossover-$band".takeIf { band < handles.size },
                    verticalGridLines = editorFrequencyGrid(sampleRate),
                    horizontalGridLines = editorDecibelGrid(-48.0, 24.0, 12.0),
                    showGridLabels = true,
                    contentDescription = stringResource(R.string.editor_graph_multiband),
                    onHandleSelected = { id ->
                        id.removePrefix("crossover-").toIntOrNull()?.let { selectedBand = it }
                    },
                    onBandRegionSelected = { selectedBand = it },
                    onHandleDragStart = { onAction(MultibandEditorAction.BeginGesture) },
                    onHandleDrag = { id, x, y -> emitGraphValue(id, x, y, false) },
                    onHandleDragSettled = { id, x, y ->
                        emitGraphValue(id, x, y, true)
                        onAction(MultibandEditorAction.SettleGesture)
                        onAction(MultibandEditorAction.Flush)
                    },
                )
                Text(
                    text = stringResource(R.string.editor_graph_multiband_transfer_title, band + 1),
                    style = MiuixTheme.textStyles.body2,
                    color = bandColors[band],
                )
                VstResponseGraph(
                    handles = transferHandles,
                    curve = transfer.curve,
                    referenceCurves = listOf(transfer.referenceCurve),
                    curveDashed = transfer.curveDashed,
                    selectedHandleId = "threshold",
                    verticalGridLines = transferInputGrid(),
                    horizontalGridLines = transferOutputGrid(),
                    showGridLabels = true,
                    contentDescription = stringResource(R.string.editor_graph_multiband_transfer, band + 1),
                    onHandleDragStart = { onAction(MultibandEditorAction.BeginGesture) },
                    onHandleDrag = { id, x, y -> emitTransferValue(id, x, y, false) },
                    onHandleDragSettled = { id, x, y ->
                        emitTransferValue(id, x, y, true)
                        onAction(MultibandEditorAction.SettleGesture)
                        onAction(MultibandEditorAction.Flush)
                    },
                )
                if (compressor.kneeAutos[band]) {
                    AutoStateText(stringResource(R.string.editor_auto_knee_curve_unavailable))
                }
                if (compressor.gainAutos[band]) {
                    AutoStateText(stringResource(R.string.editor_auto_gain_curve_excluded))
                }
                if (compressor.attackAutos[band] || compressor.releaseAutos[band]) {
                    AutoStateText(stringResource(R.string.editor_auto_timing_not_shown))
                }
            }
        },
    ) {
        VstBandStrip(
            items =
                presentation.bands.map { item ->
                    VstBandItem(
                        id = item.index.toString(),
                        title = stringResource(R.string.editor_band_number, item.index + 1),
                        value =
                            if (item.compressionEnabled) {
                                stringResource(
                                    R.string.editor_band_threshold_gr,
                                    item.thresholdDb,
                                    gainReduction[item.index],
                                )
                            } else {
                                stringResource(R.string.editor_bypassed)
                            },
                        color = bandColors[item.index],
                    )
                },
            selectedIndex = band,
            onSelected = { selectedBand = it },
        )
        VstControlGroup(title = stringResource(R.string.editor_band_number, band + 1)) {
            LabeledSwitch(
                label = stringResource(R.string.editor_band_compression_enabled, band + 1),
                checked = presentation.bands[band].compressionEnabled,
                onCheckedChange = { enabled ->
                    onAction(MultibandEditorAction.BeginGesture)
                    onAction(
                        MultibandEditorAction.SetBoolean(
                            control = MultibandBooleanControl.BAND_ENABLE,
                            band = band,
                            value = enabled,
                            last = true,
                        ),
                    )
                    onAction(MultibandEditorAction.SettleGesture)
                    onAction(MultibandEditorAction.Flush)
                },
            )
            PrimaryDynamicsControls(
                compressor = compressor,
                band = band,
                availability = transfer.controls,
                onAction = onAction,
            )
        }
        VstExpandableControlGroup(
            title = stringResource(R.string.editor_advanced),
            expanded = advancedExpanded,
            onExpandedChange = { advancedExpanded = it },
            testTag = "multiband-advanced",
        ) {
            AdvancedDynamicsControls(
                compressor = compressor,
                band = band,
                availability = transfer.controls,
                onAction = onAction,
            )
        }
        VstControlGroup(title = stringResource(R.string.editor_crossovers)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = 3,
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                presentation.graph.crossovers.forEachIndexed { index, crossover ->
                    VstKnob(
                        label = "XO ${index + 1}",
                        value = crossover,
                        valueRange = MULTIBAND_MIN_FREQUENCY.toDouble()..safeMultibandCrossoverMax(sampleRate).toDouble(),
                        onValueChange = { value ->
                            val frequency = value.roundToInt()
                            pendingCrossovers[index] = frequency
                            onAction(
                                MultibandEditorAction.SetCrossoverHandle(
                                    crossover = index,
                                    frequency = frequency,
                                    gain = presentation.bands[index].gainDb,
                                    last = false,
                                ),
                            )
                        },
                        onValueChangeStarted = { onAction(MultibandEditorAction.BeginGesture) },
                        onValueChangeFinished = {
                            onAction(
                                MultibandEditorAction.SetCrossoverHandle(
                                    crossover = index,
                                    frequency = pendingCrossovers.remove(index) ?: crossover.roundToInt(),
                                    gain = presentation.bands[index].gainDb,
                                    last = true,
                                ),
                            )
                            onAction(MultibandEditorAction.SettleGesture)
                            onAction(MultibandEditorAction.Flush)
                        },
                        formatValue = { "${it.roundToInt()} Hz" },
                        inputValue = { it.roundToInt().toString() },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryDynamicsControls(
    compressor: MultibandCompressorState,
    band: Int,
    availability: MultibandControlAvailability,
    onAction: (MultibandEditorAction) -> Unit,
) {
    val context = LocalContext.current
    val ratioLimitText = stringResource(R.string.editor_ratio_limit)
    val autoKneeReason = stringResource(R.string.editor_disabled_auto_knee)
    val autoGainReason = stringResource(R.string.editor_disabled_auto_gain)
    val autoAttackReason = stringResource(R.string.editor_disabled_auto_attack)
    val autoReleaseReason = stringResource(R.string.editor_disabled_auto_release)

    KnobFlow {
        MultibandIntKnob(
            label = stringResource(R.string.label_threshold),
            value = compressor.thresholds[band],
            range = -48..0,
            control = MultibandIntControl.THRESHOLD,
            band = band,
            onAction = onAction,
            formatValue = { "$it dB" },
        )
    }
    MultibandAutoSwitch(
        label = stringResource(R.string.label_fet_auto_knee),
        checked = compressor.kneeAutos[band],
        control = MultibandBooleanControl.KNEE_AUTO,
        band = band,
        onAction = onAction,
    )
    KnobFlow {
        MultibandIntKnob(
            label = stringResource(R.string.label_fet_ratio),
            value = compressor.ratios[band],
            range = 0..200,
            control = MultibandIntControl.RATIO,
            band = band,
            onAction = onAction,
            enabled = availability.ratioEnabled,
            disabledReason = autoKneeReason,
            formatValue = { rawRatio ->
                multibandRatioText(
                    rawRatio = rawRatio,
                    limitText = ratioLimitText,
                    overText = { percent -> context.getString(R.string.editor_ratio_over, percent) },
                )
            },
        )
        MultibandIntKnob(
            label = stringResource(R.string.label_fet_knee),
            value = compressor.knees[band],
            range = 0..12,
            control = MultibandIntControl.KNEE,
            band = band,
            onAction = onAction,
            enabled = availability.kneeEnabled,
            disabledReason = autoKneeReason,
            formatValue = { "$it dB" },
        )
    }
    MultibandAutoSwitch(
        label = stringResource(R.string.label_fet_auto_gain),
        checked = compressor.gainAutos[band],
        control = MultibandBooleanControl.GAIN_AUTO,
        band = band,
        onAction = onAction,
    )
    KnobFlow {
        MultibandIntKnob(
            label = stringResource(R.string.label_gain),
            value = compressor.gains[band],
            range = 0..24,
            control = MultibandIntControl.GAIN,
            band = band,
            onAction = onAction,
            enabled = availability.gainEnabled,
            disabledReason = autoGainReason,
            formatValue = { "$it dB" },
        )
    }
    MultibandAutoSwitch(
        label = stringResource(R.string.label_fet_auto_attack),
        checked = compressor.attackAutos[band],
        control = MultibandBooleanControl.ATTACK_AUTO,
        band = band,
        onAction = onAction,
    )
    KnobFlow {
        MultibandIntKnob(
            label = stringResource(R.string.label_attack),
            value = compressor.attacks[band],
            range = 1..100,
            control = MultibandIntControl.ATTACK,
            band = band,
            onAction = onAction,
            enabled = availability.attackEnabled,
            disabledReason = autoAttackReason,
            formatValue = { "$it ms" },
        )
    }
    MultibandAutoSwitch(
        label = stringResource(R.string.label_fet_auto_release),
        checked = compressor.releaseAutos[band],
        control = MultibandBooleanControl.RELEASE_AUTO,
        band = band,
        onAction = onAction,
    )
    KnobFlow {
        MultibandIntKnob(
            label = stringResource(R.string.label_release),
            value = compressor.releases[band],
            range = 5..500,
            control = MultibandIntControl.RELEASE,
            band = band,
            onAction = onAction,
            enabled = availability.releaseEnabled,
            disabledReason = autoReleaseReason,
            formatValue = { "$it ms" },
        )
    }
}

@Composable
private fun MultibandIntKnob(
    label: String,
    value: Int,
    range: IntRange,
    control: MultibandIntControl,
    band: Int,
    onAction: (MultibandEditorAction) -> Unit,
    enabled: Boolean = true,
    disabledReason: String? = null,
    formatValue: (Int) -> String,
) {
    var pendingValue by remember(control, band) { mutableStateOf<Int?>(null) }
    VstKnob(
        label = label,
        value = value.toDouble(),
        valueRange = range.first.toDouble()..range.last.toDouble(),
        onValueChange = { updated ->
            val intValue = updated.roundToInt().coerceIn(range)
            pendingValue = intValue
            onAction(MultibandEditorAction.SetInt(control, band, intValue, last = false))
        },
        onValueChangeStarted = { onAction(MultibandEditorAction.BeginGesture) },
        onValueChangeFinished = {
            onAction(
                MultibandEditorAction.SetInt(
                    control = control,
                    band = band,
                    value = pendingValue ?: value,
                    last = true,
                ),
            )
            pendingValue = null
            onAction(MultibandEditorAction.SettleGesture)
            onAction(MultibandEditorAction.Flush)
        },
        formatValue = { formatValue(it.roundToInt().coerceIn(range)) },
        inputValue = { it.roundToInt().toString() },
        enabled = enabled,
        disabledReason = disabledReason,
    )
}

@Composable
private fun MultibandAutoSwitch(
    label: String,
    checked: Boolean,
    control: MultibandBooleanControl,
    band: Int,
    onAction: (MultibandEditorAction) -> Unit,
) {
    LabeledSwitch(
        label = label,
        checked = checked,
        onCheckedChange = { enabled ->
            onAction(MultibandEditorAction.BeginGesture)
            onAction(MultibandEditorAction.SetBoolean(control, band, enabled, last = true))
            onAction(MultibandEditorAction.SettleGesture)
            onAction(MultibandEditorAction.Flush)
        },
    )
}

@Composable
private fun AdvancedDynamicsControls(
    compressor: MultibandCompressorState,
    band: Int,
    availability: MultibandControlAvailability,
    onAction: (MultibandEditorAction) -> Unit,
) {
    val autoKneeReason = stringResource(R.string.editor_requires_auto_knee)
    val autoAttackReason = stringResource(R.string.editor_requires_auto_attack)
    val autoReleaseReason = stringResource(R.string.editor_requires_auto_release)
    val adaptReason = stringResource(R.string.editor_requires_auto_knee_or_gain)
    val autoGainReason = stringResource(R.string.editor_requires_auto_gain)
    KnobFlow {
        MultibandIntKnob(
            label = stringResource(R.string.label_fet_knee_multi),
            value = compressor.kneeMultis[band],
            range = 0..100,
            control = MultibandIntControl.KNEE_MULTI,
            band = band,
            onAction = onAction,
            enabled = availability.kneeMultiEnabled,
            disabledReason = autoKneeReason,
            formatValue = { "$it%" },
        )
        MultibandIntKnob(
            label = stringResource(R.string.label_fet_max_attack),
            value = compressor.maxAttacks[band],
            range = 1..100,
            control = MultibandIntControl.MAX_ATTACK,
            band = band,
            onAction = onAction,
            enabled = availability.maxAttackEnabled,
            disabledReason = autoAttackReason,
            formatValue = { "$it ms" },
        )
        MultibandIntKnob(
            label = stringResource(R.string.label_fet_max_release),
            value = compressor.maxReleases[band],
            range = 5..500,
            control = MultibandIntControl.MAX_RELEASE,
            band = band,
            onAction = onAction,
            enabled = availability.maxReleaseEnabled,
            disabledReason = autoReleaseReason,
            formatValue = { "$it ms" },
        )
        MultibandIntKnob(
            label = stringResource(R.string.label_fet_crest),
            value = compressor.crests[band],
            range = 5..300,
            control = MultibandIntControl.CREST,
            band = band,
            onAction = onAction,
            enabled = availability.crestEnabled,
            formatValue = { it.toString() },
        )
        MultibandIntKnob(
            label = stringResource(R.string.label_fet_adapt),
            value = compressor.adapts[band],
            range = 0..200,
            control = MultibandIntControl.ADAPT,
            band = band,
            onAction = onAction,
            enabled = availability.adaptEnabled,
            disabledReason = adaptReason,
            formatValue = { it.toString() },
        )
    }
    LabeledSwitch(
        label = stringResource(R.string.label_fet_no_clip),
        checked = compressor.noClips[band],
        enabled = availability.noClipEnabled,
        subtitle = autoGainReason.takeUnless { availability.noClipEnabled },
        onCheckedChange = { enabled ->
            onAction(MultibandEditorAction.BeginGesture)
            onAction(
                MultibandEditorAction.SetBoolean(
                    MultibandBooleanControl.NO_CLIP,
                    band,
                    enabled,
                    last = true,
                ),
            )
            onAction(MultibandEditorAction.SettleGesture)
            onAction(MultibandEditorAction.Flush)
        },
    )
}

@Composable
private fun KnobFlow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 3,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
private fun AutoStateText(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

private fun multibandRatioText(
    rawRatio: Int,
    limitText: String,
    overText: (Int) -> String,
): String =
    when (val label = multibandRatioLabel(rawRatio)) {
        is MultibandRatioLabel.Conventional -> {
            val rounded = label.ratio.roundToInt()
            if (abs(label.ratio - rounded) < 0.01) "$rounded:1" else "%.1f:1".format(label.ratio)
        }
        MultibandRatioLabel.Limit -> limitText
        is MultibandRatioLabel.Over -> overText(label.percent)
    }

private fun transferInputGrid(): List<GraphGridLine> =
    listOf(-60, -48, -36, -24, -12, 0).map { value ->
        GraphGridLine(
            position = linearValueToX(value.toDouble(), -60.0, 0.0),
            major = value % 24 == 0,
            label = "$value dB",
        )
    }

private fun transferOutputGrid(): List<GraphGridLine> =
    listOf(-60, -48, -36, -24, -12, 0, 12, 24).map { value ->
        GraphGridLine(
            position = linearValueToY(value.toDouble(), -60.0, 24.0),
            major = value % 24 == 0,
            label = "$value dB",
        )
    }

internal fun multibandBandColors(
    primary: Color,
    secondary: Color,
): List<Color> = List(5) { index -> lerp(primary, secondary, index / 4f) }

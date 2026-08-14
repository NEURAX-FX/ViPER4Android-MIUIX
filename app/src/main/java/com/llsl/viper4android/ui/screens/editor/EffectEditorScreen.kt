package com.llsl.viper4android.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llsl.viper4android.R
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.EqBandSpec
import com.llsl.viper4android.dsp.DynamicEqBandSpec
import com.llsl.viper4android.dsp.GRAPH_MIN_FREQUENCY
import com.llsl.viper4android.dsp.dynamicEqCurvePoints
import com.llsl.viper4android.dsp.firCurvePoints
import com.llsl.viper4android.dsp.graphDbToY
import com.llsl.viper4android.dsp.graphFrequencyToX
import com.llsl.viper4android.dsp.graphMaxFrequency
import com.llsl.viper4android.dsp.graphXToFrequency
import com.llsl.viper4android.dsp.graphYToDb
import com.llsl.viper4android.dsp.multibandCrossoverCurves
import com.llsl.viper4android.ui.components.viper.ViperDialog
import com.llsl.viper4android.ui.components.viper.ViperIconButton
import com.llsl.viper4android.ui.components.viper.ViperPowerButton
import com.llsl.viper4android.ui.components.viper.ViperScaffold
import com.llsl.viper4android.ui.components.viper.ViperTopBar
import com.llsl.viper4android.ui.components.viper.VstBandItem
import com.llsl.viper4android.ui.components.viper.VstBandStrip
import com.llsl.viper4android.ui.components.viper.VstControlGroup
import com.llsl.viper4android.ui.components.viper.VstGraphWorkspace
import com.llsl.viper4android.ui.components.viper.VstKnob
import com.llsl.viper4android.ui.components.viper.VstResponseGraph
import com.llsl.viper4android.ui.components.viper.GraphDragAxis
import com.llsl.viper4android.ui.components.viper.GraphGridLine
import com.llsl.viper4android.ui.components.viper.GraphHandle
import com.llsl.viper4android.ui.theme.ViperDesign
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun EffectEditorScreen(
    kind: EditorKind,
    viewModel: EffectEditorViewModel,
    onBack: () -> Unit,
) {
    if (kind == EditorKind.IEM) {
        IemEditorScreen(viewModel = viewModel, onBack = onBack)
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connected by viewModel.isServiceConnected.collectAsStateWithLifecycle()
    val undoCount by viewModel.undoCount.collectAsStateWithLifecycle()
    val redoCount by viewModel.redoCount.collectAsStateWithLifecycle()
    val telemetry by viewModel.driverTelemetry.collectAsStateWithLifecycle()
    var showReset by remember { mutableStateOf(false) }

    LaunchedEffect(kind, connected) {
        if (kind != EditorKind.MULTIBAND_COMPRESSOR || !connected) {
            viewModel.clearDriverTelemetry()
            return@LaunchedEffect
        }
        while (isActive) {
            viewModel.refreshDriverTelemetry()
            delay(50L)
        }
    }

    ViperScaffold(
        topBar = {
            ViperTopBar(
                title = editorTitle(kind),
                largeTitle = editorTitle(kind),
                compact = true,
                navigationIcon = {
                    ViperIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back),
                        )
                    }
                },
                actions = {
                    if (kind != EditorKind.IEM) {
                        ViperIconButton(onClick = { showReset = true }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = stringResource(R.string.editor_reset))
                        }
                        ViperIconButton(enabled = undoCount > 0, onClick = viewModel::undo) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.editor_undo))
                        }
                        ViperIconButton(enabled = redoCount > 0, onClick = viewModel::redo) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.editor_redo))
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        if (kind == EditorKind.MULTIBAND_COMPRESSOR) {
            val sampleRate by viewModel.graphSampleRate.collectAsStateWithLifecycle()
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(ViperDesign.sectionSpacing),
            ) {
                EditorStatusRow(
                    kind = kind,
                    enabled = isEnabled(kind, state),
                    connected = connected,
                    onEnabledChange = { viewModel.setEnabled(kind, it) },
                )
                MultibandCompressorEditor(
                    state = state,
                    sampleRate = sampleRate,
                    telemetry = telemetry,
                    onAction = viewModel::handleMultibandEditorAction,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ViperDesign.sectionSpacing),
            ) {
                EditorStatusRow(
                    kind = kind,
                    enabled = isEnabled(kind, state),
                    connected = connected,
                    onEnabledChange = { viewModel.setEnabled(kind, it) },
                )
                when (kind) {
                    EditorKind.FIR_EQUALIZER -> FirEqualizerEditor(state, viewModel)
                    EditorKind.DYNAMIC_EQUALIZER -> DynamicEqualizerEditor(state, viewModel)
                    EditorKind.MULTIBAND_COMPRESSOR -> Unit
                    EditorKind.IEM -> Unit
                }
            }
        }
    }

    if (showReset) {
        ViperDialog(
            show = true,
            onDismissRequest = { showReset = false },
            title = stringResource(R.string.editor_reset_title),
            summary = stringResource(R.string.editor_reset_message),
            content = {},
            confirmText = stringResource(R.string.editor_reset),
            onConfirm = {
                viewModel.reset(kind)
                showReset = false
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showReset = false },
        )
    }
}

@Composable
private fun EditorStatusRow(
    kind: EditorKind,
    enabled: Boolean,
    connected: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (connected) stringResource(R.string.editor_live) else stringResource(R.string.editor_offline),
                color = if (connected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline,
                style = MiuixTheme.textStyles.body2,
            )
            Text(
                text = stringResource(R.string.editor_bypass),
                color = MiuixTheme.colorScheme.outline,
                style = MiuixTheme.textStyles.body2,
            )
        }
        ViperPowerButton(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            contentDescription = stringResource(R.string.editor_bypass),
        )
    }
}

@Composable
private fun FirEqualizerEditor(
    state: EffectState,
    viewModel: EffectEditorViewModel,
) {
    var selectedBand by remember { mutableIntStateOf(0) }
    val sampleRate by viewModel.graphSampleRate.collectAsStateWithLifecycle()
    val bands = state.eq.bands.take(state.eq.bandCount).ifEmpty { listOf(0.0) }
    val frequencies = EqBandSpec.frequenciesFor(state.eq.bandCount)
    val labels = EqBandSpec.labelsFor(state.eq.bandCount)
    val band = selectedBand.coerceIn(bands.indices)
    val accent = MiuixTheme.colorScheme.primary
    val muted = MiuixTheme.colorScheme.secondary
    val handles = bands.mapIndexed { index, gain ->
        GraphHandle(
            id = index.toString(),
            x = graphFrequencyToX(frequencies.getOrElse(index) { frequencies.last() }, sampleRate),
            y = graphDbToY(gain, -12.0, 12.0),
            color = if (index == band) accent else muted,
            label = "${labels.getOrElse(index) { "" }} Hz",
            dragAxis = GraphDragAxis.VERTICAL,
            valueDescription = "%.1f dB".format(gain),
        )
    }
    // Sampling the driver's own parallel-IIR bank is the only way the preview matches the
    // audio; the coefficients depend on the live sample rate.
    val curve = remember(bands, state.eq.bandCount, sampleRate) {
        firCurvePoints(
            bandCount = state.eq.bandCount,
            gainsDb = bands,
            sampleRate = sampleRate,
            minDb = -12.0,
            maxDb = 12.0,
        )
    }
    EditorBody(
        graph = {
            VstResponseGraph(
                handles = handles,
                curve = curve,
                selectedHandleId = band.toString(),
                verticalGridLines = editorFrequencyGrid(sampleRate),
                horizontalGridLines = editorDecibelGrid(-12.0, 12.0, 6.0),
                showGridLabels = true,
                contentDescription = stringResource(R.string.editor_graph_fir),
                onHandleSelected = { selectedBand = it.toIntOrNull() ?: 0 },
                onHandleDragStart = { viewModel.beginGesture() },
                onHandleDrag = { id, _, y ->
                    val index = id.toIntOrNull() ?: return@VstResponseGraph
                    viewModel.updateFirBand(index, graphYToDb(y, -12.0, 12.0), last = false)
                },
                onHandleDragEnd = {
                    viewModel.flush()
                    viewModel.settleGesture()
                },
            )
        },
    ) {
        VstBandStrip(
            items = bands.mapIndexed { index, gain ->
                VstBandItem(
                    id = index.toString(),
                    title = "${labels.getOrElse(index) { "" }} Hz",
                    value = "%.1f dB".format(gain),
                )
            },
            selectedIndex = band,
            onSelected = { selectedBand = it },
        )
        VstControlGroup(title = "${labels.getOrElse(band) { "" }} Hz") {
            ParameterGrid {
                VstKnob(
                    label = "GAIN",
                    value = bands[band],
                    valueRange = -12.0..12.0,
                    onValueChange = { viewModel.updateFirBand(band, it) },
                    onValueChangeFinished = { viewModel.flush() },
                    formatValue = { String.format("%.1f dB", it) },
                )
            }
        }
    }
}

@Composable
private fun DynamicEqualizerEditor(
    state: EffectState,
    viewModel: EffectEditorViewModel,
) {
    var selectedBand by remember { mutableIntStateOf(0) }
    val sampleRate by viewModel.graphSampleRate.collectAsStateWithLifecycle()
    val dynamic = state.dynamicEq
    // A malformed persisted state can leave the band lists empty while bandCount claims
    // otherwise, so normalize once here instead of indexing raw lists.
    val count = min(dynamic.bandCount, min(dynamic.freqs.size, dynamic.gains.size)).coerceAtLeast(1)
    val index = selectedBand.coerceIn(0, count - 1)
    val specs = (0 until count).map { band ->
        DynamicEqBandSpec(
            frequency = dynamic.freqs.getOrElse(band) { 1000 }.toDouble(),
            gainDb = dynamic.gains.getOrElse(band) { 0 } / 10.0,
            q = dynamic.qs.getOrElse(band) { 100 } / 100.0,
            filterType = dynamic.filterTypes.getOrElse(band) { 0 },
        )
    }
    val handles = specs.mapIndexed { band, spec ->
        GraphHandle(
            id = band.toString(),
            x = graphFrequencyToX(spec.frequency, sampleRate),
            y = graphDbToY(spec.gainDb, -12.0, 12.0),
            color = if (band == index) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondary,
            label = "Band ${band + 1}",
            valueDescription = "%.0f Hz, %.1f dB".format(spec.frequency, spec.gainDb),
        )
    }
    // Driver chains one biquad per band, honouring each band's filter type. This is the
    // target response; the live gain also depends on the envelope follower.
    val curve = remember(specs, sampleRate) {
        dynamicEqCurvePoints(
            bands = specs,
            sampleRate = sampleRate,
            minDb = -12.0,
            maxDb = 12.0,
        )
    }
    EditorBody(
        graph = {
            VstResponseGraph(
                handles = handles,
                curve = curve,
                selectedHandleId = index.toString(),
                verticalGridLines = editorFrequencyGrid(sampleRate),
                horizontalGridLines = editorDecibelGrid(-12.0, 12.0, 6.0),
                showGridLabels = true,
                contentDescription = stringResource(R.string.editor_graph_dynamic_eq),
                onHandleSelected = { selectedBand = it.toIntOrNull() ?: 0 },
                onHandleDragStart = { viewModel.beginGesture() },
                onHandleDrag = { id, x, y ->
                    val band = id.toIntOrNull() ?: return@VstResponseGraph
                    viewModel.updateDynamicFrequency(
                        band,
                        graphXToFrequency(x, sampleRate).toInt(),
                        last = false,
                    )
                    viewModel.updateDynamicGain(
                        band,
                        (graphYToDb(y, -12.0, 12.0) * 10).toInt(),
                        last = false,
                    )
                },
                onHandleDragEnd = {
                    viewModel.flush()
                    viewModel.settleGesture()
                },
            )
        },
    ) {
        VstBandStrip(
            items = specs.mapIndexed { band, spec ->
                VstBandItem(
                    id = band.toString(),
                    title = "%.0f Hz".format(spec.frequency),
                    value = "%.1f dB".format(spec.gainDb),
                )
            },
            selectedIndex = index,
            onSelected = { selectedBand = it },
        )
        VstControlGroup(title = "Band ${index + 1}") {
            ParameterGrid {
                VstKnob("FREQ", specs[index].frequency, GRAPH_MIN_FREQUENCY..graphMaxFrequency(sampleRate), { viewModel.updateDynamicFrequency(index, it.toInt()) }, { viewModel.flush() }, { "${it.toInt()} Hz" })
                VstKnob("Q", specs[index].q, 0.5..8.0, { viewModel.updateDynamicQ(index, (it * 100).toInt()) }, formatValue = { "%.2f".format(it) })
                VstKnob("GAIN", specs[index].gainDb, -12.0..12.0, { viewModel.updateDynamicGain(index, (it * 10).toInt()) }, { viewModel.flush() }, { "%.1f dB".format(it) })
                VstKnob("THRESH", dynamic.thresholds.getOrElse(index) { 0 } / 10.0, -80.0..0.0, { viewModel.updateDynamicThreshold(index, (it * 10).toInt()) }, formatValue = { "%.1f dB".format(it) })
                VstKnob("ATTACK", dynamic.attacks.getOrElse(index) { 1 }.toDouble(), 1.0..100.0, { viewModel.updateDynamicAttack(index, it.toInt()) }, formatValue = { "${it.toInt()} ms" })
                VstKnob("RELEASE", dynamic.releases.getOrElse(index) { 100 }.toDouble(), 10.0..500.0, { viewModel.updateDynamicRelease(index, it.toInt()) }, formatValue = { "${it.toInt()} ms" })
            }
        }
    }
}

@Composable
private fun EditorBody(
    graph: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    VstGraphWorkspace(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        graph = { graph() },
    ) {
        content()
    }
}

@Composable
private fun ParameterGrid(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 3,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

private fun editorTitle(kind: EditorKind): String =
    when (kind) {
        EditorKind.FIR_EQUALIZER -> "FIR Equalizer"
        EditorKind.DYNAMIC_EQUALIZER -> "Dynamic EQ"
        EditorKind.MULTIBAND_COMPRESSOR -> "Multiband Compressor"
        EditorKind.IEM -> "IEM Spatial Audio"
    }

private fun isEnabled(kind: EditorKind, state: EffectState): Boolean =
    when (kind) {
        EditorKind.FIR_EQUALIZER -> state.eq.enable
        EditorKind.DYNAMIC_EQUALIZER -> state.dynamicEq.enable
        EditorKind.MULTIBAND_COMPRESSOR -> state.multibandCompressor.enable
        EditorKind.IEM -> state.iem.general.enable
    }

internal fun editorFrequencyGrid(sampleRate: Int): List<GraphGridLine> =
    frequencyGridLines(GRAPH_MIN_FREQUENCY, graphMaxFrequency(sampleRate)).map { line ->
        GraphGridLine(
            position = line.position,
            major = line.major,
            // Label the LSP-style markers (1, 2, 3, 5 per decade) so the axis stays
            // readable instead of labelling every minor subdivision.
            label = line.label.takeIf { isLabeledFrequency(line.frequency) },
        )
    }

private fun isLabeledFrequency(frequency: Double): Boolean {
    val decade = 10.0.pow(floor(log10(frequency)))
    val multiple = (frequency / decade).roundToInt()
    return multiple == 1 || multiple == 2 || multiple == 3 || multiple == 5
}

internal fun editorDecibelGrid(
    minDb: Double,
    maxDb: Double,
    step: Double,
): List<GraphGridLine> =
    decibelGridLines(minDb, maxDb, step).map {
        GraphGridLine(position = it.position, major = it.major, label = it.label)
    }

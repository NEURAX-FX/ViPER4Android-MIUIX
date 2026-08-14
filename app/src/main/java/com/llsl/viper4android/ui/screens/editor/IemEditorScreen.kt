package com.llsl.viper4android.ui.screens.editor

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llsl.viper4android.R
import com.llsl.viper4android.effect.BoolPref
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.Effects
import com.llsl.viper4android.effect.IntPref
import com.llsl.viper4android.ui.components.LabeledDropdown
import com.llsl.viper4android.ui.components.LabeledSlider
import com.llsl.viper4android.ui.components.LabeledSwitch
import com.llsl.viper4android.ui.components.SliderEdit
import com.llsl.viper4android.ui.components.viper.ViperIconButton
import com.llsl.viper4android.ui.components.viper.ViperPowerButton
import com.llsl.viper4android.ui.components.viper.ViperScaffold
import com.llsl.viper4android.ui.components.viper.ViperTabs
import com.llsl.viper4android.ui.components.viper.ViperTopBar
import com.llsl.viper4android.viper.IemDriverTelemetry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

const val IEM_PROJECT_URL = "https://plugins.iem.at"

data class HeadphoneEqOption(val id: Int, val label: String)

fun iemEditorTabs(): List<String> = listOf("encoder", "rotation", "decoder", "output")
fun iemGranularSections(): List<String> = listOf("spatial", "timing", "pitch", "window", "mix")
fun iemRenderModes(): List<String> = listOf("off", "simple", "ku100")
fun shouldEnableHeadphoneEq(renderMode: Int): Boolean = renderMode == 2
fun iemHaloControls(): List<String> = listOf(
    "dialogIsolate", "dialogAggress", "dialogAttack", "dialogRelease", "dialogMixIn",
    "divergence", "fade", "fadeRears", "diffusion", "space", "backBoost",
    "rearShelfEnable", "rearShelfFreq", "rearShelfGain", "lfeEnable", "lfeFrequency",
    "lfeSplit", "lfeGain",
)
fun iemTelemetryKeys(): List<String> = listOf("latency", "activeGrains", "queueFaults", "limiterReduction", "fault", "preparation")
fun haloLfeCutoffHz(normalizedMillionths: Int): Double {
    val normalized = normalizedMillionths.coerceIn(0, 1_000_000) / 1_000_000.0
    return exp(ln(10.0) + normalized * (ln(200.0) - ln(10.0)))
}
fun haloLfeFrequencyMillionths(frequencyHz: Double): Int {
    val frequency = frequencyHz.coerceIn(10.0, 200.0)
    return (((ln(frequency) - ln(10.0)) / (ln(200.0) - ln(10.0))) * 1_000_000.0)
        .roundToInt()
        .coerceIn(0, 1_000_000)
}
fun haloLfeGainDb(normalizedMillionths: Int): Double =
    55.0 * normalizedMillionths.coerceIn(0, 1_000_000) / 1_000_000.0 - 45.0
fun haloLfeGainMillionths(gainDb: Double): Int =
    (((gainDb.coerceIn(-45.0, 10.0) + 45.0) / 55.0) * 1_000_000.0)
        .roundToInt()
        .coerceIn(0, 1_000_000)

fun headphoneEqOptions(): List<HeadphoneEqOption> =
    listOf(
        "AKG K1000 Closed", "AKG K1000 Open", "AKG K141 MK2", "AKG K240 DF",
        "AKG K240 MK2", "AKG K271 MK2", "AKG K271 Studio", "AKG K601", "AKG K701",
        "AKG K702", "Audio-Technica ATH-M50", "Beyerdynamic DT250",
        "Beyerdynamic DT770 Pro 250 Ohms", "Beyerdynamic DT880", "Beyerdynamic DT990 Pro",
        "Presonus HD7", "Sennheiser HD430", "Sennheiser HD480", "Sennheiser HD560 Ovation II",
        "Sennheiser HD565 Ovation", "Sennheiser HD600", "Sennheiser HD650", "Shure SRH940",
    ).mapIndexed { index, label -> HeadphoneEqOption(index, label) }

@Composable
fun IemEditorScreen(viewModel: EffectEditorViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connected by viewModel.isServiceConnected.collectAsStateWithLifecycle()
    val telemetry by viewModel.iemTelemetry.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(connected) {
        if (!connected) {
            viewModel.clearIemTelemetry()
            return@LaunchedEffect
        }
        while (isActive) {
            viewModel.refreshIemTelemetry()
            delay(250L)
        }
    }

    ViperScaffold(
        topBar = {
            ViperTopBar(
                title = stringResource(R.string.section_iem),
                largeTitle = stringResource(R.string.section_iem),
                navigationIcon = {
                    ViperIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.editor_back))
                    }
                },
                actions = {
                    ViperPowerButton(
                        checked = state.iem.general.enable,
                        onCheckedChange = { viewModel.setEnabled(EditorKind.IEM, it) },
                        contentDescription = stringResource(R.string.editor_bypass),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ViperTabs(
                tabs = listOf(
                    stringResource(R.string.iem_tab_encoder), stringResource(R.string.iem_tab_rotation),
                    stringResource(R.string.iem_tab_decoder), stringResource(R.string.iem_tab_output),
                ),
                selectedTabIndex = tab,
                onTabSelected = { tab = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (tab) {
                    0 -> EncoderTab(state, viewModel)
                    1 -> RotationTab(state, viewModel)
                    2 -> DecoderTab(state, viewModel)
                    else -> OutputTab(state, telemetry, viewModel)
                }
                IemAttributionFooter()
            }
        }
    }
}

@Composable
private fun EncoderTab(state: EffectState, viewModel: EffectEditorViewModel) {
    val modes = listOf(
        stringResource(R.string.iem_mode_stereo),
        stringResource(R.string.iem_mode_multi),
        stringResource(R.string.iem_mode_granular),
        stringResource(R.string.iem_mode_halo),
    )
    LabeledDropdown(
        label = stringResource(R.string.iem_encoder_mode),
        selectedValue = modes[state.iem.general.encoderMode],
        options = modes,
        onOptionSelected = { index, _ -> viewModel.updateIemInt(Effects.iem.encoderMode, index) },
    )
    when (state.iem.general.encoderMode) {
        0 -> StereoControls(state, viewModel)
        1 -> MultiControls(state, viewModel)
        2 -> GranularControls(state, viewModel)
        else -> HaloControls(state, viewModel)
    }
}

@Composable
private fun HaloControls(state: EffectState, vm: EffectEditorViewModel) {
    val h = state.iem.halo
    IemSlider(stringResource(R.string.iem_editor_halo_dialog_isolate), h.dialogIsolateThousandths, 0..1000, 10.0, "%", Effects.iem.haloDialogIsolate, vm)
    IemSlider(stringResource(R.string.iem_editor_halo_dialog_aggress), h.dialogAggressThousandths, 0..1000, 10.0, "%", Effects.iem.haloDialogAggress, vm)
    IemSlider(stringResource(R.string.iem_editor_halo_dialog_attack), h.dialogAttackThousandths, 0..1000, 10.0, "%", Effects.iem.haloDialogAttack, vm)
    IemSlider(stringResource(R.string.iem_editor_halo_dialog_release), h.dialogReleaseThousandths, 0..1000, 10.0, "%", Effects.iem.haloDialogRelease, vm)
    IemSlider(stringResource(R.string.iem_editor_halo_dialog_mix_in), h.dialogMixInThousandths, 0..1000, 10.0, "%", Effects.iem.haloDialogMixIn, vm)
    IemSlider(stringResource(R.string.iem_editor_halo_divergence), h.divergenceThousandths, 0..1000, 10.0, "%", Effects.iem.haloDivergence, vm)
    IemSlider(stringResource(R.string.iem_editor_halo_fade), h.fadeThousandths, 0..1000, 10.0, "%", Effects.iem.haloFade, vm)
    IemSlider(stringResource(R.string.iem_editor_halo_fade_rears), h.fadeRearsThousandths, 0..1000, 10.0, "%", Effects.iem.haloFadeRears, vm)
    IemSlider(stringResource(R.string.iem_editor_halo_diffusion), h.diffusionThousandths, 0..1000, 10.0, "%", Effects.iem.haloDiffusion, vm)
    IemSlider(stringResource(R.string.iem_editor_halo_space), h.spaceThousandths, 0..1000, 10.0, "%", Effects.iem.haloSpace, vm)
    IemSwitch(stringResource(R.string.iem_editor_halo_back_boost), h.backBoost, Effects.iem.haloBackBoost, vm)
    IemSwitch(stringResource(R.string.iem_editor_halo_rear_shelf_enable), h.rearShelfEnable, Effects.iem.haloRearShelfEnable, vm)
    IemSlider(stringResource(R.string.iem_editor_halo_rear_shelf_freq), h.rearShelfFreqThousandths, 0..1000, 10.0, "%", Effects.iem.haloRearShelfFreq, vm)
    IemSlider(stringResource(R.string.iem_editor_halo_rear_shelf_gain), h.rearShelfGainThousandths, 0..1000, 10.0, "%", Effects.iem.haloRearShelfGain, vm)
    SectionTitle(stringResource(R.string.iem_editor_halo_lfe_section))
    IemSwitch(stringResource(R.string.iem_editor_halo_lfe_enable), h.lfeEnabled, Effects.iem.haloLfeEnable, vm)
    IemMappedSlider(
        label = stringResource(R.string.iem_editor_halo_lfe_frequency),
        value = h.lfeFrequencyMillionths,
        displayValue = haloLfeCutoffHz(h.lfeFrequencyMillionths),
        displayRange = 10.0..200.0,
        decimals = 1,
        unit = "Hz",
        enabled = h.lfeEnabled,
        onValueChange = { vm.updateIemInt(Effects.iem.haloLfeFrequency, it) },
        onDisplayCommit = { haloLfeFrequencyMillionths(it) },
    )
    IemSlider(
        stringResource(R.string.iem_editor_halo_lfe_split),
        h.lfeSplitMillionths,
        0..1_000_000,
        10_000.0,
        "%",
        Effects.iem.haloLfeSplit,
        vm,
        decimals = 1,
        enabled = h.lfeEnabled,
    )
    IemMappedSlider(
        label = stringResource(R.string.iem_editor_halo_lfe_gain),
        value = h.lfeGainMillionths,
        displayValue = haloLfeGainDb(h.lfeGainMillionths),
        displayRange = -45.0..10.0,
        decimals = 1,
        unit = "dB",
        enabled = h.lfeEnabled,
        onValueChange = { vm.updateIemInt(Effects.iem.haloLfeGain, it) },
        onDisplayCommit = { haloLfeGainMillionths(it) },
    )
}

@Composable
private fun StereoControls(state: EffectState, vm: EffectEditorViewModel) {
    IemSlider(stringResource(R.string.iem_editor_azimuth), state.iem.stereo.azimuthCentidegrees, -18000..18000, 100.0, "°", Effects.iem.stereoAzimuth, vm)
    IemSlider(stringResource(R.string.iem_editor_elevation), state.iem.stereo.elevationCentidegrees, -18000..18000, 100.0, "°", Effects.iem.stereoElevation, vm)
    IemSlider(stringResource(R.string.iem_editor_roll), state.iem.stereo.rollCentidegrees, -18000..18000, 100.0, "°", Effects.iem.stereoRoll, vm)
    IemSlider(stringResource(R.string.iem_editor_width), state.iem.stereo.widthCentidegrees, -36000..36000, 100.0, "°", Effects.iem.stereoWidth, vm)
    IemSwitch(stringResource(R.string.iem_editor_sample_wise_panning), state.iem.stereo.sampleWise, Effects.iem.stereoSampleWise, vm)
}

@Composable
private fun MultiControls(state: EffectState, vm: EffectEditorViewModel) {
    var source by remember { mutableIntStateOf(0) }
    ViperTabs(listOf(stringResource(R.string.iem_editor_left), stringResource(R.string.iem_editor_right)), source, { source = it }, Modifier.fillMaxWidth())
    fun update(pref: com.llsl.viper4android.effect.IntListPref, value: Int) = vm.updateIemMultiSource(pref, source, value)
    IemRawSlider(stringResource(R.string.iem_editor_azimuth), state.iem.multi.azimuthCentidegrees[source], -18000..18000, 100.0, "°") { update(Effects.iem.multiAzimuth, it) }
    IemRawSlider(stringResource(R.string.iem_editor_elevation), state.iem.multi.elevationCentidegrees[source], -18000..18000, 100.0, "°") { update(Effects.iem.multiElevation, it) }
    IemRawSlider(stringResource(R.string.iem_editor_gain), state.iem.multi.gainDecidb[source], -600..100, 10.0, "dB") { update(Effects.iem.multiGain, it) }
    LabeledSwitch(stringResource(R.string.iem_editor_mute), state.iem.multi.mute[source], { vm.updateIemMultiSource(Effects.iem.multiMute, source, it) })
}

@Composable
private fun GranularControls(state: EffectState, vm: EffectEditorViewModel) {
    val g = state.iem.granular
    SectionTitle(stringResource(R.string.iem_editor_spatial))
    IemSlider(stringResource(R.string.iem_editor_azimuth), g.azimuthCentidegrees, -18000..18000, 100.0, "°", Effects.iem.granularAzimuth, vm)
    IemSlider(stringResource(R.string.iem_editor_elevation), g.elevationCentidegrees, -18000..18000, 100.0, "°", Effects.iem.granularElevation, vm)
    IemSlider(stringResource(R.string.iem_editor_shape), g.shapeTenths, -100..100, 10.0, "", Effects.iem.granularShape, vm)
    IemSlider(stringResource(R.string.iem_editor_size), g.sizeCentidegrees, 0..36000, 100.0, "°", Effects.iem.granularSize, vm)
    IemSlider(stringResource(R.string.iem_editor_roll), g.rollCentidegrees, -18000..18000, 100.0, "°", Effects.iem.granularRoll, vm)
    IemSlider(stringResource(R.string.iem_editor_width), g.widthCentidegrees, -36000..36000, 100.0, "°", Effects.iem.granularWidth, vm)
    LabeledDropdown(
        label = stringResource(R.string.iem_editor_spatial_mode),
        selectedValue = if (g.spatialMode == 0) stringResource(R.string.iem_editor_mode_3d) else stringResource(R.string.iem_editor_mode_2d),
        options = listOf(stringResource(R.string.iem_editor_mode_3d), stringResource(R.string.iem_editor_mode_2d)),
        onOptionSelected = { i, _ -> vm.updateIemInt(Effects.iem.granularSpatialMode, i) },
    )
    IemSwitch(stringResource(R.string.iem_editor_sample_wise_panning), g.sampleWise, Effects.iem.granularSampleWise, vm)
    SectionTitle(stringResource(R.string.iem_editor_timing))
    IemSlider(stringResource(R.string.iem_editor_delta_time), g.deltaTimeUs, 1000..2000000, 1_000_000.0, "s", Effects.iem.granularDeltaTime, vm, 3)
    IemSlider(stringResource(R.string.iem_editor_delta_time_modulation), g.deltaTimeModTenthsPercent, 0..1000, 10.0, "%", Effects.iem.granularDeltaTimeMod, vm)
    IemSlider(stringResource(R.string.iem_editor_grain_length), g.grainLengthUs, 1000..2000000, 1_000_000.0, "s", Effects.iem.granularGrainLength, vm, 3)
    IemSlider(stringResource(R.string.iem_editor_grain_length_modulation), g.grainLengthModTenthsPercent, 0..1000, 10.0, "%", Effects.iem.granularGrainLengthMod, vm)
    IemSlider(stringResource(R.string.iem_editor_read_position), g.readPositionUs, 0..4000000, 1_000_000.0, "s", Effects.iem.granularReadPosition, vm, 3)
    IemSlider(stringResource(R.string.iem_editor_position_modulation), g.positionModUs, 0..4000000, 1_000_000.0, "s", Effects.iem.granularPositionMod, vm, 3)
    SectionTitle(stringResource(R.string.iem_editor_pitch))
    IemSlider(stringResource(R.string.iem_editor_pitch), g.pitchMilliSemitones, -12000..12000, 1000.0, "st", Effects.iem.granularPitch, vm, 3)
    IemSlider(stringResource(R.string.iem_editor_pitch_modulation), g.pitchModMilliSemitones, 0..12000, 1000.0, "st", Effects.iem.granularPitchMod, vm, 3)
    SectionTitle(stringResource(R.string.iem_editor_window))
    IemSlider(stringResource(R.string.iem_editor_attack), g.attackTenthsPercent, 0..500, 10.0, "%", Effects.iem.granularAttack, vm)
    IemSlider(stringResource(R.string.iem_editor_attack_modulation), g.attackModTenthsPercent, 0..1000, 10.0, "%", Effects.iem.granularAttackMod, vm)
    IemSlider(stringResource(R.string.iem_editor_decay), g.decayTenthsPercent, 0..500, 10.0, "%", Effects.iem.granularDecay, vm)
    IemSlider(stringResource(R.string.iem_editor_decay_modulation), g.decayModTenthsPercent, 0..1000, 10.0, "%", Effects.iem.granularDecayMod, vm)
    SectionTitle(stringResource(R.string.iem_editor_mix))
    IemSlider(stringResource(R.string.iem_editor_mix), g.mixTenthsPercent, 0..1000, 10.0, "%", Effects.iem.granularMix, vm)
    IemSlider(stringResource(R.string.iem_editor_source_probability), g.sourceProbabilityHundredths, -100..100, 100.0, "", Effects.iem.granularSourceProbability, vm)
    LabeledSwitch(stringResource(R.string.iem_editor_freeze), state.iem.freeze, vm::setIemFreeze, subtitle = stringResource(R.string.iem_editor_runtime_only))
}

@Composable
private fun RotationTab(state: EffectState, vm: EffectEditorViewModel) {
    val r = state.iem.rotation
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SectionTitle(stringResource(R.string.iem_editor_rotation))
        ViperIconButton(onClick = vm::resetIemRotation) { Icon(Icons.Default.RestartAlt, stringResource(R.string.editor_reset)) }
    }
    IemSlider(stringResource(R.string.iem_editor_yaw), r.yawCentidegrees, -18000..18000, 100.0, "°", Effects.iem.rotationYaw, vm)
    IemSlider(stringResource(R.string.iem_editor_pitch), r.pitchCentidegrees, -18000..18000, 100.0, "°", Effects.iem.rotationPitch, vm)
    IemSlider(stringResource(R.string.iem_editor_roll), r.rollCentidegrees, -18000..18000, 100.0, "°", Effects.iem.rotationRoll, vm)
    SectionTitle(stringResource(R.string.iem_editor_advanced))
    IemSwitch(stringResource(R.string.iem_editor_invert_yaw), r.invertYaw, Effects.iem.invertYaw, vm)
    IemSwitch(stringResource(R.string.iem_editor_invert_pitch), r.invertPitch, Effects.iem.invertPitch, vm)
    IemSwitch(stringResource(R.string.iem_editor_invert_roll), r.invertRoll, Effects.iem.invertRoll, vm)
    IemSwitch(stringResource(R.string.iem_editor_invert_overall_rotation), r.invertOverall, Effects.iem.invertOverall, vm)
    val sequences = listOf(stringResource(R.string.iem_editor_sequence_ypr), stringResource(R.string.iem_editor_sequence_rpy))
    LabeledDropdown(
        label = stringResource(R.string.iem_editor_sequence),
        selectedValue = sequences[r.sequence],
        options = sequences,
        onOptionSelected = { i, _ -> vm.updateIemInt(Effects.iem.rotationSequence, i) },
    )
}

@Composable
private fun DecoderTab(state: EffectState, vm: EffectEditorViewModel) {
    val renderModes = listOf(
        stringResource(R.string.iem_editor_render_off),
        stringResource(R.string.iem_editor_render_simple),
        stringResource(R.string.iem_editor_render_ku100),
    )
    ViperTabs(
        tabs = renderModes,
        selectedTabIndex = state.iem.general.renderMode,
        onTabSelected = { vm.updateIemInt(Effects.iem.renderMode, it) },
        modifier = Modifier.fillMaxWidth(),
    )
    SectionTitle(stringResource(R.string.iem_editor_decoder))
    ReadOnlyRow(
        stringResource(R.string.iem_editor_decoder),
        renderModes[state.iem.general.renderMode],
    )
    ReadOnlyRow(stringResource(R.string.iem_editor_effective_order), state.iem.general.order.toString())
    val options = listOf(HeadphoneEqOption(-1, stringResource(R.string.iem_editor_off))) + headphoneEqOptions()
    val selected = options.first { it.id == state.iem.decoder.headphoneEq }
    LabeledDropdown(
        label = stringResource(R.string.iem_editor_headphone_eq),
        selectedValue = selected.label,
        options = options.map { it.label },
        onOptionSelected = { index, _ -> vm.updateIemInt(Effects.iem.headphoneEq, options[index].id) },
        enabled = shouldEnableHeadphoneEq(state.iem.general.renderMode),
    )
}

@Composable
private fun OutputTab(state: EffectState, telemetry: IemDriverTelemetry?, vm: EffectEditorViewModel) {
    val o = state.iem.output
    IemSlider(stringResource(R.string.iem_wet), o.wetPercent, 0..100, 1.0, "%", Effects.iem.wet, vm, 0)
    IemSlider(stringResource(R.string.iem_editor_output_gain), o.gainDecidb, -240..240, 10.0, "dB", Effects.iem.outputGain, vm)
    val profiles = listOf(stringResource(R.string.iem_editor_latency_low), stringResource(R.string.iem_editor_latency_balanced), stringResource(R.string.iem_editor_latency_stable))
    LabeledDropdown(
        label = stringResource(R.string.iem_editor_latency_profile),
        selectedValue = profiles[o.latencyProfile],
        options = profiles,
        onOptionSelected = { i, _ -> vm.updateIemInt(Effects.iem.latencyProfile, i) },
    )
    IemSwitch(stringResource(R.string.iem_editor_limiter), o.limiterEnabled, Effects.iem.limiterEnable, vm)
    IemSlider(stringResource(R.string.iem_editor_ceiling), o.limiterCeilingCentidb, -1200..0, 100.0, "dBFS", Effects.iem.limiterCeiling, vm, 2)
    SectionTitle(stringResource(R.string.iem_editor_diagnostics))
    ReadOnlyRow(stringResource(R.string.iem_editor_actual_latency), telemetry?.let { stringResource(R.string.iem_editor_latency_value, it.latencyFrames, format(it.latencyMs.toDouble(), 2)) } ?: "—")
    ReadOnlyRow(stringResource(R.string.iem_editor_active_grains), telemetry?.activeGrains?.toString() ?: "—")
    ReadOnlyRow(stringResource(R.string.iem_editor_queue_faults), telemetry?.let { "${it.outputUnderflows} / ${it.inputOverflows + it.outputOverflows}" } ?: "—")
    ReadOnlyRow(stringResource(R.string.iem_editor_grain_exhaustion), telemetry?.grainPoolExhaustions?.toString() ?: "—")
    ReadOnlyRow(stringResource(R.string.iem_editor_limiter_reduction), telemetry?.let { "${format(it.limiterGainReductionDb.toDouble(), 2)} dB" } ?: "—")
    ReadOnlyRow(stringResource(R.string.iem_editor_fault_preparation), telemetry?.let { "${it.faultCode} / ${it.preparationResult}" } ?: "—")
    ReadOnlyRow(stringResource(R.string.iem_editor_render_mode), telemetry?.renderMode?.toString() ?: "—")
    ReadOnlyRow(stringResource(R.string.iem_editor_dialog_net), telemetry?.dialogNetResult?.toString() ?: "—")
}

@Composable private fun IemSlider(label: String, value: Int, range: IntRange, scale: Double, unit: String, pref: IntPref, vm: EffectEditorViewModel, decimals: Int = if (scale >= 100.0) 2 else 1, enabled: Boolean = true) =
    IemRawSlider(label, value, range, scale, unit, decimals, enabled) { vm.updateIemInt(pref, it) }

@Composable
private fun IemRawSlider(label: String, value: Int, range: IntRange, scale: Double, unit: String, decimals: Int = if (scale >= 100.0) 2 else 1, enabled: Boolean = true, update: (Int) -> Unit) {
    val display = value / scale
    LabeledSlider(
        label = label, value = value.toFloat(), onValueChange = { update(it.roundToInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(), valueLabel = "${format(display, decimals)} $unit".trim(),
        edit = SliderEdit(display, range.first / scale..range.last / scale, decimals, { update((it * scale).roundToInt()) }, unit),
        enabled = enabled,
    )
}

@Composable
private fun IemMappedSlider(
    label: String,
    value: Int,
    displayValue: Double,
    displayRange: ClosedFloatingPointRange<Double>,
    decimals: Int,
    unit: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onDisplayCommit: (Double) -> Int,
) = LabeledSlider(
    label = label,
    value = value.toFloat(),
    onValueChange = { onValueChange(it.roundToInt()) },
    valueRange = 0f..1_000_000f,
    valueLabel = "${format(displayValue, decimals)} $unit",
    edit = SliderEdit(displayValue, displayRange, decimals, { onValueChange(onDisplayCommit(it)) }, unit),
    enabled = enabled,
)

@Composable private fun IemSwitch(label: String, checked: Boolean, pref: BoolPref, vm: EffectEditorViewModel) = LabeledSwitch(label, checked, { vm.updateIemBool(pref, it) })

@Composable private fun SectionTitle(text: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(text, color = MiuixTheme.colorScheme.primary, style = MiuixTheme.textStyles.subtitle)
}

@Composable private fun ReadOnlyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MiuixTheme.textStyles.body2)
        Text(value, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
    }
}

@Composable private fun IemAttributionFooter() {
    val context = LocalContext.current
    Text(
        stringResource(R.string.iem_powered_by),
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        style = MiuixTheme.textStyles.body2,
        modifier = Modifier.fillMaxWidth().clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(IEM_PROJECT_URL)))
        }.padding(vertical = 24.dp),
    )
}

private fun format(value: Double, decimals: Int): String = String.format(Locale.US, ".${decimals}f".let { "%$it" }, value)

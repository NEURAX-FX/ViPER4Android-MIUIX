package com.llsl.viper4android.ui.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.Waves
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llsl.viper4android.R
import com.llsl.viper4android.dsp.dynamicEqGraphModel
import com.llsl.viper4android.dsp.firGraphModel
import com.llsl.viper4android.dsp.multibandGraphModel
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.Effects
import com.llsl.viper4android.ui.components.EqEditDialog
import com.llsl.viper4android.ui.components.LabeledDropdown
import com.llsl.viper4android.ui.components.LabeledSlider
import com.llsl.viper4android.ui.components.LabeledSwitch
import com.llsl.viper4android.ui.components.SliderEdit
import com.llsl.viper4android.ui.components.resolvePresetName
import com.llsl.viper4android.ui.components.viper.ViperEffectCard
import com.llsl.viper4android.ui.components.viper.ViperCurvePreview
import com.llsl.viper4android.ui.components.viper.ViperEditorRow
import com.llsl.viper4android.ui.components.viper.ViperTabs
import com.llsl.viper4android.ui.components.viper.ViperTextFieldDialog
import com.llsl.viper4android.ui.components.EqCurveGraph
import com.llsl.viper4android.ui.theme.ViperType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private fun rawToDb(raw: Number): Double = 20.0 * log10(raw.toDouble() / 100.0)

private fun dbToRaw(db: Double): Int = (10.0.pow(db / 20.0) * 100.0).roundToInt()

@Composable
fun MasterLimiterRows(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val outputVolume = state.out.volume
    val channelPan = state.out.channelPan
    val limiter = state.out.limiter
    val gainDb = if (outputVolume > 0) rawToDb(outputVolume) else -99.9
    val limDb = if (limiter > 0) rawToDb(limiter) else -99.9
    val left = 50 - channelPan / 2
    val right = 50 + channelPan / 2
    ViperEffectCard(
        title = stringResource(R.string.section_output),
        summary =
            formatOutputSummary(
                outputVolume,
                channelPan,
                limiter,
                stringResource(R.string.label_output_limiter),
            ),
        enabled = state.masterEnable,
        onEnabledChange = viewModel::setMasterEnabled,
        icon = Icons.AutoMirrored.Filled.VolumeUp,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_output_volume),
            value = outputVolume.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.masterLimiter.outputVolume, it.roundToInt()) },
            valueRange = 1f..200f,
            valueLabel = "${"%.1f".format(gainDb)}dB",
            edit =
                SliderEdit(
                    displayValue = gainDb,
                    displayRange = rawToDb(1)..rawToDb(200),
                    decimals = 1,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.masterLimiter.outputVolume, dbToRaw(it).coerceIn(1, 200)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_output_pan),
            value = channelPan.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.masterLimiter.channelPan, it.roundToInt()) },
            valueRange = -100f..100f,
            valueLabel = "$left:$right",
            edit =
                SliderEdit(
                    displayValue = channelPan.toDouble(),
                    displayRange = -100.0..100.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.masterLimiter.channelPan, it.roundToInt()) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_output_limiter),
            value = limiter.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.masterLimiter.threshold, it.roundToInt()) },
            valueRange = 30f..100f,
            valueLabel = "${"%.1f".format(limDb)}dB",
            edit =
                SliderEdit(
                    displayValue = limDb,
                    displayRange = rawToDb(30)..rawToDb(100),
                    decimals = 1,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.masterLimiter.threshold, dbToRaw(it).coerceIn(30, 100)) },
                ),
        )
    }
}

@Composable
fun PlaybackGainSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.playbackGainControl
    val enabled = vals.enable
    val strength = vals.strength
    val maxGain = vals.maxGain
    val threshold = vals.outputThreshold
    val threshDb = if (threshold > 0) rawToDb(threshold) else -99.9

    ViperEffectCard(
        title = stringResource(R.string.section_agc),
        summary =
            joinEffectSummary(
                formatMultiplier(strength),
                "${stringResource(R.string.label_max_gain)} ${formatMultiplier(maxGain)}",
                String.format(Locale.US, "%.1f dB", threshDb),
            ),
        enabled = enabled,
        onEnabledChange = viewModel::setPlaybackGainControlEnabled,
        icon = Icons.AutoMirrored.Filled.TrendingUp,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_strength),
            value = strength.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.playbackGainControl.strength, it.roundToInt()) },
            valueRange = 50f..300f,
            valueLabel = "${"%.1f".format(strength / 100.0)}x",
            edit =
                SliderEdit(
                    displayValue = strength / 100.0,
                    displayRange = 0.5..3.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.playbackGainControl.strength, (it * 100).roundToInt().coerceIn(50, 300)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_max_gain),
            value = maxGain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.playbackGainControl.maxGain, it.roundToInt()) },
            valueRange = 100f..1000f,
            valueLabel = "${"%.1f".format(maxGain / 100.0)}x",
            edit =
                SliderEdit(
                    displayValue = maxGain / 100.0,
                    displayRange = 1.0..10.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.playbackGainControl.maxGain, (it * 100).roundToInt().coerceIn(100, 1000)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_agc_output_threshold),
            value = threshold.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.playbackGainControl.outputThreshold, it.roundToInt()) },
            valueRange = 30f..100f,
            valueLabel = "${"%.1f".format(threshDb)}dB",
            edit =
                SliderEdit(
                    displayValue = threshDb,
                    displayRange = rawToDb(30)..rawToDb(100),
                    decimals = 1,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.playbackGainControl.outputThreshold, dbToRaw(it).coerceIn(30, 100)) },
                ),
        )
    }
}

@Composable
fun LUFSTargetingSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.lufs
    val enabled = vals.enable
    val target = vals.target
    val maxGain = vals.maxGain
    val speed = vals.speed

    val speedNames =
        listOf(
            stringResource(R.string.label_lufs_speed_slow),
            stringResource(R.string.label_lufs_speed_medium),
            stringResource(R.string.label_lufs_speed_fast),
        )

    ViperEffectCard(
        title = stringResource(R.string.section_lufs_targeting),
        summary =
            joinEffectSummary(
                String.format(Locale.US, "%.1f LUFS", target / -10f),
                "${stringResource(R.string.label_max_gain)} ${String.format(Locale.US, "%.1f dB", maxGain / 10f)}",
                speedNames.getOrElse(speed) { speedNames[1] },
            ),
        enabled = enabled,
        onEnabledChange = viewModel::setLufsEnabled,
        icon = Icons.Default.CrisisAlert,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_lufs_target_lufs),
            value = target.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.lufs.target, it.roundToInt()) },
            valueRange = 80f..240f,
            valueLabel = String.format(Locale.US, "%.1f LUFS", target / -10f),
            edit =
                SliderEdit(
                    displayValue = target / -10.0,
                    displayRange = -24.0..-8.0,
                    decimals = 1,
                    unit = "LUFS",
                    onCommit = { viewModel.applyPref(Effects.lufs.target, (it * -10).roundToInt().coerceIn(80, 240)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_max_gain),
            value = maxGain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.lufs.maxGain, it.roundToInt()) },
            valueRange = 0f..120f,
            valueLabel = String.format(Locale.US, "%.1f dB", maxGain / 10f),
            edit =
                SliderEdit(
                    displayValue = maxGain / 10.0,
                    displayRange = 0.0..12.0,
                    decimals = 1,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.lufs.maxGain, (it * 10).roundToInt().coerceIn(0, 120)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_lufs_speed),
            value = speed.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.lufs.speed, it.roundToInt()) },
            valueRange = 0f..2f,
            steps = 1,
            valueLabel = speedNames.getOrElse(speed) { speedNames[1] },
        )
    }
}

@Composable
fun FetCompressorSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.fetCompressor
    val enabled = vals.enable
    val threshold = vals.threshold
    val ratio = vals.ratio
    val kneeAuto = vals.kneeAuto
    val knee = vals.knee
    val kneeMulti = vals.kneeMulti
    val gainAuto = vals.gainAuto
    val gain = vals.gain
    val attackAuto = vals.attackAuto
    val attack = vals.attack
    val maxAttack = vals.maxAttack
    val releaseAuto = vals.releaseAuto
    val release = vals.release
    val maxRelease = vals.maxRelease
    val crest = vals.crest
    val adapt = vals.adapt
    val noClip = vals.noClip

    ViperEffectCard(
        title = stringResource(R.string.section_fet_compressor),
        summary =
            joinEffectSummary(
                "$threshold dB",
                String.format(Locale.US, "%.1f:1", ratio / 100.0),
                if (gainAuto) stringResource(R.string.label_fet_auto_gain) else "$gain dB",
            ),
        enabled = enabled,
        onEnabledChange = viewModel::setFetCompressorEnabled,
        icon = Icons.Default.VerticalAlignCenter,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_threshold),
            value = threshold.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.threshold, it.roundToInt()) },
            valueRange = -48f..0f,
            valueLabel = "$threshold dB",
            edit =
                SliderEdit(
                    displayValue = threshold.toDouble(),
                    displayRange = -48.0..0.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.threshold, it.roundToInt().coerceIn(-48, 0)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_ratio),
            value = ratio / 100f,
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.ratio, (it * 100f).roundToInt()) },
            valueRange = 0f..2f,
            valueLabel = String.format(Locale.US, "%.1f", ratio / 100.0),
            edit =
                SliderEdit(
                    displayValue = ratio / 100.0,
                    displayRange = 0.0..2.0,
                    decimals = 1,
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.ratio, (it * 100).roundToInt().coerceIn(0, 200)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_knee),
            checked = kneeAuto,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.kneeAuto, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee),
            value = knee.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.knee, it.roundToInt()) },
            valueRange = 0f..12f,
            enabled = !kneeAuto,
            valueLabel = "$knee dB",
            edit =
                SliderEdit(
                    displayValue = knee.toDouble(),
                    displayRange = 0.0..12.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.knee, it.roundToInt().coerceIn(0, 12)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee_multi),
            value = (kneeMulti / 100f * 4f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.kneeMulti, (it / 4f * 100f).roundToInt()) },
            valueRange = 0f..4f,
            valueLabel = String.format(Locale.US, "%.1fx", kneeMulti / 100.0 * 4.0),
            edit =
                SliderEdit(
                    displayValue = kneeMulti / 100.0 * 4.0,
                    displayRange = 0.0..4.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.kneeMulti, (it / 4 * 100).roundToInt().coerceIn(0, 100)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_gain),
            checked = gainAuto,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.gainAuto, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.gain, it.roundToInt()) },
            valueRange = 0f..24f,
            enabled = !gainAuto,
            valueLabel = "$gain dB",
            edit =
                SliderEdit(
                    displayValue = gain.toDouble(),
                    displayRange = 0.0..24.0,
                    decimals = 0,
                    unit = "dB",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.gain, it.roundToInt().coerceIn(0, 24)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_attack),
            checked = attackAuto,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.attackAuto, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_attack),
            value = attack.toFloat().coerceIn(1f, 100f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.attack, it.roundToInt()) },
            valueRange = 1f..100f,
            enabled = !attackAuto,
            valueLabel = "$attack ms",
            edit =
                SliderEdit(
                    displayValue = attack.toDouble(),
                    displayRange = 1.0..100.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.attack, it.roundToInt().coerceIn(1, 100)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_attack),
            value = maxAttack.toFloat().coerceIn(1f, 100f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.maxAttack, it.roundToInt()) },
            valueRange = 1f..100f,
            valueLabel = "$maxAttack ms",
            edit =
                SliderEdit(
                    displayValue = maxAttack.toDouble(),
                    displayRange = 1.0..100.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.maxAttack, it.roundToInt().coerceIn(1, 100)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_release),
            checked = releaseAuto,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.releaseAuto, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_release),
            value = release.toFloat().coerceIn(5f, 500f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.release, it.roundToInt()) },
            valueRange = 5f..500f,
            enabled = !releaseAuto,
            valueLabel = "$release ms",
            edit =
                SliderEdit(
                    displayValue = release.toDouble(),
                    displayRange = 5.0..500.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.release, it.roundToInt().coerceIn(5, 500)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_release),
            value = maxRelease.toFloat().coerceIn(5f, 500f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.maxRelease, it.roundToInt()) },
            valueRange = 5f..500f,
            valueLabel = "$maxRelease ms",
            edit =
                SliderEdit(
                    displayValue = maxRelease.toDouble(),
                    displayRange = 5.0..500.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.maxRelease, it.roundToInt().coerceIn(5, 500)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_crest),
            value = crest.toFloat().coerceIn(5f, 300f),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.crest, it.roundToInt()) },
            valueRange = 5f..300f,
            valueLabel = "$crest ms",
            edit =
                SliderEdit(
                    displayValue = crest.toDouble(),
                    displayRange = 5.0..300.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.crest, it.roundToInt().coerceIn(5, 300)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_adapt),
            value = adapt.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fetCompressor.adapt, it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$adapt%",
            edit =
                SliderEdit(
                    displayValue = adapt.toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.fetCompressor.adapt, it.roundToInt().coerceIn(0, 200)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_no_clip),
            checked = noClip,
            onCheckedChange = { viewModel.applyPref(Effects.fetCompressor.noClip, it) },
        )
    }
}

@Composable
fun MultibandCompressorSection(
    state: EffectState,
    viewModel: MainViewModel,
    showCurvePreview: Boolean = true,
    onOpenEditor: () -> Unit,
) {
    val sampleRate by viewModel.graphSampleRate.collectAsStateWithLifecycle()
    val model = remember(state.multibandCompressor, sampleRate) {
        multibandGraphModel(state, sampleRate)
    }
    ViperEffectCard(
        title = stringResource(R.string.section_multiband_compressor),
        summary =
            stringResource(
                R.string.summary_active_bands,
                state.multibandCompressor.bandEnables.count { it },
            ),
        enabled = state.multibandCompressor.enable,
        onEnabledChange = viewModel::setMultibandCompressorEnabled,
        icon = Icons.Default.Compress,
    ) {
        if (showCurvePreview) {
            ViperCurvePreview(
                curve = model.unitySumCurve,
                bandCurves = model.bandCurves,
                contentDescription = stringResource(R.string.section_multiband_compressor),
                onClick = onOpenEditor,
            )
            Spacer(Modifier.height(8.dp))
        }
        ViperEditorRow(
            title = stringResource(R.string.action_open_mbc_editor),
            icon = Icons.Default.Insights,
            onClick = onOpenEditor,
        )
    }
}

@Composable
fun DdcSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.ddc
    val enabled = vals.enable
    val device = vals.device

    val vdcFiles by viewModel.vdcFileList.collectAsStateWithLifecycle()
    val vdcNoneLabel = stringResource(R.string.label_none)
    val cdvOptions = vdcFiles.ifEmpty { listOf(vdcNoneLabel) }

    ViperEffectCard(
        title = stringResource(R.string.section_ddc),
        summary = basenameOrNone(device, vdcNoneLabel),
        enabled = enabled,
        onEnabledChange = viewModel::setDdcEnabled,
        icon = Icons.Default.SettingsInputComponent,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_ddc_device),
            selectedValue = device.ifEmpty { vdcNoneLabel },
            options = cdvOptions,
            onOptionSelected = { _, value ->
                viewModel.setDdcDevice(if (value == vdcNoneLabel) "" else value)
            },
            onDeleteOption = { _, name -> viewModel.deleteVdcFile(name) },
            isOptionDeletable = { _, name -> name != vdcNoneLabel },
        )
    }
}

@Composable
fun SpectrumExtensionSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.spectrumExtension
    val enabled = vals.enable
    val strength = vals.strength
    val exciter = vals.exciter

    ViperEffectCard(
        title = stringResource(R.string.section_spectrum_extension),
        summary = joinEffectSummary("$strength Hz", "$exciter%"),
        enabled = enabled,
        onEnabledChange = viewModel::setSpectrumExtensionEnabled,
        icon = Icons.Default.Waves,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_strength),
            value = strength.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.spectrumExtension.strength, it.roundToInt()) },
            valueRange = 2200f..8200f,
            steps = 1199,
            valueLabel = "$strength Hz",
            edit =
                SliderEdit(
                    displayValue = strength.toDouble(),
                    displayRange = 2200.0..8200.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { viewModel.applyPref(Effects.spectrumExtension.strength, it.roundToInt().coerceIn(2200, 8200)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_vse_exciter),
            value = exciter.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.spectrumExtension.exciter, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$exciter%",
            edit =
                SliderEdit(
                    displayValue = exciter.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.spectrumExtension.exciter, it.roundToInt().coerceIn(0, 100)) },
                ),
        )
    }
}

@Composable
fun EqualizerSection(
    state: EffectState,
    viewModel: MainViewModel,
    showCurvePreview: Boolean = true,
    onOpenEditor: () -> Unit,
) {
    val sampleRate by viewModel.graphSampleRate.collectAsStateWithLifecycle()
    val model = remember(state.eq, sampleRate) { firGraphModel(state, sampleRate) }
    val presetName =
        state.eq.presets
            .firstOrNull { it.id == state.eq.presetId }
            ?.let { resolvePresetName(it) }
            ?: stringResource(R.string.label_custom)
    ViperEffectCard(
        title = stringResource(R.string.section_equalizer),
        summary =
            joinEffectSummary(
                stringResource(R.string.label_eq_n_bands, state.eq.bandCount),
                presetName,
            ),
        enabled = state.eq.enable,
        onEnabledChange = viewModel::setEqEnabled,
        icon = Icons.Default.Equalizer,
    ) {
        if (showCurvePreview) {
            ViperCurvePreview(
                curve = model.curve,
                contentDescription = stringResource(R.string.section_equalizer),
                onClick = onOpenEditor,
            )
            Spacer(Modifier.height(8.dp))
        }
        ViperEditorRow(
            title = stringResource(R.string.action_open_eq_editor),
            icon = Icons.Default.Equalizer,
            onClick = onOpenEditor,
        )
    }
}

@Composable
fun DynamicEqSection(
    state: EffectState,
    viewModel: MainViewModel,
    showCurvePreview: Boolean = true,
    onOpenEditor: () -> Unit,
) {
    val sampleRate by viewModel.graphSampleRate.collectAsStateWithLifecycle()
    val model = remember(state.dynamicEq, sampleRate) { dynamicEqGraphModel(state, sampleRate) }
    ViperEffectCard(
        title = stringResource(R.string.section_dynamic_eq),
        summary =
            joinEffectSummary(
                stringResource(R.string.label_eq_n_bands, state.dynamicEq.bandCount),
                stringResource(R.string.label_custom),
            ),
        enabled = state.dynamicEq.enable,
        onEnabledChange = viewModel::setDynamicEqEnabled,
        icon = Icons.Default.GraphicEq,
    ) {
        if (showCurvePreview) {
            ViperCurvePreview(
                curve = model.curve,
                contentDescription = stringResource(R.string.section_dynamic_eq),
                onClick = onOpenEditor,
            )
            Spacer(Modifier.height(8.dp))
        }
        ViperEditorRow(
            title = stringResource(R.string.action_open_dynamic_eq_editor),
            icon = Icons.Default.GraphicEq,
            onClick = onOpenEditor,
        )
    }
}

@Composable
fun ConvolverSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.convolver
    val enabled = vals.enable
    val kernel = vals.kernelFile
    val crossChannel = vals.crossChannel
    val wet = vals.wet
    val outputGain = vals.outputGain
    val routing = vals.routing
    val crossDelay100Ns = vals.crossDelay100Ns

    val kernelFiles by viewModel.kernelFileList.collectAsStateWithLifecycle()
    val kernelNoneLabel = stringResource(R.string.label_none)
    val kernelOptions = kernelFiles.ifEmpty { listOf(kernelNoneLabel) }

    ViperEffectCard(
        title = stringResource(R.string.section_convolver),
        summary =
            formatConvolverSummary(
                kernel,
                wet,
                crossDelay100Ns,
                kernelNoneLabel,
                stringResource(R.string.label_convolver_wet),
            ),
        enabled = enabled,
        onEnabledChange = viewModel::setConvolverEnabled,
        icon = Icons.Default.BlurCircular,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_convolver_kernel),
            selectedValue = kernel.ifEmpty { kernelNoneLabel },
            options = kernelOptions,
            onOptionSelected = { _, value ->
                viewModel.setConvolverKernel(if (value == kernelNoneLabel) "" else value)
            },
            onDeleteOption = { _, name -> viewModel.deleteKernelFile(name) },
            isOptionDeletable = { _, name -> name != kernelNoneLabel },
        )
        LabeledSlider(
            label = stringResource(R.string.label_convolver_cross_channel),
            value = crossChannel.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.convolver.crossChannel, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$crossChannel%",
            edit =
                SliderEdit(
                    displayValue = crossChannel.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.convolver.crossChannel, it.roundToInt().coerceIn(0, 100)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_convolver_wet),
            value = wet.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.convolver.wet, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$wet%",
            edit =
                SliderEdit(
                    displayValue = wet.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.convolver.wet, it.roundToInt().coerceIn(0, 100)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_convolver_output_gain),
            value = outputGain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.convolver.outputGain, it.roundToInt()) },
            valueRange = -240f..240f,
            valueLabel = String.format(Locale.US, "%.1f dB", outputGain / 10f),
            edit =
                SliderEdit(
                    displayValue = outputGain / 10.0,
                    displayRange = -24.0..24.0,
                    decimals = 1,
                    unit = "dB",
                    onCommit = {
                        viewModel.applyPref(
                            Effects.convolver.outputGain,
                            (it * 10).roundToInt().coerceIn(-240, 240),
                        )
                    },
                ),
        )
        val routingOptions =
            listOf(
                stringResource(R.string.convolver_routing_direct_cross),
                stringResource(R.string.convolver_routing_direct_only),
                stringResource(R.string.convolver_routing_cross_only),
            )
        LabeledDropdown(
            label = stringResource(R.string.label_convolver_routing),
            selectedValue = routingOptions.getOrElse(routing) { routingOptions[0] },
            options = routingOptions,
            onOptionSelected = { index, _ -> viewModel.applyPref(Effects.convolver.routing, index) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_convolver_cross_delay),
            value = crossDelay100Ns.toFloat(),
            onValueChange = {
                viewModel.applyPref(Effects.convolver.crossDelay100Ns, it.roundToInt())
            },
            valueRange = 0f..100000f,
            valueLabel = String.format(Locale.US, "%.4f ms", crossDelay100Ns / 10000f),
            edit =
                SliderEdit(
                    displayValue = crossDelay100Ns / 10000.0,
                    displayRange = 0.0..10.0,
                    decimals = 4,
                    unit = "ms",
                    onCommit = {
                        viewModel.applyPref(
                            Effects.convolver.crossDelay100Ns,
                            (it * 10000).roundToInt().coerceIn(0, 100000),
                        )
                    },
                ),
        )
    }
}

@Composable
fun IemSection(
    state: EffectState,
    viewModel: MainViewModel,
    onOpenEditor: () -> Unit,
) {
    val iem = state.iem
    val modeOptions =
        listOf(
            stringResource(R.string.iem_mode_stereo),
            stringResource(R.string.iem_mode_multi),
            stringResource(R.string.iem_mode_granular),
            stringResource(R.string.iem_mode_halo),
        )
    val orderOptions =
        listOf(
            stringResource(R.string.iem_order_first),
            stringResource(R.string.iem_order_second),
            stringResource(R.string.iem_order_third),
        )

    ViperEffectCard(
        title = stringResource(R.string.section_iem),
        summary = iemSummary(iem),
        enabled = iem.general.enable,
        onEnabledChange = viewModel::setIemEnabled,
        icon = Icons.Default.SurroundSound,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.iem_encoder_mode),
            selectedValue = modeOptions[iem.general.encoderMode.coerceIn(modeOptions.indices)],
            options = modeOptions,
            onOptionSelected = { index, _ -> viewModel.setIemEncoderMode(index) },
        )
        ViperTabs(
            tabs = orderOptions,
            selectedTabIndex = (iem.general.order - 1).coerceIn(orderOptions.indices),
            onTabSelected = { viewModel.setIemOrder(it + 1) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        LabeledSlider(
            label = stringResource(R.string.iem_wet),
            value = iem.output.wetPercent.toFloat(),
            onValueChange = { viewModel.setIemWet(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "${iem.output.wetPercent}%",
        )
        ViperEditorRow(
            title = stringResource(R.string.action_open_iem_editor),
            icon = Icons.Default.SpatialAudio,
            onClick = onOpenEditor,
        )
    }
}

@Composable
fun FieldSurroundSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.fieldSurround
    val enabled = vals.enable
    val widening = vals.widening
    val midImage = vals.midImage
    val depth = vals.depth

    ViperEffectCard(
        title = stringResource(R.string.section_field_surround),
        summary = joinEffectSummary("$widening", "$midImage", "$depth"),
        enabled = enabled,
        onEnabledChange = viewModel::setFieldSurroundEnabled,
        icon = Icons.Default.SurroundSound,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_field_surround_widening),
            value = widening.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fieldSurround.widening, it.roundToInt()) },
            valueRange = 0f..8f,
            steps = 7,
            valueLabel = "$widening",
            edit =
                SliderEdit(
                    displayValue = widening.toDouble(),
                    displayRange = 0.0..8.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.fieldSurround.widening, it.roundToInt().coerceIn(0, 8)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_field_surround_mid_image),
            value = midImage.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fieldSurround.midImage, it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            edit =
                SliderEdit(
                    displayValue = midImage.toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.fieldSurround.midImage, it.roundToInt().coerceIn(0, 10)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_depth),
            value = depth.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.fieldSurround.depth, it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            edit =
                SliderEdit(
                    displayValue = depth.toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.fieldSurround.depth, it.roundToInt().coerceIn(0, 10)) },
                ),
        )
    }
}

@Composable
fun DiffSurroundSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.diffSurround
    val enabled = vals.enable
    val delay = vals.delay
    val reverse = vals.reverse
    val wetDryMix = vals.wetDryMix
    val lpCutoff = vals.lpCutoff

    ViperEffectCard(
        title = stringResource(R.string.section_diff_surround),
        summary =
            joinEffectSummary(
                "$delay ms",
                "$wetDryMix%",
                if (reverse) stringResource(R.string.label_diff_surround_reverse) else stringResource(R.string.label_off),
            ),
        enabled = enabled,
        onEnabledChange = viewModel::setDiffSurroundEnabled,
        icon = Icons.Default.SpatialAudio,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_delay),
            value = delay.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.diffSurround.delay, it.roundToInt()) },
            valueRange = 1f..20f,
            steps = 18,
            valueLabel = "$delay ms",
            edit =
                SliderEdit(
                    displayValue = delay.toDouble(),
                    displayRange = 1.0..20.0,
                    decimals = 0,
                    unit = "ms",
                    onCommit = { viewModel.applyPref(Effects.diffSurround.delay, it.roundToInt().coerceIn(1, 20)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_diff_surround_reverse),
            checked = reverse,
            onCheckedChange = { viewModel.applyPref(Effects.diffSurround.reverse, it) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_diff_surround_wet_dry_mix),
            value = wetDryMix.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.diffSurround.wetDryMix, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$wetDryMix%",
            edit =
                SliderEdit(
                    displayValue = wetDryMix.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.diffSurround.wetDryMix, it.roundToInt().coerceIn(0, 100)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_diff_surround_lp_cutoff),
            value = lpCutoff.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.diffSurround.lpCutoff, it.roundToInt()) },
            valueRange = 0f..20000f,
            steps = 3999,
            valueLabel = if (lpCutoff == 0) stringResource(R.string.label_off) else "$lpCutoff Hz",
            edit =
                SliderEdit(
                    displayValue = lpCutoff.toDouble(),
                    displayRange = 0.0..20000.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { viewModel.applyPref(Effects.diffSurround.lpCutoff, it.roundToInt().coerceIn(0, 20000)) },
                ),
        )
    }
}

@Composable
fun StereoImagerSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.stereoImager
    val enabled = vals.enable
    val lowWidth = vals.lowWidth
    val midWidth = vals.midWidth
    val highWidth = vals.highWidth
    val lowCrossover = vals.lowCrossover
    val highCrossover = vals.highCrossover

    ViperEffectCard(
        title = stringResource(R.string.section_stereo_imager),
        summary = "$lowWidth% / $midWidth% / $highWidth%",
        enabled = enabled,
        onEnabledChange = viewModel::setStereoImagerEnabled,
        icon = Icons.Default.AspectRatio,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_low_width),
            value = lowWidth.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.stereoImager.lowWidth, it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$lowWidth%",
            edit =
                SliderEdit(
                    displayValue = lowWidth.toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.lowWidth, it.roundToInt().coerceIn(0, 200)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_mid_width),
            value = midWidth.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.stereoImager.midWidth, it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$midWidth%",
            edit =
                SliderEdit(
                    displayValue = midWidth.toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.midWidth, it.roundToInt().coerceIn(0, 200)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_high_width),
            value = highWidth.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.stereoImager.highWidth, it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$highWidth%",
            edit =
                SliderEdit(
                    displayValue = highWidth.toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.highWidth, it.roundToInt().coerceIn(0, 200)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_low_crossover),
            value = lowCrossover.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.stereoImager.lowCrossover, it.roundToInt()) },
            valueRange = 80f..400f,
            steps = 63,
            valueLabel = "$lowCrossover Hz",
            edit =
                SliderEdit(
                    displayValue = lowCrossover.toDouble(),
                    displayRange = 80.0..400.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.lowCrossover, it.roundToInt().coerceIn(80, 400)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_high_crossover),
            value = highCrossover.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.stereoImager.highCrossover, it.roundToInt()) },
            valueRange = 2000f..8000f,
            steps = 1199,
            valueLabel = "$highCrossover Hz",
            edit =
                SliderEdit(
                    displayValue = highCrossover.toDouble(),
                    displayRange = 2000.0..8000.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { viewModel.applyPref(Effects.stereoImager.highCrossover, it.roundToInt().coerceIn(2000, 8000)) },
                ),
        )
    }
}

@Composable
fun HeadphoneSurroundSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.headphoneSurround
    val enabled = vals.enable
    val quality = vals.quality

    ViperEffectCard(
        title = stringResource(R.string.section_headphone_surround),
        summary = stringResource(R.string.label_vhe_quality) + " $quality",
        enabled = enabled,
        onEnabledChange = viewModel::setHeadphoneSurroundEnabled,
        icon = Icons.Default.Headphones,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_vhe_quality),
            value = quality.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.headphoneSurround.quality, it.roundToInt()) },
            valueRange = 0f..4f,
            steps = 3,
            edit =
                SliderEdit(
                    displayValue = quality.toDouble(),
                    displayRange = 0.0..4.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.headphoneSurround.quality, it.roundToInt().coerceIn(0, 4)) },
                ),
        )
    }
}

@Composable
fun ReverberationSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.reverb
    val enabled = vals.enable
    val roomSize = vals.roomSize
    val width = vals.width
    val damp = vals.damp
    val wet = vals.wet
    val dry = vals.dry

    ViperEffectCard(
        title = stringResource(R.string.section_reverb),
        summary = joinEffectSummary("$roomSize", "$damp", "$wet%"),
        enabled = enabled,
        onEnabledChange = viewModel::setReverbEnabled,
        icon = Icons.Default.BlurOn,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_reverb_room_size),
            value = roomSize.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.reverb.roomSize, it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            edit =
                SliderEdit(
                    displayValue = roomSize.toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.reverb.roomSize, it.roundToInt().coerceIn(0, 10)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_width),
            value = width.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.reverb.width, it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            edit =
                SliderEdit(
                    displayValue = width.toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.reverb.width, it.roundToInt().coerceIn(0, 10)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_dampening),
            value = damp.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.reverb.damp, it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            edit =
                SliderEdit(
                    displayValue = damp.toDouble(),
                    displayRange = 0.0..10.0,
                    decimals = 0,
                    onCommit = { viewModel.applyPref(Effects.reverb.damp, it.roundToInt().coerceIn(0, 10)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_wet),
            value = wet.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.reverb.wet, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$wet%",
            edit =
                SliderEdit(
                    displayValue = wet.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.reverb.wet, it.roundToInt().coerceIn(0, 100)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_dry),
            value = dry.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.reverb.dry, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$dry%",
            edit =
                SliderEdit(
                    displayValue = dry.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.reverb.dry, it.roundToInt().coerceIn(0, 100)) },
                ),
        )
    }
}

@Composable
fun DynamicSystemSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.dynamicSystem
    val enabled = vals.enable
    val strength = vals.strength
    val dsPresetId = vals.presetId
    val dsPresets = vals.presets
    val xLow = vals.xLow
    val xHigh = vals.xHigh
    val yLow = vals.yLow
    val yHigh = vals.yHigh
    val sideGainLow = vals.sideGainLow
    val sideGainHigh = vals.sideGainHigh

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    val onPresetSelect = viewModel::setDynamicSystemPreset
    val onXLowChange = viewModel::setDynamicSystemXLow
    val onXHighChange = viewModel::setDynamicSystemXHigh
    val onYLowChange = viewModel::setDynamicSystemYLow
    val onYHighChange = viewModel::setDynamicSystemYHigh
    val onSideGainLowChange = viewModel::setDynamicSystemSideGainLow
    val onSideGainHighChange = viewModel::setDynamicSystemSideGainHigh
    val onPresetAdd = viewModel::addDynamicSystemPreset
    val onPresetDelete = viewModel::deleteDynamicSystemPreset
    val onReset = viewModel::resetDynamicSystemCoefficients
    val dynamicPresetName =
        dsPresets.find { it.id == dsPresetId }?.let { resolvePresetName(it) }
            ?: stringResource(R.string.label_custom)

    ViperEffectCard(
        title = stringResource(R.string.section_dynamic_system),
        summary = joinEffectSummary(dynamicPresetName, "$strength%"),
        enabled = enabled,
        onEnabledChange = viewModel::setDynamicSystemEnabled,
        icon = Icons.Default.CandlestickChart,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_preset),
            selectedValue = dynamicPresetName,
            options = dsPresets.map { resolvePresetName(it) },
            onOptionSelected = { index, _ -> onPresetSelect(dsPresets[index].id) },
        )

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
                onClick = { dsPresetId?.let { onPresetDelete(it) } },
                enabled = dsPresetId != null,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.action_reset),
                onClick = onReset,
                modifier = Modifier.weight(1f),
            )
        }

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_strength),
            value = strength.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.dynamicSystem.strength, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$strength%",
            edit =
                SliderEdit(
                    displayValue = strength.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.dynamicSystem.strength, it.roundToInt().coerceIn(0, 100)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_x_low_freq),
            value = xLow.toFloat(),
            onValueChange = { onXLowChange(it.roundToInt()) },
            valueRange = 0f..2400f,
            steps = (2400 / 5) - 1,
            valueLabel = "$xLow Hz",
            edit =
                SliderEdit(
                    displayValue = xLow.toDouble(),
                    displayRange = 0.0..2400.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { onXLowChange(it.roundToInt().coerceIn(0, 2400)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_x_high_freq),
            value = xHigh.toFloat(),
            onValueChange = { onXHighChange(it.roundToInt()) },
            valueRange = 0f..12000f,
            steps = (12000 / 5) - 1,
            valueLabel = "$xHigh Hz",
            edit =
                SliderEdit(
                    displayValue = xHigh.toDouble(),
                    displayRange = 0.0..12000.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { onXHighChange(it.roundToInt().coerceIn(0, 12000)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_y_low_freq),
            value = yLow.toFloat(),
            onValueChange = { onYLowChange(it.roundToInt()) },
            valueRange = 0f..200f,
            steps = 199,
            valueLabel = "$yLow Hz",
            edit =
                SliderEdit(
                    displayValue = yLow.toDouble(),
                    displayRange = 0.0..200.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { onYLowChange(it.roundToInt().coerceIn(0, 200)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_y_high_freq),
            value = yHigh.toFloat(),
            onValueChange = { onYHighChange(it.roundToInt()) },
            valueRange = 0f..300f,
            steps = (300 / 5) - 1,
            valueLabel = "$yHigh Hz",
            edit =
                SliderEdit(
                    displayValue = yHigh.toDouble(),
                    displayRange = 0.0..300.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { onYHighChange(it.roundToInt().coerceIn(0, 300)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_side_gain_low),
            value = sideGainLow.toFloat(),
            onValueChange = { onSideGainLowChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$sideGainLow%",
            edit =
                SliderEdit(
                    displayValue = sideGainLow.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { onSideGainLowChange(it.roundToInt().coerceIn(0, 100)) },
                ),
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_side_gain_high),
            value = sideGainHigh.toFloat(),
            onValueChange = { onSideGainHighChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$sideGainHigh%",
            edit =
                SliderEdit(
                    displayValue = sideGainHigh.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { onSideGainHighChange(it.roundToInt().coerceIn(0, 100)) },
                ),
        )
    }

    if (showSaveDialog) {
        ViperTextFieldDialog(
            show = true,
            onDismissRequest = { showSaveDialog = false },
            title = stringResource(R.string.preset_save_title),
            value = TextFieldValue(presetNameInput),
            onValueChange = { presetNameInput = it.text },
            label = stringResource(R.string.preset_name_hint),
            confirmText = stringResource(android.R.string.ok),
            onConfirm = {
                if (presetNameInput.isNotBlank()) {
                    onPresetAdd(presetNameInput.trim())
                    presetNameInput = ""
                    showSaveDialog = false
                }
            },
            confirmEnabled = presetNameInput.isNotBlank(),
            dismissText = stringResource(android.R.string.cancel),
            onDismiss = { showSaveDialog = false },
        )
    }
}

@Composable
fun TubeSimulatorSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.tubeSimulator
    val enabled = vals.enable

    ViperEffectCard(
        title = stringResource(R.string.section_tube_simulator),
        summary = stringResource(if (enabled) R.string.status_active else R.string.status_inactive),
        enabled = enabled,
        onEnabledChange = viewModel::setTubeSimulatorEnabled,
        icon = Icons.Default.MusicNote,
        toggleOnly = true,
    ) {}
}

@Composable
fun PsychoacousticBassSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.psychoacousticBass
    val enabled = vals.enable
    val cutoff = vals.cutoff
    val intensity = vals.intensity
    val harmonicOrder = vals.harmonicOrder
    val originalLevel = vals.originalLevel

    val harmonicNames =
        listOf(
            stringResource(R.string.harmonic_2nd),
            stringResource(R.string.harmonic_3rd),
            stringResource(R.string.harmonic_4th),
            stringResource(R.string.harmonic_5th),
        )
    val harmonicValues = listOf(2, 3, 4, 5)
    val harmonicIndex = harmonicValues.indexOf(harmonicOrder).coerceAtLeast(0)

    ViperEffectCard(
        title = stringResource(R.string.section_psycho_bass),
        summary = joinEffectSummary("$cutoff Hz", "$intensity%", harmonicNames[harmonicIndex]),
        enabled = enabled,
        onEnabledChange = viewModel::setPsychoacousticBassEnabled,
        icon = Icons.Default.Psychology,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_psycho_bass_cutoff),
            value = cutoff.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.psychoacousticBass.cutoff, it.roundToInt()) },
            valueRange = 60f..150f,
            valueLabel = "$cutoff Hz",
            edit =
                SliderEdit(
                    displayValue = cutoff.toDouble(),
                    displayRange = 60.0..150.0,
                    decimals = 0,
                    unit = "Hz",
                    onCommit = { viewModel.applyPref(Effects.psychoacousticBass.cutoff, it.roundToInt().coerceIn(60, 150)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_psycho_bass_intensity),
            value = intensity.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.psychoacousticBass.intensity, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$intensity%",
            edit =
                SliderEdit(
                    displayValue = intensity.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.psychoacousticBass.intensity, it.roundToInt().coerceIn(0, 100)) },
                ),
        )
        LabeledSlider(
            label = stringResource(R.string.label_psycho_bass_harmonic_order),
            value = harmonicOrder.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.psychoacousticBass.harmonicOrder, it.roundToInt()) },
            valueRange = 2f..5f,
            steps = 2,
            valueLabel = harmonicNames[harmonicIndex],
        )
        LabeledSlider(
            label = stringResource(R.string.label_psycho_bass_ori_bass_level),
            value = originalLevel.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.psychoacousticBass.originalLevel, it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$originalLevel%",
            edit =
                SliderEdit(
                    displayValue = originalLevel.toDouble(),
                    displayRange = 0.0..100.0,
                    decimals = 0,
                    unit = "%",
                    onCommit = { viewModel.applyPref(Effects.psychoacousticBass.originalLevel, it.roundToInt().coerceIn(0, 100)) },
                ),
        )
    }
}

@Composable
fun ViperBassSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.bass
    val enabled = vals.enable
    val mode = vals.mode
    val frequency = vals.frequency
    val gain = vals.gain
    val antiPop = vals.antiPop

    val modeNames =
        listOf(
            stringResource(R.string.bass_mode_natural),
            stringResource(R.string.bass_mode_pure),
            stringResource(R.string.bass_mode_subwoofer),
        )

    ViperEffectCard(
        title = stringResource(R.string.section_viper_bass),
        summary =
            joinEffectSummary(
                modeNames.getOrElse(mode) { modeNames[0] },
                if (mode != 2) "${frequency + 15} Hz" else "",
                formatMultiplier(gain),
            ),
        enabled = enabled,
        onEnabledChange = viewModel::setBassEnabled,
        icon = Icons.Default.GraphicEq,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> viewModel.applyPref(Effects.bass.mode, index) },
        )
        if (mode != 2) {
            LabeledSlider(
                label = stringResource(R.string.label_frequency),
                value = frequency.toFloat(),
                onValueChange = { viewModel.applyPref(Effects.bass.frequency, it.roundToInt()) },
                valueRange = 0f..135f,
                steps = 134,
                valueLabel = "${frequency + 15}Hz",
                edit =
                    SliderEdit(
                        displayValue = (frequency + 15).toDouble(),
                        displayRange = 15.0..150.0,
                        decimals = 0,
                        unit = "Hz",
                        onCommit = { viewModel.applyPref(Effects.bass.frequency, (it - 15).roundToInt().coerceIn(0, 135)) },
                    ),
            )
        }
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.bass.gain, it.roundToInt()) },
            valueRange = 50f..1000f,
            valueLabel = "${"%.1f".format(gain / 100.0)}x",
            edit =
                SliderEdit(
                    displayValue = gain / 100.0,
                    displayRange = 0.5..10.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.bass.gain, (it * 100).roundToInt().coerceIn(50, 1000)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_bass_anti_pop),
            checked = antiPop,
            onCheckedChange = { viewModel.applyPref(Effects.bass.antiPop, it) },
        )
    }
}

@Composable
fun ViperBassMonoSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.bassMono
    val enabled = vals.enable
    val mode = vals.mode
    val frequency = vals.frequency
    val gain = vals.gain
    val antiPop = vals.antiPop

    val modeNames =
        listOf(
            stringResource(R.string.bass_mode_natural),
            stringResource(R.string.bass_mode_pure),
            stringResource(R.string.bass_mode_subwoofer),
        )

    ViperEffectCard(
        title = stringResource(R.string.section_viper_bass_mono),
        summary =
            joinEffectSummary(
                modeNames.getOrElse(mode) { modeNames[0] },
                if (mode != 2) "${frequency + 15} Hz" else "",
                formatMultiplier(gain),
            ),
        enabled = enabled,
        onEnabledChange = viewModel::setBassMonoEnabled,
        icon = Icons.Default.GraphicEq,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> viewModel.applyPref(Effects.bassMono.mode, index) },
        )
        if (mode != 2) {
            LabeledSlider(
                label = stringResource(R.string.label_frequency),
                value = frequency.toFloat(),
                onValueChange = { viewModel.applyPref(Effects.bassMono.frequency, it.roundToInt()) },
                valueRange = 0f..135f,
                steps = 134,
                valueLabel = "${frequency + 15}Hz",
                edit =
                    SliderEdit(
                        displayValue = (frequency + 15).toDouble(),
                        displayRange = 15.0..150.0,
                        decimals = 0,
                        unit = "Hz",
                        onCommit = { viewModel.applyPref(Effects.bassMono.frequency, (it - 15).roundToInt().coerceIn(0, 135)) },
                    ),
            )
        }
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.bassMono.gain, it.roundToInt()) },
            valueRange = 50f..1000f,
            valueLabel = "${"%.1f".format(gain / 100.0)}x",
            edit =
                SliderEdit(
                    displayValue = gain / 100.0,
                    displayRange = 0.5..10.0,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.bassMono.gain, (it * 100).roundToInt().coerceIn(50, 1000)) },
                ),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_bass_anti_pop),
            checked = antiPop,
            onCheckedChange = { viewModel.applyPref(Effects.bassMono.antiPop, it) },
        )
    }
}

@Composable
fun ViperClaritySection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.clarity
    val enabled = vals.enable
    val mode = vals.mode
    val gain = vals.gain

    val modeNames =
        listOf(
            stringResource(R.string.clarity_mode_natural),
            stringResource(R.string.clarity_mode_ozone),
            stringResource(R.string.clarity_mode_xhifi),
        )

    ViperEffectCard(
        title = stringResource(R.string.section_viper_clarity),
        summary = joinEffectSummary(modeNames.getOrElse(mode) { modeNames[0] }, formatMultiplier(gain)),
        enabled = enabled,
        onEnabledChange = viewModel::setClarityEnabled,
        icon = Icons.Default.Hearing,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> viewModel.applyPref(Effects.clarity.mode, index) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { viewModel.applyPref(Effects.clarity.gain, it.roundToInt()) },
            valueRange = 0f..450f,
            valueLabel = "${"%.1f".format(gain / 100.0)}x",
            edit =
                SliderEdit(
                    displayValue = gain / 100.0,
                    displayRange = 0.0..4.5,
                    decimals = 1,
                    unit = "x",
                    onCommit = { viewModel.applyPref(Effects.clarity.gain, (it * 100).roundToInt().coerceIn(0, 450)) },
                ),
        )
    }
}

@Composable
fun AuditoryProtectionSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.cure
    val enabled = vals.enable
    val crossfeedPreset = vals.crossfeedPreset

    val strengthNames =
        listOf(
            stringResource(R.string.label_mild),
            stringResource(R.string.label_medium),
            stringResource(R.string.label_strong),
        )

    ViperEffectCard(
        title = stringResource(R.string.section_cure),
        summary = strengthNames.getOrElse(crossfeedPreset) { strengthNames[0] },
        enabled = enabled,
        onEnabledChange = viewModel::setCureEnabled,
        icon = Icons.Default.HealthAndSafety,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_cure_strength),
            selectedValue = strengthNames.getOrElse(crossfeedPreset) { strengthNames[0] },
            options = strengthNames,
            onOptionSelected = { index, _ ->
                viewModel.applyPref(Effects.cure.crossfeedPreset, index)
            },
        )
    }
}

@Composable
fun AnalogXSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    val vals = state.analogX
    val enabled = vals.enable
    val mode = vals.mode

    val modeNames =
        listOf(
            stringResource(R.string.label_mild),
            stringResource(R.string.label_medium),
            stringResource(R.string.label_strong),
        )

    ViperEffectCard(
        title = stringResource(R.string.section_analogx),
        summary = modeNames.getOrElse(mode) { modeNames[0] },
        enabled = enabled,
        onEnabledChange = viewModel::setAnalogXEnabled,
        icon = Icons.Default.Memory,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> viewModel.applyPref(Effects.analogX.mode, index) },
        )
    }
}

@Composable
fun SpeakerOptSection(
    state: EffectState,
    viewModel: MainViewModel,
) {
    ViperEffectCard(
        title = stringResource(R.string.section_speaker_optimization),
        summary =
            stringResource(
                if (state.speakerCorrection.enable) R.string.status_active else R.string.status_inactive,
            ),
        enabled = state.speakerCorrection.enable,
        onEnabledChange = viewModel::setSpeakerCorrectionEnabled,
        icon = Icons.Default.SpeakerPhone,
        toggleOnly = true,
    ) {}
}

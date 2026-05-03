package com.llsl.viper4android.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llsl.viper4android.R
import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.ui.components.EffectSection
import com.llsl.viper4android.ui.components.EqCurveGraph
import com.llsl.viper4android.ui.components.EqEditDialog
import com.llsl.viper4android.ui.components.LabeledDropdown
import com.llsl.viper4android.ui.components.LabeledSlider
import com.llsl.viper4android.ui.components.LabeledSwitch
import kotlin.math.log10
import kotlin.math.roundToInt

@Composable
fun MasterLimiterSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.out.forType(fxType)
    val outputVolume = vals.volume
    val limiter = vals.limiter
    val onOutputVolumeChange = viewModel::setOutputVolume
    val onLimiterChange = viewModel::setLimiter

    val masterEnabled = if (isSpkMode) state.spkMasterEnabled else state.masterEnabled
    val onMasterEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkMasterEnabled else viewModel::setMasterEnabled

    EffectSection(
        title = stringResource(R.string.section_output),
        enabled = masterEnabled,
        onEnabledChange = onMasterEnabledChange,
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        initiallyExpanded = true
    ) {
        val gainPct = MainViewModel.OUTPUT_VOLUME_VALUES.getOrElse(outputVolume) { 100 }
        val gainDb = if (gainPct > 0) 20.0 * log10(gainPct / 100.0) else -99.9
        LabeledSlider(
            label = stringResource(R.string.label_volume),
            value = outputVolume.toFloat(),
            onValueChange = { onOutputVolumeChange(it.roundToInt()) },
            valueRange = 0f..21f,
            steps = 20,
            valueLabel = "${"%.1f".format(gainDb)}dB"
        )
        if (!isSpkMode) {
            val channelPan = vals.channelPan
            val left = 50 - channelPan / 2
            val right = 50 + channelPan / 2
            LabeledSlider(
                label = stringResource(R.string.label_pan),
                value = channelPan.toFloat(),
                onValueChange = { viewModel.setChannelPan(it.roundToInt()) },
                valueRange = -100f..100f,
                valueLabel = "${left}:${right}"
            )
        }
        val limPct = MainViewModel.OUTPUT_DB_VALUES.getOrElse(limiter) { 100 }
        val limDb = if (limPct > 0) 20.0 * log10(limPct / 100.0) else -99.9
        LabeledSlider(
            label = stringResource(R.string.label_limiter),
            value = limiter.toFloat(),
            onValueChange = { onLimiterChange(it.roundToInt()) },
            valueRange = 0f..5f,
            steps = 4,
            valueLabel = "${"%.1f".format(limDb)}dB"
        )
    }
}

@Composable
fun PlaybackGainSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.agc.forType(fxType)
    val enabled = vals.enabled
    val strength = vals.strength
    val maxGain = vals.maxGain
    val threshold = vals.outputThreshold
    val onEnabledChange = viewModel::setAgcEnabled
    val onStrengthChange = viewModel::setAgcStrength
    val onMaxGainChange = viewModel::setAgcMaxGain
    val onThresholdChange = viewModel::setAgcOutputThreshold

    EffectSection(
        title = stringResource(R.string.section_agc),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.AutoMirrored.Filled.TrendingUp
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_agc_strength),
            value = strength.toFloat(),
            onValueChange = { onStrengthChange(it.roundToInt()) },
            valueRange = 0f..2f,
            steps = 1
        )
        LabeledSlider(
            label = stringResource(R.string.label_agc_max_gain),
            value = maxGain.toFloat(),
            onValueChange = { onMaxGainChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
        val threshPct = MainViewModel.OUTPUT_DB_VALUES.getOrElse(threshold) { 100 }
        val threshDb = if (threshPct > 0) 20.0 * log10(threshPct / 100.0) else -99.9
        LabeledSlider(
            label = stringResource(R.string.label_agc_output_threshold),
            value = threshold.toFloat(),
            onValueChange = { onThresholdChange(it.roundToInt()) },
            valueRange = 0f..5f,
            steps = 4,
            valueLabel = "${"%.1f".format(threshDb)}dB"
        )
    }
}

@Composable
fun FetCompressorSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.fet.forType(fxType)
    val enabled = vals.enabled
    val threshold = vals.threshold
    val ratio = vals.ratio
    val autoKnee = vals.autoKnee
    val knee = vals.knee
    val kneeMulti = vals.kneeMulti
    val autoGain = vals.autoGain
    val gain = vals.gain
    val autoAttack = vals.autoAttack
    val attack = vals.attack
    val maxAttack = vals.maxAttack
    val autoRelease = vals.autoRelease
    val release = vals.release
    val maxRelease = vals.maxRelease
    val crest = vals.crest
    val adapt = vals.adapt
    val noClip = vals.noClip

    val onEnabledChange = viewModel::setFetEnabled
    val onThresholdChange = viewModel::setFetThreshold
    val onRatioChange = viewModel::setFetRatio
    val onAutoKneeChange = viewModel::setFetAutoKnee
    val onKneeChange = viewModel::setFetKnee
    val onKneeMultiChange = viewModel::setFetKneeMulti
    val onAutoGainChange = viewModel::setFetAutoGain
    val onGainChange = viewModel::setFetGain
    val onAutoAttackChange = viewModel::setFetAutoAttack
    val onAttackChange = viewModel::setFetAttack
    val onMaxAttackChange = viewModel::setFetMaxAttack
    val onAutoReleaseChange = viewModel::setFetAutoRelease
    val onReleaseChange = viewModel::setFetRelease
    val onMaxReleaseChange = viewModel::setFetMaxRelease
    val onCrestChange = viewModel::setFetCrest
    val onAdaptChange = viewModel::setFetAdapt
    val onNoClipChange = viewModel::setFetNoClip

    EffectSection(
        title = stringResource(R.string.section_fet_compressor),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Compress
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_fet_threshold),
            value = threshold.toFloat(),
            onValueChange = { onThresholdChange(it.roundToInt()) },
            valueRange = 0f..200f
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_ratio),
            value = ratio.toFloat(),
            onValueChange = { onRatioChange(it.roundToInt()) },
            valueRange = 0f..200f
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_knee),
            checked = autoKnee,
            onCheckedChange = onAutoKneeChange
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee),
            value = knee.toFloat(),
            onValueChange = { onKneeChange(it.roundToInt()) },
            valueRange = 0f..200f,
            enabled = !autoKnee
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee_multi),
            value = kneeMulti.toFloat(),
            onValueChange = { onKneeMultiChange(it.roundToInt()) },
            valueRange = 0f..200f
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_gain),
            checked = autoGain,
            onCheckedChange = onAutoGainChange
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_gain),
            value = gain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 0f..200f,
            enabled = !autoGain
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_attack),
            checked = autoAttack,
            onCheckedChange = onAutoAttackChange
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_attack),
            value = attack.toFloat(),
            onValueChange = { onAttackChange(it.roundToInt()) },
            valueRange = 0f..200f,
            enabled = !autoAttack
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_attack),
            value = maxAttack.toFloat(),
            onValueChange = { onMaxAttackChange(it.roundToInt()) },
            valueRange = 0f..200f
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_release),
            checked = autoRelease,
            onCheckedChange = onAutoReleaseChange
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_release),
            value = release.toFloat(),
            onValueChange = { onReleaseChange(it.roundToInt()) },
            valueRange = 0f..200f,
            enabled = !autoRelease
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_release),
            value = maxRelease.toFloat(),
            onValueChange = { onMaxReleaseChange(it.roundToInt()) },
            valueRange = 0f..200f
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_crest),
            value = crest.toFloat(),
            onValueChange = { onCrestChange(it.roundToInt()) },
            valueRange = 0f..300f
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_adapt),
            value = adapt.toFloat(),
            onValueChange = { onAdaptChange(it.roundToInt()) },
            valueRange = 0f..200f
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_no_clip),
            checked = noClip,
            onCheckedChange = onNoClipChange
        )
    }
}

@Composable
fun DdcSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.ddc.forType(fxType)
    val enabled = vals.enabled
    val device = vals.device
    val onEnabledChange = viewModel::setDdcEnabled
    val onDeviceChange = viewModel::setDdcDevice

    val vdcFiles by viewModel.vdcFileList.collectAsStateWithLifecycle()
    val vdcNoneLabel = stringResource(R.string.label_ddc_none)
    val cdvOptions = listOf(vdcNoneLabel) + vdcFiles

    EffectSection(
        title = stringResource(R.string.section_ddc),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Tune
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_ddc_device),
            selectedValue = device.ifEmpty { vdcNoneLabel },
            options = cdvOptions,
            onOptionSelected = { index, value ->
                onDeviceChange(if (index == 0) "" else value)
            },
            onDeleteOption = { _, name -> viewModel.deleteVdcFile(name) }
        )
    }
}

@Composable
fun SpectrumExtensionSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.vse.forType(fxType)
    val enabled = vals.enabled
    val strength = vals.strength
    val exciter = vals.exciter
    val onEnabledChange = viewModel::setVseEnabled
    val onStrengthChange = viewModel::setVseStrength
    val onExciterChange = viewModel::setVseExciter

    EffectSection(
        title = stringResource(R.string.section_spectrum_extension),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Waves
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_vse_strength),
            value = strength.toFloat(),
            onValueChange = { onStrengthChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            valueLabel = "$strength"
        )
        LabeledSlider(
            label = stringResource(R.string.label_vse_exciter),
            value = exciter.toFloat(),
            onValueChange = { onExciterChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "${exciter}%"
        )
    }
}

@Composable
fun EqualizerSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val eqVals = state.eq.forType(fxType)
    val enabled = eqVals.enabled
    val bandCount = eqVals.bandCount
    val presetId = eqVals.presetId
    val eqBands = eqVals.bands
    val eqPresets = eqVals.presets
    val onEnabledChange = viewModel::setEqEnabled
    val onBandCountChange = viewModel::setEqBandCount
    val onPresetSelect = viewModel::setEqPreset
    val onBandsChange = viewModel::setEqBands
    val onPresetAdd = viewModel::addEqPreset
    val onPresetDelete = viewModel::deleteEqPreset
    val onReset = viewModel::resetEqBands

    val bands = remember(eqBands) {
        eqBands.split(";").mapNotNull { it.toFloatOrNull() }
    }

    EffectSection(
        title = stringResource(R.string.section_equalizer),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Equalizer
    ) {
        var showEqDialog by remember { mutableStateOf(false) }

        val bandCounts = listOf(10, 15, 25, 31)
        val bandCountOptions = bandCounts.map { stringResource(R.string.label_eq_n_bands, it) }
        val bandCountIndex = when (bandCount) {
            15 -> 1; 25 -> 2; 31 -> 3; else -> 0
        }
        LabeledDropdown(
            label = stringResource(R.string.label_eq_bands),
            selectedValue = bandCountOptions[bandCountIndex],
            options = bandCountOptions,
            onOptionSelected = { index, _ -> onBandCountChange(bandCounts[index]) }
        )

        if (bands.size >= bandCount) {
            EqCurveGraph(
                bands = bands,
                onClick = { showEqDialog = true },
                bandCount = bandCount
            )
        }

        if (showEqDialog) {
            EqEditDialog(
                bands = bands,
                onBandsChange = onBandsChange,
                presetId = presetId,
                presets = eqPresets,
                onPresetSelect = onPresetSelect,
                onPresetAdd = onPresetAdd,
                onPresetDelete = onPresetDelete,
                onReset = onReset,
                onDismiss = { showEqDialog = false },
                bandCount = bandCount
            )
        }
    }
}

@Composable
fun ConvolverSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.convolver.forType(fxType)
    val enabled = vals.enabled
    val kernel = vals.kernel
    val crossChannel = vals.crossChannel
    val onEnabledChange = viewModel::setConvolverEnabled
    val onKernelChange = viewModel::setConvolverKernel
    val onCrossChannelChange = viewModel::setConvolverCrossChannel

    val kernelFiles by viewModel.kernelFileList.collectAsStateWithLifecycle()
    val kernelNoneLabel = stringResource(R.string.label_convolver_none)
    val kernelOptions = listOf(kernelNoneLabel) + kernelFiles

    EffectSection(
        title = stringResource(R.string.section_convolver),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.GraphicEq
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_convolver_kernel),
            selectedValue = kernel.ifEmpty { kernelNoneLabel },
            options = kernelOptions,
            onOptionSelected = { index, value ->
                onKernelChange(if (index == 0) "" else value)
            },
            onDeleteOption = { _, name -> viewModel.deleteKernelFile(name) }
        )
        LabeledSlider(
            label = stringResource(R.string.label_convolver_cross_channel),
            value = crossChannel.toFloat(),
            onValueChange = { onCrossChannelChange(it.roundToInt()) },
            valueRange = 0f..100f
        )
    }
}

@Composable
fun FieldSurroundSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.fieldSurround.forType(fxType)
    val enabled = vals.enabled
    val widening = vals.widening
    val midImage = vals.midImage
    val depth = vals.depth
    val onEnabledChange = viewModel::setFieldSurroundEnabled
    val onWideningChange = viewModel::setFieldSurroundWidening
    val onMidImageChange = viewModel::setFieldSurroundMidImage
    val onDepthChange = viewModel::setFieldSurroundDepth

    EffectSection(
        title = stringResource(R.string.section_field_surround),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.SurroundSound
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_fs_widening),
            value = widening.toFloat(),
            onValueChange = { onWideningChange(it.roundToInt()) },
            valueRange = 0f..8f,
            steps = 7
        )
        LabeledSlider(
            label = stringResource(R.string.label_fs_mid_image),
            value = midImage.toFloat(),
            onValueChange = { onMidImageChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
        LabeledSlider(
            label = stringResource(R.string.label_fs_depth),
            value = depth.toFloat(),
            onValueChange = { onDepthChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
    }
}

@Composable
fun DiffSurroundSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.diffSurround.forType(fxType)
    val enabled = vals.enabled
    val delay = vals.delay
    val reverse = vals.reverse
    val onEnabledChange = viewModel::setDiffSurroundEnabled
    val onDelayChange = viewModel::setDiffSurroundDelay
    val onReverseChange = viewModel::setDiffSurroundReverse

    val delayValue = MainViewModel.DIFF_SURROUND_DELAY_VALUES.getOrElse(delay) { 500 }

    EffectSection(
        title = stringResource(R.string.section_differential_surround),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.SpatialAudio
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_ds_delay),
            value = delay.toFloat(),
            onValueChange = { onDelayChange(it.roundToInt()) },
            valueRange = 0f..19f,
            steps = 18,
            valueLabel = "${delayValue / 100}ms"
        )
        LabeledSwitch(
            label = stringResource(R.string.label_diff_surround_reverse),
            checked = reverse,
            onCheckedChange = onReverseChange
        )
    }
}

@Composable
fun HeadphoneSurroundSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.vhe.forType(fxType)
    val enabled = vals.enabled
    val quality = vals.quality
    val onEnabledChange = viewModel::setVheEnabled
    val onQualityChange = viewModel::setVheQuality

    EffectSection(
        title = stringResource(R.string.section_headphone_surround),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Headphones
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_vhe_quality),
            value = quality.toFloat(),
            onValueChange = { onQualityChange(it.roundToInt()) },
            valueRange = 0f..4f,
            steps = 3
        )
    }
}

@Composable
fun ReverberationSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.reverb.forType(fxType)
    val enabled = vals.enabled
    val roomSize = vals.roomSize
    val width = vals.width
    val dampening = vals.dampening
    val wet = vals.wet
    val dry = vals.dry
    val onEnabledChange = viewModel::setReverbEnabled
    val onRoomSizeChange = viewModel::setReverbRoomSize
    val onWidthChange = viewModel::setReverbWidth
    val onDampeningChange = viewModel::setReverbDampening
    val onWetChange = viewModel::setReverbWet
    val onDryChange = viewModel::setReverbDry

    EffectSection(
        title = stringResource(R.string.section_reverberation),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.BlurOn
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_reverb_room_size),
            value = roomSize.toFloat(),
            onValueChange = { onRoomSizeChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_width),
            value = width.toFloat(),
            onValueChange = { onWidthChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_dampening),
            value = dampening.toFloat(),
            onValueChange = { onDampeningChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_wet),
            value = wet.toFloat(),
            onValueChange = { onWetChange(it.roundToInt()) },
            valueRange = 0f..100f
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_dry),
            value = dry.toFloat(),
            onValueChange = { onDryChange(it.roundToInt()) },
            valueRange = 0f..100f
        )
    }
}

@Composable
fun DynamicSystemSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.dynamicSystem.forType(fxType)
    val enabled = vals.enabled
    val strength = vals.strength
    val dsPresetId = vals.presetId
    val dsPresets = vals.presets
    val xLow = vals.xLow
    val xHigh = vals.xHigh
    val yLow = vals.yLow
    val yHigh = vals.yHigh
    val sideGainLow = vals.sideGainLow
    val sideGainHigh = vals.sideGainHigh
    val onEnabledChange = viewModel::setDynamicSystemEnabled
    val onStrengthChange = viewModel::setDynamicSystemStrength
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

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    EffectSection(
        title = stringResource(R.string.section_dynamic_system),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Speaker
    ) {
        val presetName =
            dsPresets.find { it.id == dsPresetId }?.name ?: stringResource(R.string.label_ds_custom)
        LabeledDropdown(
            label = stringResource(R.string.label_ds_preset),
            selectedValue = presetName,
            options = dsPresets.map { it.name },
            onOptionSelected = { index, _ -> onPresetSelect(dsPresets[index].id) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { showSaveDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_save))
            }
            TextButton(
                onClick = { dsPresetId?.let { onPresetDelete(it) } },
                enabled = dsPresetId != null
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_delete))
            }
            TextButton(onClick = onReset) {
                Icon(
                    Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.action_reset))
            }
        }

        LabeledSlider(
            label = stringResource(R.string.label_ds_strength),
            value = strength.toFloat(),
            onValueChange = { onStrengthChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "${strength}%"
        )

        LabeledSlider(
            label = stringResource(R.string.label_ds_x_low_freq),
            value = xLow.toFloat(),
            onValueChange = { onXLowChange((it / 100f).roundToInt() * 100) },
            valueRange = 0f..2400f,
            steps = 23,
            valueLabel = "$xLow Hz"
        )

        LabeledSlider(
            label = stringResource(R.string.label_ds_x_high_freq),
            value = xHigh.toFloat(),
            onValueChange = { onXHighChange((it / 100f).roundToInt() * 100) },
            valueRange = 0f..12000f,
            steps = 119,
            valueLabel = "$xHigh Hz"
        )

        LabeledSlider(
            label = stringResource(R.string.label_ds_y_low_freq),
            value = yLow.toFloat(),
            onValueChange = { onYLowChange(it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$yLow Hz"
        )

        LabeledSlider(
            label = stringResource(R.string.label_ds_y_high_freq),
            value = yHigh.toFloat(),
            onValueChange = { onYHighChange(it.roundToInt()) },
            valueRange = 0f..300f,
            valueLabel = "$yHigh Hz"
        )

        LabeledSlider(
            label = stringResource(R.string.label_ds_side_gain_low),
            value = sideGainLow.toFloat(),
            onValueChange = { onSideGainLowChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "${sideGainLow}%"
        )

        LabeledSlider(
            label = stringResource(R.string.label_ds_side_gain_high),
            value = sideGainHigh.toFloat(),
            onValueChange = { onSideGainHighChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "${sideGainHigh}%"
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(R.string.preset_save_title)) },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    label = { Text(stringResource(R.string.preset_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (presetNameInput.isNotBlank()) {
                            onPresetAdd(presetNameInput.trim())
                            presetNameInput = ""
                            showSaveDialog = false
                        }
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun TubeSimulatorSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.tube.forType(fxType)
    val enabled = vals.enabled
    val onEnabledChange = viewModel::setTubeSimulatorEnabled

    EffectSection(
        title = stringResource(R.string.section_tube_simulator),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.MusicNote,
        toggleOnly = true
    ) {}
}

@Composable
fun ViperBassSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.bass.forType(fxType)
    val enabled = vals.enabled
    val mode = vals.mode
    val frequency = vals.frequency
    val gain = vals.gain
    val antiPop = vals.antiPop
    val onEnabledChange = viewModel::setBassEnabled
    val onModeChange = viewModel::setBassMode
    val onFrequencyChange = viewModel::setBassFrequency
    val onGainChange = viewModel::setBassGain
    val onAntiPopChange = viewModel::setBassAntiPop

    val modeNames = listOf(
        stringResource(R.string.bass_mode_natural),
        stringResource(R.string.bass_mode_pure),
        stringResource(R.string.bass_mode_subwoofer)
    )

    EffectSection(
        title = stringResource(R.string.section_viper_bass),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.GraphicEq
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_bass_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> onModeChange(index) }
        )
        if (mode != 2) {
            LabeledSlider(
                label = stringResource(R.string.label_bass_frequency),
                value = frequency.toFloat(),
                onValueChange = { onFrequencyChange(it.roundToInt()) },
                valueRange = 0f..135f,
                valueLabel = "${frequency + 15}Hz"
            )
        }
        LabeledSlider(
            label = stringResource(R.string.label_bass_gain),
            value = gain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 0f..19f,
            steps = 18,
            valueLabel = "${
                if (mode == 2) {
                    MainViewModel.BASS_SUBWOOFER_GAIN_DB_LABELS.getOrElse(gain) { "--" }
                } else {
                    MainViewModel.BASS_GAIN_DB_LABELS.getOrElse(gain) { "--" }
                }
            }dB"
        )
        LabeledSwitch(
            label = stringResource(R.string.label_bass_anti_pop),
            checked = antiPop,
            onCheckedChange = onAntiPopChange
        )
    }
}

@Composable
fun ViperBassMonoSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.bassMono.forType(fxType)
    val enabled = vals.enabled
    val mode = vals.mode
    val frequency = vals.frequency
    val gain = vals.gain
    val antiPop = vals.antiPop
    val onEnabledChange = viewModel::setBassMonoEnabled
    val onModeChange = viewModel::setBassMonoMode
    val onFrequencyChange = viewModel::setBassMonoFrequency
    val onGainChange = viewModel::setBassMonoGain
    val onAntiPopChange = viewModel::setBassMonoAntiPop

    val modeNames = listOf(
        stringResource(R.string.bass_mode_natural),
        stringResource(R.string.bass_mode_pure),
        stringResource(R.string.bass_mode_subwoofer)
    )

    EffectSection(
        title = stringResource(R.string.section_viper_bass_mono),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.GraphicEq
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_bass_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> onModeChange(index) }
        )
        if (mode != 2) {
            LabeledSlider(
                label = stringResource(R.string.label_bass_frequency),
                value = frequency.toFloat(),
                onValueChange = { onFrequencyChange(it.roundToInt()) },
                valueRange = 0f..135f,
                valueLabel = "${frequency + 15}Hz"
            )
        }
        LabeledSlider(
            label = stringResource(R.string.label_bass_gain),
            value = gain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 0f..19f,
            steps = 18,
            valueLabel = "${
                if (mode == 2) {
                    MainViewModel.BASS_SUBWOOFER_GAIN_DB_LABELS.getOrElse(gain) { "--" }
                } else {
                    MainViewModel.BASS_GAIN_DB_LABELS.getOrElse(gain) { "--" }
                }
            }dB"
        )
        LabeledSwitch(
            label = stringResource(R.string.label_bass_anti_pop),
            checked = antiPop,
            onCheckedChange = onAntiPopChange
        )
    }
}

@Composable
fun ViperClaritySection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.clarity.forType(fxType)
    val enabled = vals.enabled
    val mode = vals.mode
    val gain = vals.gain
    val onEnabledChange = viewModel::setClarityEnabled
    val onModeChange = viewModel::setClarityMode
    val onGainChange = viewModel::setClarityGain

    val modeNames = listOf(
        stringResource(R.string.clarity_mode_natural),
        stringResource(R.string.clarity_mode_ozone),
        stringResource(R.string.clarity_mode_xhifi)
    )

    EffectSection(
        title = stringResource(R.string.section_viper_clarity),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Hearing
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_clarity_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> onModeChange(index) }
        )
        LabeledSlider(
            label = stringResource(R.string.label_clarity_gain),
            value = gain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 0f..9f,
            steps = 8,
            valueLabel = "${
                MainViewModel.CLARITY_GAIN_DB_LABELS.getOrElse(gain) {
                    "%.1f".format(
                        gain * 0.5
                    )
                }
            }dB"
        )
    }
}

@Composable
fun AuditoryProtectionSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.cure.forType(fxType)
    val enabled = vals.enabled
    val strength = vals.strength
    val onEnabledChange = viewModel::setCureEnabled
    val onStrengthChange = viewModel::setCureStrength

    val strengthNames = listOf(
        stringResource(R.string.cure_level_mild),
        stringResource(R.string.cure_level_medium),
        stringResource(R.string.cure_level_strong)
    )

    EffectSection(
        title = stringResource(R.string.section_cure),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.HealthAndSafety
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_cure_strength),
            selectedValue = strengthNames.getOrElse(strength) { strengthNames[0] },
            options = strengthNames,
            onOptionSelected = { index, _ -> onStrengthChange(index) }
        )
    }
}

@Composable
fun AnalogXSection(state: MainUiState, viewModel: MainViewModel, isSpkMode: Boolean = false) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.analog.forType(fxType)
    val enabled = vals.enabled
    val mode = vals.mode
    val onEnabledChange = viewModel::setAnalogXEnabled
    val onModeChange = viewModel::setAnalogXMode

    val modeNames = listOf(
        stringResource(R.string.analogx_mode_mild),
        stringResource(R.string.analogx_mode_medium),
        stringResource(R.string.analogx_mode_strong)
    )

    EffectSection(
        title = stringResource(R.string.section_analogx),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Memory
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_analogx_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> onModeChange(index) }
        )
    }
}

@Composable
fun SpeakerOptSection(state: MainUiState, viewModel: MainViewModel) {
    EffectSection(
        title = stringResource(R.string.section_speaker_optimization),
        enabled = state.speakerCorrection.spk.enabled,
        onEnabledChange = viewModel::setSpeakerOptEnabled,
        icon = Icons.Default.Speaker,
        toggleOnly = true
    ) {}
}

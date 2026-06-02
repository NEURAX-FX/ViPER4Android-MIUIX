package com.llsl.viper4android.ui.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Psychology
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
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
import com.llsl.viper4android.ui.components.resolvePresetName
import com.llsl.viper4android.ui.components.viper.ViperDialog
import com.llsl.viper4android.ui.components.viper.ViperTabs
import com.llsl.viper4android.ui.components.viper.ViperTextFieldDialog
import java.util.Locale
import kotlin.math.log10
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MasterLimiterSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.out.forType(fxType)
    val outputVolume = vals.volume
    val channelPan = vals.channelPan
    val limiter = vals.limiter
    val masterEnabled = if (isSpkMode) state.spkMasterEnabled else state.masterEnabled

    val onMasterEnabledChange: (Boolean) -> Unit =
        if (isSpkMode) viewModel::setSpkMasterEnabled else viewModel::setMasterEnabled
    val onOutputVolumeChange = viewModel::setOutputVolume
    val onChannelPanChange = viewModel::setChannelPan
    val onLimiterChange = viewModel::setLimiter

    EffectSection(
        title = stringResource(R.string.section_output),
        enabled = masterEnabled,
        onEnabledChange = onMasterEnabledChange,
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        initiallyExpanded = true,
    ) {
        val gainDb = if (outputVolume > 0) 20.0 * log10(outputVolume / 100.0) else -99.9
        LabeledSlider(
            label = stringResource(R.string.label_output_volume),
            value = outputVolume.toFloat(),
            onValueChange = { onOutputVolumeChange(it.roundToInt()) },
            valueRange = 1f..200f,
            valueLabel = "${"%.1f".format(gainDb)}dB",
        )
        if (!isSpkMode) {
            val left = 50 - channelPan / 2
            val right = 50 + channelPan / 2
            LabeledSlider(
                label = stringResource(R.string.label_output_pan),
                value = channelPan.toFloat(),
                onValueChange = { onChannelPanChange(it.roundToInt()) },
                valueRange = -100f..100f,
                valueLabel = "$left:$right",
            )
        }
        val limDb = if (limiter > 0) 20.0 * log10(limiter / 100.0) else -99.9
        LabeledSlider(
            label = stringResource(R.string.label_output_limiter),
            value = limiter.toFloat(),
            onValueChange = { onLimiterChange(it.roundToInt()) },
            valueRange = 30f..100f,
            valueLabel = "${"%.1f".format(limDb)}dB",
        )
    }
}

@Composable
fun PlaybackGainSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
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
        icon = Icons.AutoMirrored.Filled.TrendingUp,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_strength),
            value = strength.toFloat(),
            onValueChange = { onStrengthChange(it.roundToInt()) },
            valueRange = 50f..300f,
            valueLabel = "${"%.1f".format(strength / 100.0)}x",
        )
        LabeledSlider(
            label = stringResource(R.string.label_max_gain),
            value = maxGain.toFloat(),
            onValueChange = { onMaxGainChange(it.roundToInt()) },
            valueRange = 100f..1000f,
            valueLabel = "${"%.1f".format(maxGain / 100.0)}x",
        )
        val threshDb = if (threshold > 0) 20.0 * log10(threshold / 100.0) else -99.9
        LabeledSlider(
            label = stringResource(R.string.label_agc_output_threshold),
            value = threshold.toFloat(),
            onValueChange = { onThresholdChange(it.roundToInt()) },
            valueRange = 30f..100f,
            valueLabel = "${"%.1f".format(threshDb)}dB",
        )
    }
}

@Composable
fun LUFSTargetingSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.lufs.forType(fxType)
    val enabled = vals.enabled
    val target = vals.target
    val maxGain = vals.maxGain
    val speed = vals.speed

    val onEnabledChange = viewModel::setLufsEnabled
    val onTargetChange = viewModel::setLufsTarget
    val onMaxGainChange = viewModel::setLufsMaxGain
    val onSpeedChange = viewModel::setLufsSpeed

    val speedNames =
        listOf(
            stringResource(R.string.label_lufs_speed_slow),
            stringResource(R.string.label_lufs_speed_medium),
            stringResource(R.string.label_lufs_speed_fast),
        )

    EffectSection(
        title = stringResource(R.string.section_lufs_targeting),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.CrisisAlert,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_lufs_target_lufs),
            value = target.toFloat(),
            onValueChange = { onTargetChange(it.roundToInt()) },
            valueRange = 80f..240f,
            valueLabel = String.format(Locale.US, "%.1f LUFS", target / -10f),
        )
        LabeledSlider(
            label = stringResource(R.string.label_max_gain),
            value = maxGain.toFloat(),
            onValueChange = { onMaxGainChange(it.roundToInt()) },
            valueRange = 0f..120f,
            valueLabel = String.format(Locale.US, "%.1f dB", maxGain / 10f),
        )
        LabeledSlider(
            label = stringResource(R.string.label_lufs_speed),
            value = speed.toFloat(),
            onValueChange = { onSpeedChange(it.roundToInt()) },
            valueRange = 0f..2f,
            steps = 1,
            valueLabel = speedNames.getOrElse(speed) { speedNames[1] },
        )
    }
}

@Composable
fun FetCompressorSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
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
        icon = Icons.Default.VerticalAlignCenter,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_threshold),
            value = threshold.toFloat(),
            onValueChange = { onThresholdChange(it.roundToInt()) },
            valueRange = -48f..0f,
            valueLabel = "$threshold dB",
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_ratio),
            value = ratio / 100f,
            onValueChange = { onRatioChange((it * 100f).roundToInt()) },
            valueRange = 0f..2f,
            valueLabel = String.format(Locale.US, "%.1f", ratio / 100.0),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_knee),
            checked = autoKnee,
            onCheckedChange = onAutoKneeChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee),
            value = knee.toFloat(),
            onValueChange = { onKneeChange(it.roundToInt()) },
            valueRange = 0f..12f,
            enabled = !autoKnee,
            valueLabel = "$knee dB",
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee_multi),
            value = (kneeMulti / 100f * 4f),
            onValueChange = { onKneeMultiChange((it / 4f * 100f).roundToInt()) },
            valueRange = 0f..4f,
            valueLabel = String.format(Locale.US, "%.1fx", kneeMulti / 100.0 * 4.0),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_gain),
            checked = autoGain,
            onCheckedChange = onAutoGainChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 0f..24f,
            enabled = !autoGain,
            valueLabel = "$gain dB",
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_attack),
            checked = autoAttack,
            onCheckedChange = onAutoAttackChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_attack),
            value = attack.toFloat().coerceIn(1f, 100f),
            onValueChange = { onAttackChange(it.roundToInt()) },
            valueRange = 1f..100f,
            enabled = !autoAttack,
            valueLabel = "$attack ms",
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_attack),
            value = maxAttack.toFloat().coerceIn(1f, 100f),
            onValueChange = { onMaxAttackChange(it.roundToInt()) },
            valueRange = 1f..100f,
            valueLabel = "$maxAttack ms",
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_release),
            checked = autoRelease,
            onCheckedChange = onAutoReleaseChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_release),
            value = release.toFloat().coerceIn(5f, 500f),
            onValueChange = { onReleaseChange(it.roundToInt()) },
            valueRange = 5f..500f,
            enabled = !autoRelease,
            valueLabel = "$release ms",
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_release),
            value = maxRelease.toFloat().coerceIn(5f, 500f),
            onValueChange = { onMaxReleaseChange(it.roundToInt()) },
            valueRange = 5f..500f,
            valueLabel = "$maxRelease ms",
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_crest),
            value = crest.toFloat().coerceIn(5f, 300f),
            onValueChange = { onCrestChange(it.roundToInt()) },
            valueRange = 5f..300f,
            valueLabel = "$crest ms",
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_adapt),
            value = adapt.toFloat(),
            onValueChange = { onAdaptChange(it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$adapt%",
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_no_clip),
            checked = noClip,
            onCheckedChange = onNoClipChange,
        )
    }
}

@Composable
fun MultibandCompressorSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    fun parseInts(
        s: String,
        def: Int,
    ) = s.split(";").map { it.toIntOrNull() ?: def }

    fun parseBools(s: String) = s.split(";").map { (it.toIntOrNull() ?: 1) != 0 }

    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val mbcVals = state.mbc.forType(fxType)
    val enabled = mbcVals.enabled
    val onEnabledChange = viewModel::setMbcEnabled

    val crossoverDefaults = listOf(120, 500, 4000, 8000)
    val bandEnables = parseBools(mbcVals.bandEnables)
    val crossovers =
        mbcVals.crossovers
            .split(";")
            .mapIndexed { i, v -> v.toIntOrNull() ?: crossoverDefaults.getOrElse(i) { 0 } }

    val thresholds = parseInts(mbcVals.thresholds, -18)
    val ratios = parseInts(mbcVals.ratios, 50)
    val gains = parseInts(mbcVals.gains, 24)
    val knees = parseInts(mbcVals.knees, 0)
    val kneeMultis = parseInts(mbcVals.kneeMultis, 0)
    val attacks = parseInts(mbcVals.attacks, 1)
    val maxAttacks = parseInts(mbcVals.maxAttacks, 44)
    val releases = parseInts(mbcVals.releases, 100)
    val maxReleases = parseInts(mbcVals.maxReleases, 200)
    val crests = parseInts(mbcVals.crests, 100)
    val adapts = parseInts(mbcVals.adapts, 50)
    val autoKnees = parseBools(mbcVals.autoKnees)
    val autoGains = parseBools(mbcVals.autoGains)
    val autoAttacks = parseBools(mbcVals.autoAttacks)
    val autoReleases = parseBools(mbcVals.autoReleases)
    val noClips = parseBools(mbcVals.noClips)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabNames = listOf("Sub", "Low", "Mid", "Pres", "Air")
    val b = selectedTab

    val threshold = thresholds.getOrElse(b) { -18 }
    val ratio = ratios.getOrElse(b) { 50 }
    val gain = gains.getOrElse(b) { 24 }
    val knee = knees.getOrElse(b) { 0 }
    val kneeMulti = kneeMultis.getOrElse(b) { 0 }
    val attack = attacks.getOrElse(b) { 1 }
    val maxAttack = maxAttacks.getOrElse(b) { 44 }
    val release = releases.getOrElse(b) { 100 }
    val maxRelease = maxReleases.getOrElse(b) { 200 }
    val crest = crests.getOrElse(b) { 100 }
    val adapt = adapts.getOrElse(b) { 50 }
    val bandEnabled = bandEnables.getOrElse(b) { true }
    val autoKnee = autoKnees.getOrElse(b) { true }
    val autoGain = autoGains.getOrElse(b) { true }
    val autoAttack = autoAttacks.getOrElse(b) { true }
    val autoRelease = autoReleases.getOrElse(b) { true }
    val noClip = noClips.getOrElse(b) { true }

    val onBandEnableChange: (Boolean) -> Unit = { viewModel.setMbcBandEnable(b, it) }
    val onCrossoverChange: (Int) -> Unit = { viewModel.setMbcCrossover(b, it) }
    val onThresholdChange: (Int) -> Unit = { viewModel.setMbcBandThreshold(b, it) }
    val onRatioChange: (Int) -> Unit = { viewModel.setMbcBandRatio(b, it) }
    val onAutoKneeChange: (Boolean) -> Unit = { viewModel.setMbcBandAutoKnee(b, it) }
    val onKneeChange: (Int) -> Unit = { viewModel.setMbcBandKnee(b, it) }
    val onKneeMultiChange: (Int) -> Unit = { viewModel.setMbcBandKneeMulti(b, it) }
    val onAutoGainChange: (Boolean) -> Unit = { viewModel.setMbcBandAutoGain(b, it) }
    val onGainChange: (Int) -> Unit = { viewModel.setMbcBandGain(b, it) }
    val onAutoAttackChange: (Boolean) -> Unit = { viewModel.setMbcBandAutoAttack(b, it) }
    val onAttackChange: (Int) -> Unit = { viewModel.setMbcBandAttack(b, it) }
    val onMaxAttackChange: (Int) -> Unit = { viewModel.setMbcBandMaxAttack(b, it) }
    val onAutoReleaseChange: (Boolean) -> Unit = { viewModel.setMbcBandAutoRelease(b, it) }
    val onReleaseChange: (Int) -> Unit = { viewModel.setMbcBandRelease(b, it) }
    val onMaxReleaseChange: (Int) -> Unit = { viewModel.setMbcBandMaxRelease(b, it) }
    val onCrestChange: (Int) -> Unit = { viewModel.setMbcBandCrest(b, it) }
    val onAdaptChange: (Int) -> Unit = { viewModel.setMbcBandAdapt(b, it) }
    val onNoClipChange: (Boolean) -> Unit = { viewModel.setMbcBandNoClip(b, it) }

    EffectSection(
        title = stringResource(R.string.section_multiband_compressor),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Compress,
    ) {
        ViperTabs(
            tabs = tabNames,
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )

        val lowFreq =
            if (b == 0) 20 else crossovers.getOrElse(b - 1) { crossoverDefaults.getOrElse(b - 1) { 20 } }
        val highFreq =
            if (b < 4) crossovers.getOrElse(b) { crossoverDefaults.getOrElse(b) { 20000 } } else 20000
        Text(
            text = "$lowFreq - ${if (b < 4) "$highFreq" else "20000+"} Hz",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        LabeledSwitch(
            label = stringResource(R.string.label_band_enabled),
            checked = bandEnabled,
            onCheckedChange = onBandEnableChange,
        )

        if (b < 4) {
            val crossoverHardMin = intArrayOf(30, 80, 300, 2000)
            val crossoverHardMax = intArrayOf(300, 2000, 8000, 16000)
            val neighborMin =
                if (b > 0) crossovers.getOrElse(b - 1) { crossoverDefaults.getOrElse(b - 1) { 20 } } + 50 else 30
            val neighborMax =
                if (b < 3) crossovers.getOrElse(b + 1) { crossoverDefaults.getOrElse(b + 1) { 20000 } } - 50 else 16000
            val minCrossover = maxOf(crossoverHardMin[b], neighborMin)
            val maxCrossover = minOf(crossoverHardMax[b], neighborMax)
            LabeledSlider(
                label = stringResource(R.string.label_upper_crossover),
                value =
                    crossovers
                        .getOrElse(b) { crossoverDefaults[b] }
                        .toFloat()
                        .coerceIn(minCrossover.toFloat(), maxCrossover.toFloat()),
                onValueChange = { onCrossoverChange(it.roundToInt()) },
                valueRange = minCrossover.toFloat()..maxCrossover.toFloat(),
                steps = ((maxCrossover - minCrossover) / 5).coerceAtLeast(1) - 1,
                valueLabel = "${crossovers.getOrElse(b) { crossoverDefaults[b] }} Hz",
            )
        }

        LabeledSlider(
            label = stringResource(R.string.label_threshold),
            value = threshold.toFloat(),
            onValueChange = { onThresholdChange(it.roundToInt()) },
            valueRange = -48f..0f,
            enabled = bandEnabled,
            valueLabel = "$threshold dB",
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_ratio),
            value = ratio / 100f,
            onValueChange = { onRatioChange((it * 100f).roundToInt()) },
            valueRange = 0f..2f,
            valueLabel = String.format(Locale.US, "%.1f", ratio / 100.0),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_knee),
            checked = autoKnee,
            onCheckedChange = onAutoKneeChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee),
            value = knee.toFloat(),
            onValueChange = { onKneeChange(it.roundToInt()) },
            valueRange = 0f..12f,
            enabled = !autoKnee,
            valueLabel = "$knee dB",
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_knee_multi),
            value = (kneeMulti / 100f * 4f),
            onValueChange = { onKneeMultiChange((it / 4f * 100f).roundToInt()) },
            valueRange = 0f..4f,
            valueLabel = String.format(Locale.US, "%.1fx", kneeMulti / 100.0 * 4.0),
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_gain),
            checked = autoGain,
            onCheckedChange = onAutoGainChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 0f..24f,
            enabled = !autoGain,
            valueLabel = "$gain dB",
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_attack),
            checked = autoAttack,
            onCheckedChange = onAutoAttackChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_attack),
            value = attack.toFloat().coerceIn(1f, 100f),
            onValueChange = { onAttackChange(it.roundToInt()) },
            valueRange = 1f..100f,
            enabled = !autoAttack,
            valueLabel = "$attack ms",
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_attack),
            value = maxAttack.toFloat().coerceIn(1f, 100f),
            onValueChange = { onMaxAttackChange(it.roundToInt()) },
            valueRange = 1f..100f,
            valueLabel = "$maxAttack ms",
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_auto_release),
            checked = autoRelease,
            onCheckedChange = onAutoReleaseChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_release),
            value = release.toFloat().coerceIn(5f, 500f),
            onValueChange = { onReleaseChange(it.roundToInt()) },
            valueRange = 5f..500f,
            enabled = !autoRelease,
            valueLabel = "$release ms",
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_max_release),
            value = maxRelease.toFloat().coerceIn(5f, 500f),
            onValueChange = { onMaxReleaseChange(it.roundToInt()) },
            valueRange = 5f..500f,
            valueLabel = "$maxRelease ms",
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_crest),
            value = crest.toFloat().coerceIn(5f, 300f),
            onValueChange = { onCrestChange(it.roundToInt()) },
            valueRange = 5f..300f,
            valueLabel = "$crest ms",
        )
        LabeledSlider(
            label = stringResource(R.string.label_fet_adapt),
            value = adapt.toFloat(),
            onValueChange = { onAdaptChange(it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$adapt%",
        )
        LabeledSwitch(
            label = stringResource(R.string.label_fet_no_clip),
            checked = noClip,
            onCheckedChange = onNoClipChange,
        )
    }
}

@Composable
fun DdcSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.ddc.forType(fxType)
    val enabled = vals.enabled
    val device = vals.device

    val vdcFiles by viewModel.vdcFileList.collectAsStateWithLifecycle()
    val vdcNoneLabel = stringResource(R.string.label_none)
    val cdvOptions = listOf(vdcNoneLabel) + vdcFiles

    val onEnabledChange = viewModel::setDdcEnabled
    val onDeviceChange = viewModel::setDdcDevice

    EffectSection(
        title = stringResource(R.string.section_ddc),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.SettingsInputComponent,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_ddc_device),
            selectedValue = device.ifEmpty { vdcNoneLabel },
            options = cdvOptions,
            onOptionSelected = { index, value ->
                onDeviceChange(if (index == 0) "" else value)
            },
            onDeleteOption = { _, name -> viewModel.deleteVdcFile(name) },
        )
    }
}

@Composable
fun SpectrumExtensionSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
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
        icon = Icons.Default.Waves,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_strength),
            value = strength.toFloat(),
            onValueChange = { onStrengthChange(it.roundToInt()) },
            valueRange = 2200f..8200f,
            steps = 1199,
            valueLabel = "$strength Hz",
        )
        LabeledSlider(
            label = stringResource(R.string.label_vse_exciter),
            value = exciter.toFloat(),
            onValueChange = { onExciterChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$exciter%",
        )
    }
}

@Composable
fun EqualizerSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
    showCurvePreview: Boolean = true,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val eqVals = state.eq.forType(fxType)
    val enabled = eqVals.enabled
    val bandCount = eqVals.bandCount
    val presetId = eqVals.presetId
    val eqBands = eqVals.bands
    val eqPresets = eqVals.presets

    val bands =
        remember(eqBands) {
            eqBands.split(";").mapNotNull { it.toFloatOrNull() }
        }

    val onEnabledChange = viewModel::setEqEnabled
    val onBandCountChange = viewModel::setEqBandCount
    val onPresetSelect = viewModel::setEqPreset
    val onBandsChange = viewModel::setEqBands
    val onPresetAdd = viewModel::addEqPreset
    val onPresetDelete = viewModel::deleteEqPreset
    val onReset = viewModel::resetEqBands

    EffectSection(
        title = stringResource(R.string.section_equalizer),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Equalizer,
    ) {
        var showEqDialog by remember { mutableStateOf(false) }

        val bandCounts = listOf(10, 15, 25, 31)
        val bandCountOptions = bandCounts.map { stringResource(R.string.label_eq_n_bands, it) }
        val bandCountIndex =
            when (bandCount) {
                15 -> 1
                25 -> 2
                31 -> 3
                else -> 0
            }
        LabeledDropdown(
            label = stringResource(R.string.label_eq_bands),
            selectedValue = bandCountOptions[bandCountIndex],
            options = bandCountOptions,
            onOptionSelected = { index, _ -> onBandCountChange(bandCounts[index]) },
        )

        if (showCurvePreview && bands.size >= bandCount) {
            EqCurveGraph(
                bands = bands,
                onClick = { showEqDialog = true },
                bandCount = bandCount,
            )
        } else if (bands.size >= bandCount) {
            TextButton(
                text = stringResource(R.string.action_edit_curve),
                onClick = { showEqDialog = true },
                modifier = Modifier.padding(top = 8.dp),
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
                bandCount = bandCount,
            )
        }
    }
}

@Composable
fun DynamicEqSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val dynVals = state.dynamicEq.forType(fxType)
    val enabled = dynVals.enabled
    val onEnabledChange = viewModel::setDynamicEqEnabled
    val bandCount = dynVals.bandCount

    fun parseInts(
        s: String,
        def: Int,
    ) = s.split(";").map { it.toIntOrNull() ?: def }

    val freqs = parseInts(dynVals.freqs, 1000).take(bandCount)
    val qs = parseInts(dynVals.qs, 150).take(bandCount)
    val gains = parseInts(dynVals.gains, 0).take(bandCount)
    val thresholds = parseInts(dynVals.thresholds, -300).take(bandCount)
    val attacks = parseInts(dynVals.attacks, 10).take(bandCount)
    val releases = parseInts(dynVals.releases, 100).take(bandCount)
    val filterTypes = parseInts(dynVals.filterTypes, 0).take(bandCount)

    var selectedTab by remember { mutableIntStateOf(0) }
    val safeTab = if (bandCount > 0) selectedTab.coerceIn(0, bandCount - 1) else 0
    var deleteBandIndex by remember { mutableIntStateOf(-1) }

    fun formatFreq(hz: Int): String =
        when {
            hz >= 1000 -> "${hz / 1000}kHz"
            else -> "${hz}Hz"
        }

    if (deleteBandIndex >= 0) {
        ViperDialog(
            show = true,
            onDismissRequest = { deleteBandIndex = -1 },
            title = stringResource(R.string.dialog_delete_band),
            content = {
                Text(
                    text = stringResource(
                        R.string.dynamic_eq_delete_band_message,
                        formatFreq(freqs.getOrElse(deleteBandIndex) { 1000 }),
                    ),
                    style = MiuixTheme.textStyles.body2,
                )
            },
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                val i = deleteBandIndex
                deleteBandIndex = -1
                viewModel.removeDynamicEqBand(i)
                if (selectedTab >= bandCount - 1) selectedTab = maxOf(0, bandCount - 2)
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { deleteBandIndex = -1 },
        )
    }

    val dynamicEqTabNames = List(bandCount) { i -> formatFreq(freqs.getOrElse(i) { 1000 }) }
    val lastFreq = if (bandCount > 0) freqs.getOrElse(bandCount - 1) { 0 } else 0
    val canAddBand = bandCount < 8 && lastFreq < 20000
    val canDeleteBand = bandCount > 1

    val addBandLabel = stringResource(R.string.action_add)
    val deleteBandLabel = stringResource(R.string.action_delete)

    fun addBand() {
        viewModel.addDynamicEqBand()
        selectedTab = bandCount
    }

    fun requestDeleteBand() {
        if (canDeleteBand) deleteBandIndex = safeTab
    }

    val tabLabels =
        buildList {
            addAll(dynamicEqTabNames)
            if (canAddBand) add(addBandLabel)
            if (canDeleteBand) add(deleteBandLabel)
        }
    val selectedTabIndex =
        if (safeTab in tabLabels.indices) safeTab else 0

    fun handleTabSelected(index: Int) {
        when {
            index < dynamicEqTabNames.size -> selectedTab = index
            canAddBand && index == dynamicEqTabNames.size -> addBand()
            canDeleteBand -> requestDeleteBand()
        }
    }

    EffectSection(
        title = stringResource(R.string.section_dynamic_eq),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Insights,
    ) {
        ViperTabs(
            tabs = tabLabels,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = ::handleTabSelected,
        )

        if (bandCount > 0) {
            if (canDeleteBand) {
                TextButton(
                    text = stringResource(R.string.action_delete),
                    onClick = ::requestDeleteBand,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val freq = freqs.getOrElse(safeTab) { 1000 }
            val q = qs.getOrElse(safeTab) { 150 }
            val gain = gains.getOrElse(safeTab) { 0 }
            val threshold = thresholds.getOrElse(safeTab) { -300 }
            val attack = attacks.getOrElse(safeTab) { 10 }
            val release = releases.getOrElse(safeTab) { 100 }
            val filterType = filterTypes.getOrElse(safeTab) { 0 }.coerceIn(0, 2)

            val minFreq =
                if (safeTab > 0) (freqs.getOrElse(safeTab - 1) { 20 } + 5).toFloat() else 20f
            val maxFreq =
                if (safeTab < bandCount - 1) (freqs.getOrElse(safeTab + 1) { 20000 } - 5).toFloat() else 20000f

            val onFreqChange: (Int) -> Unit = { viewModel.setDynamicEqBandFreq(safeTab, it) }
            val onQChange: (Int) -> Unit = { viewModel.setDynamicEqBandQ(safeTab, it) }
            val onGainChange: (Int) -> Unit = { viewModel.setDynamicEqBandGain(safeTab, it) }
            val onThresholdChange: (Int) -> Unit =
                { viewModel.setDynamicEqBandThreshold(safeTab, it) }
            val onAttackChange: (Int) -> Unit = { viewModel.setDynamicEqBandAttack(safeTab, it) }
            val onReleaseChange: (Int) -> Unit = { viewModel.setDynamicEqBandRelease(safeTab, it) }
            val onFilterTypeChange: (Int) -> Unit =
                { viewModel.setDynamicEqBandFilterType(safeTab, it) }

            LabeledSlider(
                label = stringResource(R.string.label_frequency),
                value = freq.toFloat(),
                onValueChange = { onFreqChange(it.roundToInt()) },
                valueRange = minFreq..maxFreq,
                steps = ((maxFreq - minFreq) / 5f).toInt().coerceAtLeast(1) - 1,
                valueLabel = "$freq Hz",
            )
            LabeledSlider(
                label = stringResource(R.string.label_dynamic_eq_q_factor),
                value = q.toFloat(),
                onValueChange = { onQChange(it.roundToInt()) },
                valueRange = 50f..800f,
                valueLabel = String.format(Locale.US, "%.1f", q / 100f),
            )
            LabeledSlider(
                label = stringResource(R.string.label_dynamic_eq_target_gain),
                value = gain.toFloat(),
                onValueChange = { onGainChange(it.roundToInt()) },
                valueRange = -120f..120f,
                valueLabel = String.format(Locale.US, "%.1f dB", gain / 10f),
            )
            LabeledSlider(
                label = stringResource(R.string.label_threshold),
                value = threshold.toFloat(),
                onValueChange = { onThresholdChange(it.roundToInt()) },
                valueRange = -800f..0f,
                valueLabel = "${threshold / 10} dB",
            )
            LabeledSlider(
                label = stringResource(R.string.label_attack),
                value = attack.toFloat(),
                onValueChange = { onAttackChange(it.roundToInt()) },
                valueRange = 1f..100f,
                valueLabel = "$attack ms",
            )
            LabeledSlider(
                label = stringResource(R.string.label_release),
                value = release.toFloat(),
                onValueChange = { onReleaseChange(it.roundToInt()) },
                valueRange = 10f..500f,
                valueLabel = "$release ms",
            )
            val filterTypeNames =
                listOf(
                    stringResource(R.string.filter_peak),
                    stringResource(R.string.filter_low_shelf),
                    stringResource(R.string.filter_high_shelf),
                )
            LabeledDropdown(
                label = stringResource(R.string.label_dynamic_eq_filter_type),
                selectedValue = filterTypeNames[filterType],
                options = filterTypeNames,
                onOptionSelected = { index, _ -> onFilterTypeChange(index) },
            )
        }
    }
}

@Composable
fun ConvolverSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.convolver.forType(fxType)
    val enabled = vals.enabled
    val kernel = vals.kernel
    val crossChannel = vals.crossChannel

    val kernelFiles by viewModel.kernelFileList.collectAsStateWithLifecycle()
    val kernelNoneLabel = stringResource(R.string.label_none)
    val kernelOptions = listOf(kernelNoneLabel) + kernelFiles

    val onEnabledChange = viewModel::setConvolverEnabled
    val onKernelChange = viewModel::setConvolverKernel
    val onCrossChannelChange = viewModel::setConvolverCrossChannel

    EffectSection(
        title = stringResource(R.string.section_convolver),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.BlurCircular,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_convolver_kernel),
            selectedValue = kernel.ifEmpty { kernelNoneLabel },
            options = kernelOptions,
            onOptionSelected = { index, value ->
                onKernelChange(if (index == 0) "" else value)
            },
            onDeleteOption = { _, name -> viewModel.deleteKernelFile(name) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_convolver_cross_channel),
            value = crossChannel.toFloat(),
            onValueChange = { onCrossChannelChange(it.roundToInt()) },
            valueRange = 0f..100f,
        )
    }
}

@Composable
fun FieldSurroundSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
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
        icon = Icons.Default.SurroundSound,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_field_surround_widening),
            value = widening.toFloat(),
            onValueChange = { onWideningChange(it.roundToInt()) },
            valueRange = 0f..8f,
            steps = 7,
            valueLabel = "$widening",
        )
        LabeledSlider(
            label = stringResource(R.string.label_field_surround_mid_image),
            value = midImage.toFloat(),
            onValueChange = { onMidImageChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
        )
        LabeledSlider(
            label = stringResource(R.string.label_depth),
            value = depth.toFloat(),
            onValueChange = { onDepthChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
        )
    }
}

@Composable
fun DiffSurroundSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.diffSurround.forType(fxType)
    val enabled = vals.enabled
    val delay = vals.delay
    val reverse = vals.reverse
    val wetDryMix = vals.wetDryMix
    val lpCutoff = vals.lpCutoff

    val onEnabledChange = viewModel::setDiffSurroundEnabled
    val onDelayChange = viewModel::setDiffSurroundDelay
    val onReverseChange = viewModel::setDiffSurroundReverse
    val onWetDryMixChange = viewModel::setDiffSurroundWetDryMix
    val onLpCutoffChange = viewModel::setDiffSurroundLpCutoff

    EffectSection(
        title = stringResource(R.string.section_diff_surround),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.SpatialAudio,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_delay),
            value = delay.toFloat(),
            onValueChange = { onDelayChange(it.roundToInt()) },
            valueRange = 1f..20f,
            steps = 18,
            valueLabel = "$delay ms",
        )
        LabeledSwitch(
            label = stringResource(R.string.label_diff_surround_reverse),
            checked = reverse,
            onCheckedChange = onReverseChange,
        )
        LabeledSlider(
            label = stringResource(R.string.label_diff_surround_wet_dry_mix),
            value = wetDryMix.toFloat(),
            onValueChange = { onWetDryMixChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$wetDryMix%",
        )
        LabeledSlider(
            label = stringResource(R.string.label_diff_surround_lp_cutoff),
            value = lpCutoff.toFloat(),
            onValueChange = { onLpCutoffChange(it.roundToInt()) },
            valueRange = 0f..20000f,
            steps = 3999,
            valueLabel = if (lpCutoff == 0) stringResource(R.string.label_off) else "$lpCutoff Hz",
        )
    }
}

@Composable
fun StereoImagerSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.stereoImg.forType(fxType)
    val enabled = vals.enabled
    val lowWidth = vals.lowWidth
    val midWidth = vals.midWidth
    val highWidth = vals.highWidth
    val lowCrossover = vals.lowCrossover
    val highCrossover = vals.highCrossover

    val onEnabledChange = viewModel::setStereoImgEnabled
    val onLowWidthChange = viewModel::setStereoImgLowWidth
    val onMidWidthChange = viewModel::setStereoImgMidWidth
    val onHighWidthChange = viewModel::setStereoImgHighWidth
    val onLowCrossoverChange = viewModel::setStereoImgLowCrossover
    val onHighCrossoverChange = viewModel::setStereoImgHighCrossover

    EffectSection(
        title = stringResource(R.string.section_stereo_imager),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.AspectRatio,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_low_width),
            value = lowWidth.toFloat(),
            onValueChange = { onLowWidthChange(it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$lowWidth%",
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_mid_width),
            value = midWidth.toFloat(),
            onValueChange = { onMidWidthChange(it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$midWidth%",
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_high_width),
            value = highWidth.toFloat(),
            onValueChange = { onHighWidthChange(it.roundToInt()) },
            valueRange = 0f..200f,
            valueLabel = "$highWidth%",
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_low_crossover),
            value = lowCrossover.toFloat(),
            onValueChange = { onLowCrossoverChange(it.roundToInt()) },
            valueRange = 80f..400f,
            steps = 63,
            valueLabel = "$lowCrossover Hz",
        )
        LabeledSlider(
            label = stringResource(R.string.label_stereo_imager_high_crossover),
            value = highCrossover.toFloat(),
            onValueChange = { onHighCrossoverChange(it.roundToInt()) },
            valueRange = 2000f..8000f,
            steps = 1199,
            valueLabel = "$highCrossover Hz",
        )
    }
}

@Composable
fun HeadphoneSurroundSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
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
        icon = Icons.Default.Headphones,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_vhe_quality),
            value = quality.toFloat(),
            onValueChange = { onQualityChange(it.roundToInt()) },
            valueRange = 0f..4f,
            steps = 3,
        )
    }
}

@Composable
fun ReverberationSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
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
        title = stringResource(R.string.section_reverb),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.BlurOn,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_reverb_room_size),
            value = roomSize.toFloat(),
            onValueChange = { onRoomSizeChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
        )
        LabeledSlider(
            label = stringResource(R.string.label_width),
            value = width.toFloat(),
            onValueChange = { onWidthChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_dampening),
            value = dampening.toFloat(),
            onValueChange = { onDampeningChange(it.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_wet),
            value = wet.toFloat(),
            onValueChange = { onWetChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$wet%",
        )
        LabeledSlider(
            label = stringResource(R.string.label_reverb_dry),
            value = dry.toFloat(),
            onValueChange = { onDryChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$dry%",
        )
    }
}

@Composable
fun DynamicSystemSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
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

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf(TextFieldValue("")) }

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

    EffectSection(
        title = stringResource(R.string.section_dynamic_system),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.CandlestickChart,
    ) {
        val presetName =
            dsPresets.find { it.id == dsPresetId }?.let { resolvePresetName(it) }
                ?: stringResource(R.string.label_custom)
        LabeledDropdown(
            label = stringResource(R.string.label_preset),
            selectedValue = presetName,
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
            onValueChange = { onStrengthChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$strength%",
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_x_low_freq),
            value = xLow.toFloat(),
            onValueChange = { onXLowChange(it.roundToInt()) },
            valueRange = 0f..2400f,
            steps = (2400 / 5) - 1,
            valueLabel = "$xLow Hz",
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_x_high_freq),
            value = xHigh.toFloat(),
            onValueChange = { onXHighChange(it.roundToInt()) },
            valueRange = 0f..12000f,
            steps = (12000 / 5) - 1,
            valueLabel = "$xHigh Hz",
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_y_low_freq),
            value = yLow.toFloat(),
            onValueChange = { onYLowChange(it.roundToInt()) },
            valueRange = 0f..200f,
            steps = 199,
            valueLabel = "$yLow Hz",
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_y_high_freq),
            value = yHigh.toFloat(),
            onValueChange = { onYHighChange(it.roundToInt()) },
            valueRange = 0f..300f,
            steps = (300 / 5) - 1,
            valueLabel = "$yHigh Hz",
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_side_gain_low),
            value = sideGainLow.toFloat(),
            onValueChange = { onSideGainLowChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$sideGainLow%",
        )

        LabeledSlider(
            label = stringResource(R.string.label_dynamic_system_side_gain_high),
            value = sideGainHigh.toFloat(),
            onValueChange = { onSideGainHighChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$sideGainHigh%",
        )
    }

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

@Composable
fun TubeSimulatorSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.tube.forType(fxType)
    val enabled = vals.enabled

    val onEnabledChange = viewModel::setTubeSimulatorEnabled

    EffectSection(
        title = stringResource(R.string.section_tube_simulator),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.MusicNote,
        toggleOnly = true,
    ) {}
}

@Composable
fun PsychoacousticBassSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.psychoBass.forType(fxType)
    val enabled = vals.enabled
    val cutoff = vals.cutoff
    val intensity = vals.intensity
    val harmonicOrder = vals.harmonicOrder
    val originalLevel = vals.originalLevel

    val onEnabledChange = viewModel::setPsychoBassEnabled
    val onCutoffChange = viewModel::setPsychoBassCutoff
    val onIntensityChange = viewModel::setPsychoBassIntensity
    val onHarmonicOrderChange = viewModel::setPsychoBassHarmonicOrder
    val onOriginalLevelChange = viewModel::setPsychoBassOriginalLevel

    val harmonicNames =
        listOf(
            stringResource(R.string.harmonic_2nd),
            stringResource(R.string.harmonic_3rd),
            stringResource(R.string.harmonic_4th),
            stringResource(R.string.harmonic_5th),
        )
    val harmonicValues = listOf(2, 3, 4, 5)
    val harmonicIndex = harmonicValues.indexOf(harmonicOrder).coerceAtLeast(0)

    EffectSection(
        title = stringResource(R.string.section_psycho_bass),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Psychology,
    ) {
        LabeledSlider(
            label = stringResource(R.string.label_psycho_bass_cutoff),
            value = cutoff.toFloat(),
            onValueChange = { onCutoffChange(it.roundToInt()) },
            valueRange = 60f..150f,
            valueLabel = "$cutoff Hz",
        )
        LabeledSlider(
            label = stringResource(R.string.label_psycho_bass_intensity),
            value = intensity.toFloat(),
            onValueChange = { onIntensityChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$intensity%",
        )
        LabeledSlider(
            label = stringResource(R.string.label_psycho_bass_harmonic_order),
            value = harmonicOrder.toFloat(),
            onValueChange = { onHarmonicOrderChange(it.roundToInt()) },
            valueRange = 2f..5f,
            steps = 2,
            valueLabel = harmonicNames[harmonicIndex],
        )
        LabeledSlider(
            label = stringResource(R.string.label_psycho_bass_ori_bass_level),
            value = originalLevel.toFloat(),
            onValueChange = { onOriginalLevelChange(it.roundToInt()) },
            valueRange = 0f..100f,
            valueLabel = "$originalLevel%",
        )
    }
}

@Composable
fun ViperBassSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
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

    val modeNames =
        listOf(
            stringResource(R.string.bass_mode_natural),
            stringResource(R.string.bass_mode_pure),
            stringResource(R.string.bass_mode_subwoofer),
        )

    EffectSection(
        title = stringResource(R.string.section_viper_bass),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.GraphicEq,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> onModeChange(index) },
        )
        if (mode != 2) {
            LabeledSlider(
                label = stringResource(R.string.label_frequency),
                value = frequency.toFloat(),
                onValueChange = { onFrequencyChange(it.roundToInt()) },
                valueRange = 0f..135f,
                steps = 134,
                valueLabel = "${frequency + 15}Hz",
            )
        }
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 50f..1000f,
            valueLabel = "${"%.1f".format(gain / 100.0)}x",
        )
        LabeledSwitch(
            label = stringResource(R.string.label_bass_anti_pop),
            checked = antiPop,
            onCheckedChange = onAntiPopChange,
        )
    }
}

@Composable
fun ViperBassMonoSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.bassMono.forType(fxType)
    val enabled = vals.enabled
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

    val onEnabledChange = viewModel::setBassMonoEnabled
    val onModeChange = viewModel::setBassMonoMode
    val onFrequencyChange = viewModel::setBassMonoFrequency
    val onGainChange = viewModel::setBassMonoGain
    val onAntiPopChange = viewModel::setBassMonoAntiPop

    EffectSection(
        title = stringResource(R.string.section_viper_bass_mono),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.GraphicEq,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> onModeChange(index) },
        )
        if (mode != 2) {
            LabeledSlider(
                label = stringResource(R.string.label_frequency),
                value = frequency.toFloat(),
                onValueChange = { onFrequencyChange(it.roundToInt()) },
                valueRange = 0f..135f,
                steps = 134,
                valueLabel = "${frequency + 15}Hz",
            )
        }
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 50f..1000f,
            valueLabel = "${"%.1f".format(gain / 100.0)}x",
        )
        LabeledSwitch(
            label = stringResource(R.string.label_bass_anti_pop),
            checked = antiPop,
            onCheckedChange = onAntiPopChange,
        )
    }
}

@Composable
fun ViperClaritySection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.clarity.forType(fxType)
    val enabled = vals.enabled
    val mode = vals.mode
    val gain = vals.gain

    val modeNames =
        listOf(
            stringResource(R.string.clarity_mode_natural),
            stringResource(R.string.clarity_mode_ozone),
            stringResource(R.string.clarity_mode_xhifi),
        )

    val onEnabledChange = viewModel::setClarityEnabled
    val onModeChange = viewModel::setClarityMode
    val onGainChange = viewModel::setClarityGain

    EffectSection(
        title = stringResource(R.string.section_viper_clarity),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Hearing,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> onModeChange(index) },
        )
        LabeledSlider(
            label = stringResource(R.string.label_gain),
            value = gain.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = 0f..450f,
            valueLabel = "${"%.1f".format(gain / 100.0)}x",
        )
    }
}

@Composable
fun AuditoryProtectionSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.cure.forType(fxType)
    val enabled = vals.enabled
    val strength = vals.strength

    val onEnabledChange = viewModel::setCureEnabled
    val onStrengthChange = viewModel::setCureStrength

    val strengthNames =
        listOf(
            stringResource(R.string.label_mild),
            stringResource(R.string.label_medium),
            stringResource(R.string.label_strong),
        )

    EffectSection(
        title = stringResource(R.string.section_cure),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.HealthAndSafety,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_cure_strength),
            selectedValue = strengthNames.getOrElse(strength) { strengthNames[0] },
            options = strengthNames,
            onOptionSelected = { index, _ -> onStrengthChange(index) },
        )
    }
}

@Composable
fun AnalogXSection(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean = false,
) {
    val fxType = if (isSpkMode) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
    val vals = state.analog.forType(fxType)
    val enabled = vals.enabled
    val mode = vals.mode

    val modeNames =
        listOf(
            stringResource(R.string.label_mild),
            stringResource(R.string.label_medium),
            stringResource(R.string.label_strong),
        )

    val onEnabledChange = viewModel::setAnalogXEnabled
    val onModeChange = viewModel::setAnalogXMode

    EffectSection(
        title = stringResource(R.string.section_analogx),
        enabled = enabled,
        onEnabledChange = onEnabledChange,
        icon = Icons.Default.Memory,
    ) {
        LabeledDropdown(
            label = stringResource(R.string.label_mode),
            selectedValue = modeNames.getOrElse(mode) { modeNames[0] },
            options = modeNames,
            onOptionSelected = { index, _ -> onModeChange(index) },
        )
    }
}

@Composable
fun SpeakerOptSection(
    state: MainUiState,
    viewModel: MainViewModel,
) {
    EffectSection(
        title = stringResource(R.string.section_speaker_optimization),
        enabled = state.speakerCorrection.spk.enabled,
        onEnabledChange = viewModel::setSpeakerOptEnabled,
        icon = Icons.Default.SpeakerPhone,
        toggleOnly = true,
    ) {}
}

package com.llsl.viper4android.ui.screens.main

import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.data.repository.ViperRepository
import kotlinx.coroutines.flow.first
import org.json.JSONObject

sealed class EffectPref<T>(
    val hpPrefKey: String,
    val spkPrefKey: String,
    val jsonKey: String,
    val spkJsonKey: String,
    val defaultValue: T,
    val getHp: (MainUiState) -> T,
    val setHp: MainUiState.(T) -> MainUiState,
    val getSpk: (MainUiState) -> T,
    val setSpk: MainUiState.(T) -> MainUiState
)

class IntPref(
    hpPrefKey: String,
    spkPrefKey: String,
    jsonKey: String,
    spkJsonKey: String,
    defaultValue: Int,
    getHp: (MainUiState) -> Int,
    setHp: MainUiState.(Int) -> MainUiState,
    getSp: (MainUiState) -> Int,
    setSp: MainUiState.(Int) -> MainUiState
) : EffectPref<Int>(
    hpPrefKey,
    spkPrefKey,
    jsonKey,
    spkJsonKey,
    defaultValue,
    getHp,
    setHp,
    getSp,
    setSp
)

class BoolPref(
    hpPrefKey: String,
    spkPrefKey: String,
    jsonKey: String,
    spkJsonKey: String,
    defaultValue: Boolean,
    getHp: (MainUiState) -> Boolean,
    setHp: MainUiState.(Boolean) -> MainUiState,
    getSp: (MainUiState) -> Boolean,
    setSp: MainUiState.(Boolean) -> MainUiState
) : EffectPref<Boolean>(
    hpPrefKey,
    spkPrefKey,
    jsonKey,
    spkJsonKey,
    defaultValue,
    getHp,
    setHp,
    getSp,
    setSp
)

class StringPref(
    hpPrefKey: String,
    spkPrefKey: String,
    jsonKey: String,
    spkJsonKey: String,
    defaultValue: String,
    getHp: (MainUiState) -> String,
    setHp: MainUiState.(String) -> MainUiState,
    getSp: (MainUiState) -> String,
    setSp: MainUiState.(String) -> MainUiState
) : EffectPref<String>(
    hpPrefKey,
    spkPrefKey,
    jsonKey,
    spkJsonKey,
    defaultValue,
    getHp,
    setHp,
    getSp,
    setSp
)

class NullableLongPref(
    hpPrefKey: String,
    spkPrefKey: String,
    jsonKey: String,
    spkJsonKey: String,
    getHp: (MainUiState) -> Long?,
    setHp: MainUiState.(Long?) -> MainUiState,
    getSp: (MainUiState) -> Long?,
    setSp: MainUiState.(Long?) -> MainUiState
) : EffectPref<Long?>(hpPrefKey, spkPrefKey, jsonKey, spkJsonKey, null, getHp, setHp, getSp, setSp)

val EFFECT_PREFS: List<EffectPref<*>> = listOf(
    BoolPref(
        hpPrefKey = ViperRepository.PREF_MASTER_ENABLE,
        spkPrefKey = "spk_${ViperRepository.PREF_MASTER_ENABLE}",
        jsonKey = "masterEnabled", spkJsonKey = "spkMasterEnabled",
        defaultValue = false,
        getHp = { it.masterEnabled }, setHp = { copy(masterEnabled = it) },
        getSp = { it.spkMasterEnabled }, setSp = { copy(spkMasterEnabled = it) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_OUTPUT_VOLUME}",
        spkPrefKey = "${ViperParams.PARAM_SPK_OUTPUT_VOLUME}",
        jsonKey = "outputVolume",
        spkJsonKey = "spkOutputVolume",
        defaultValue = 100,
        getHp = { it.out.hp.volume },
        setHp = { copy(out = out.copy(hp = out.hp.copy(volume = it))) },
        getSp = { it.out.spk.volume },
        setSp = { copy(out = out.copy(spk = out.spk.copy(volume = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CHANNEL_PAN}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CHANNEL_PAN}",
        jsonKey = "channelPan",
        spkJsonKey = "spkChannelPan",
        defaultValue = 0,
        getHp = { it.out.hp.channelPan },
        setHp = { copy(out = out.copy(hp = out.hp.copy(channelPan = it))) },
        getSp = { it.out.spk.channelPan },
        setSp = { copy(out = out.copy(spk = out.spk.copy(channelPan = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_LIMITER}",
        spkPrefKey = "${ViperParams.PARAM_SPK_LIMITER}",
        jsonKey = "limiter",
        spkJsonKey = "spkLimiter",
        defaultValue = 100,
        getHp = { it.out.hp.limiter },
        setHp = { copy(out = out.copy(hp = out.hp.copy(limiter = it))) },
        getSp = { it.out.spk.limiter },
        setSp = { copy(out = out.copy(spk = out.spk.copy(limiter = it))) }
    ),

    // AGC
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_AGC_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_AGC_ENABLE}",
        jsonKey = "agcEnabled",
        spkJsonKey = "spkAgcEnabled",
        defaultValue = false,
        getHp = { it.agc.hp.enabled },
        setHp = { copy(agc = agc.copy(hp = agc.hp.copy(enabled = it))) },
        getSp = { it.agc.spk.enabled },
        setSp = { copy(agc = agc.copy(spk = agc.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_AGC_RATIO}",
        spkPrefKey = "${ViperParams.PARAM_SPK_AGC_RATIO}",
        jsonKey = "agcStrength",
        spkJsonKey = "spkAgcStrength",
        defaultValue = 100,
        getHp = { it.agc.hp.strength },
        setHp = { copy(agc = agc.copy(hp = agc.hp.copy(strength = it))) },
        getSp = { it.agc.spk.strength },
        setSp = { copy(agc = agc.copy(spk = agc.spk.copy(strength = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_AGC_MAX_SCALER}",
        spkPrefKey = "${ViperParams.PARAM_SPK_AGC_MAX_SCALER}",
        jsonKey = "agcMaxGain",
        spkJsonKey = "spkAgcMaxGain",
        defaultValue = 100,
        getHp = { it.agc.hp.maxGain },
        setHp = { copy(agc = agc.copy(hp = agc.hp.copy(maxGain = it))) },
        getSp = { it.agc.spk.maxGain },
        setSp = { copy(agc = agc.copy(spk = agc.spk.copy(maxGain = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_AGC_VOLUME}",
        spkPrefKey = "${ViperParams.PARAM_SPK_AGC_VOLUME}",
        jsonKey = "agcOutputThreshold",
        spkJsonKey = "spkAgcOutputThreshold",
        defaultValue = 100,
        getHp = { it.agc.hp.outputThreshold },
        setHp = { copy(agc = agc.copy(hp = agc.hp.copy(outputThreshold = it))) },
        getSp = { it.agc.spk.outputThreshold },
        setSp = { copy(agc = agc.copy(spk = agc.spk.copy(outputThreshold = it))) }
    ),

    // LUFS
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_LUFS_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_LUFS_ENABLE}",
        jsonKey = "lufsEnabled",
        spkJsonKey = "spkLufsEnabled",
        defaultValue = false,
        getHp = { it.lufs.hp.enabled },
        setHp = { copy(lufs = lufs.copy(hp = lufs.hp.copy(enabled = it))) },
        getSp = { it.lufs.spk.enabled },
        setSp = { copy(lufs = lufs.copy(spk = lufs.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_LUFS_TARGET}",
        spkPrefKey = "${ViperParams.PARAM_SPK_LUFS_TARGET}",
        jsonKey = "lufsTarget",
        spkJsonKey = "spkLufsTarget",
        defaultValue = 140,
        getHp = { it.lufs.hp.target },
        setHp = { copy(lufs = lufs.copy(hp = lufs.hp.copy(target = it))) },
        getSp = { it.lufs.spk.target },
        setSp = { copy(lufs = lufs.copy(spk = lufs.spk.copy(target = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_LUFS_MAX_GAIN}",
        spkPrefKey = "${ViperParams.PARAM_SPK_LUFS_MAX_GAIN}",
        jsonKey = "lufsMaxGain",
        spkJsonKey = "spkLufsMaxGain",
        defaultValue = 60,
        getHp = { it.lufs.hp.maxGain },
        setHp = { copy(lufs = lufs.copy(hp = lufs.hp.copy(maxGain = it))) },
        getSp = { it.lufs.spk.maxGain },
        setSp = { copy(lufs = lufs.copy(spk = lufs.spk.copy(maxGain = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_LUFS_SPEED}",
        spkPrefKey = "${ViperParams.PARAM_SPK_LUFS_SPEED}",
        jsonKey = "lufsSpeed",
        spkJsonKey = "spkLufsSpeed",
        defaultValue = 1,
        getHp = { it.lufs.hp.speed },
        setHp = { copy(lufs = lufs.copy(hp = lufs.hp.copy(speed = it))) },
        getSp = { it.lufs.spk.speed },
        setSp = { copy(lufs = lufs.copy(spk = lufs.spk.copy(speed = it))) }
    ),

    // FET Compressor
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE}",
        jsonKey = "fetEnabled",
        spkJsonKey = "spkFetEnabled",
        defaultValue = false,
        getHp = { it.fet.hp.enabled },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(enabled = it))) },
        getSp = { it.fet.spk.enabled },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD}",
        jsonKey = "fetThreshold",
        spkJsonKey = "spkFetThreshold",
        defaultValue = 100,
        getHp = { it.fet.hp.threshold },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(threshold = it))) },
        getSp = { it.fet.spk.threshold },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(threshold = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO}",
        jsonKey = "fetRatio",
        spkJsonKey = "spkFetRatio",
        defaultValue = 100,
        getHp = { it.fet.hp.ratio },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(ratio = it))) },
        getSp = { it.fet.spk.ratio },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(ratio = it))) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE}",
        jsonKey = "fetAutoKnee",
        spkJsonKey = "spkFetAutoKnee",
        defaultValue = true,
        getHp = { it.fet.hp.autoKnee },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(autoKnee = it))) },
        getSp = { it.fet.spk.autoKnee },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(autoKnee = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE}",
        jsonKey = "fetKnee", spkJsonKey = "spkFetKnee",
        defaultValue = 0,
        getHp = { it.fet.hp.knee }, setHp = { copy(fet = fet.copy(hp = fet.hp.copy(knee = it))) },
        getSp = { it.fet.spk.knee }, setSp = { copy(fet = fet.copy(spk = fet.spk.copy(knee = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI}",
        jsonKey = "fetKneeMulti",
        spkJsonKey = "spkFetKneeMulti",
        defaultValue = 0,
        getHp = { it.fet.hp.kneeMulti },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(kneeMulti = it))) },
        getSp = { it.fet.spk.kneeMulti },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(kneeMulti = it))) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN}",
        jsonKey = "fetAutoGain",
        spkJsonKey = "spkFetAutoGain",
        defaultValue = true,
        getHp = { it.fet.hp.autoGain },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(autoGain = it))) },
        getSp = { it.fet.spk.autoGain },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(autoGain = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN}",
        jsonKey = "fetGain", spkJsonKey = "spkFetGain",
        defaultValue = 0,
        getHp = { it.fet.hp.gain }, setHp = { copy(fet = fet.copy(hp = fet.hp.copy(gain = it))) },
        getSp = { it.fet.spk.gain }, setSp = { copy(fet = fet.copy(spk = fet.spk.copy(gain = it))) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK}",
        jsonKey = "fetAutoAttack",
        spkJsonKey = "spkFetAutoAttack",
        defaultValue = true,
        getHp = { it.fet.hp.autoAttack },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(autoAttack = it))) },
        getSp = { it.fet.spk.autoAttack },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(autoAttack = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK}",
        jsonKey = "fetAttack",
        spkJsonKey = "spkFetAttack",
        defaultValue = 20,
        getHp = { it.fet.hp.attack },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(attack = it))) },
        getSp = { it.fet.spk.attack },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(attack = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK}",
        jsonKey = "fetMaxAttack",
        spkJsonKey = "spkFetMaxAttack",
        defaultValue = 80,
        getHp = { it.fet.hp.maxAttack },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(maxAttack = it))) },
        getSp = { it.fet.spk.maxAttack },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(maxAttack = it))) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE}",
        jsonKey = "fetAutoRelease",
        spkJsonKey = "spkFetAutoRelease",
        defaultValue = true,
        getHp = { it.fet.hp.autoRelease },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(autoRelease = it))) },
        getSp = { it.fet.spk.autoRelease },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(autoRelease = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE}",
        jsonKey = "fetRelease",
        spkJsonKey = "spkFetRelease",
        defaultValue = 50,
        getHp = { it.fet.hp.release },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(release = it))) },
        getSp = { it.fet.spk.release },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(release = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE}",
        jsonKey = "fetMaxRelease",
        spkJsonKey = "spkFetMaxRelease",
        defaultValue = 100,
        getHp = { it.fet.hp.maxRelease },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(maxRelease = it))) },
        getSp = { it.fet.spk.maxRelease },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(maxRelease = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_CREST}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST}",
        jsonKey = "fetCrest",
        spkJsonKey = "spkFetCrest",
        defaultValue = 100,
        getHp = { it.fet.hp.crest },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(crest = it))) },
        getSp = { it.fet.spk.crest },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(crest = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT}",
        jsonKey = "fetAdapt",
        spkJsonKey = "spkFetAdapt",
        defaultValue = 50,
        getHp = { it.fet.hp.adapt },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(adapt = it))) },
        getSp = { it.fet.spk.adapt },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(adapt = it))) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP}",
        spkPrefKey = "${ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP}",
        jsonKey = "fetNoClip",
        spkJsonKey = "spkFetNoClip",
        defaultValue = true,
        getHp = { it.fet.hp.noClip },
        setHp = { copy(fet = fet.copy(hp = fet.hp.copy(noClip = it))) },
        getSp = { it.fet.spk.noClip },
        setSp = { copy(fet = fet.copy(spk = fet.spk.copy(noClip = it))) }
    ),

    // Multiband Compressor
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_MULTIBAND_COMP_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_MULTIBAND_COMP_ENABLE}",
        jsonKey = "mbcEnabled",
        spkJsonKey = "spkMbcEnabled",
        defaultValue = false,
        getHp = { it.mbc.hp.enabled },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(enabled = it))) },
        getSp = { it.mbc.spk.enabled },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(enabled = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_band_enables",
        spkPrefKey = "spk_mbc_band_enables",
        jsonKey = "mbcBandEnables",
        spkJsonKey = "spkMbcBandEnables",
        defaultValue = "1;1;1;1;1",
        getHp = { it.mbc.hp.bandEnables },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(bandEnables = it))) },
        getSp = { it.mbc.spk.bandEnables },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(bandEnables = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_crossovers",
        spkPrefKey = "spk_mbc_crossovers",
        jsonKey = "mbcCrossovers",
        spkJsonKey = "spkMbcCrossovers",
        defaultValue = "120;500;4000;8000",
        getHp = { it.mbc.hp.crossovers },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(crossovers = it))) },
        getSp = { it.mbc.spk.crossovers },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(crossovers = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_thresholds",
        spkPrefKey = "spk_mbc_thresholds",
        jsonKey = "mbcThresholds",
        spkJsonKey = "spkMbcThresholds",
        defaultValue = "-18;-18;-18;-18;-18",
        getHp = { it.mbc.hp.thresholds },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(thresholds = it))) },
        getSp = { it.mbc.spk.thresholds },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(thresholds = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_ratios",
        spkPrefKey = "spk_mbc_ratios",
        jsonKey = "mbcRatios",
        spkJsonKey = "spkMbcRatios",
        defaultValue = "50;50;50;50;50",
        getHp = { it.mbc.hp.ratios },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(ratios = it))) },
        getSp = { it.mbc.spk.ratios },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(ratios = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_gains",
        spkPrefKey = "spk_mbc_gains",
        jsonKey = "mbcGains",
        spkJsonKey = "spkMbcGains",
        defaultValue = "24;24;24;24;24",
        getHp = { it.mbc.hp.gains },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(gains = it))) },
        getSp = { it.mbc.spk.gains },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(gains = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_knees",
        spkPrefKey = "spk_mbc_knees",
        jsonKey = "mbcKnees",
        spkJsonKey = "spkMbcKnees",
        defaultValue = "0;0;0;0;0",
        getHp = { it.mbc.hp.knees },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(knees = it))) },
        getSp = { it.mbc.spk.knees },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(knees = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_knee_multis",
        spkPrefKey = "spk_mbc_knee_multis",
        jsonKey = "mbcKneeMultis",
        spkJsonKey = "spkMbcKneeMultis",
        defaultValue = "0;0;0;0;0",
        getHp = { it.mbc.hp.kneeMultis },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(kneeMultis = it))) },
        getSp = { it.mbc.spk.kneeMultis },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(kneeMultis = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_attacks",
        spkPrefKey = "spk_mbc_attacks",
        jsonKey = "mbcAttacks",
        spkJsonKey = "spkMbcAttacks",
        defaultValue = "1;1;1;1;1",
        getHp = { it.mbc.hp.attacks },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(attacks = it))) },
        getSp = { it.mbc.spk.attacks },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(attacks = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_max_attacks",
        spkPrefKey = "spk_mbc_max_attacks",
        jsonKey = "mbcMaxAttacks",
        spkJsonKey = "spkMbcMaxAttacks",
        defaultValue = "44;44;44;44;44",
        getHp = { it.mbc.hp.maxAttacks },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(maxAttacks = it))) },
        getSp = { it.mbc.spk.maxAttacks },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(maxAttacks = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_releases",
        spkPrefKey = "spk_mbc_releases",
        jsonKey = "mbcReleases",
        spkJsonKey = "spkMbcReleases",
        defaultValue = "100;100;100;100;100",
        getHp = { it.mbc.hp.releases },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(releases = it))) },
        getSp = { it.mbc.spk.releases },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(releases = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_max_releases",
        spkPrefKey = "spk_mbc_max_releases",
        jsonKey = "mbcMaxReleases",
        spkJsonKey = "spkMbcMaxReleases",
        defaultValue = "200;200;200;200;200",
        getHp = { it.mbc.hp.maxReleases },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(maxReleases = it))) },
        getSp = { it.mbc.spk.maxReleases },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(maxReleases = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_crests",
        spkPrefKey = "spk_mbc_crests",
        jsonKey = "mbcCrests",
        spkJsonKey = "spkMbcCrests",
        defaultValue = "100;100;100;100;100",
        getHp = { it.mbc.hp.crests },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(crests = it))) },
        getSp = { it.mbc.spk.crests },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(crests = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_adapts",
        spkPrefKey = "spk_mbc_adapts",
        jsonKey = "mbcAdapts",
        spkJsonKey = "spkMbcAdapts",
        defaultValue = "50;50;50;50;50",
        getHp = { it.mbc.hp.adapts },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(adapts = it))) },
        getSp = { it.mbc.spk.adapts },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(adapts = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_auto_knees",
        spkPrefKey = "spk_mbc_auto_knees",
        jsonKey = "mbcAutoKnees",
        spkJsonKey = "spkMbcAutoKnees",
        defaultValue = "1;1;1;1;1",
        getHp = { it.mbc.hp.autoKnees },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(autoKnees = it))) },
        getSp = { it.mbc.spk.autoKnees },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(autoKnees = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_auto_gains",
        spkPrefKey = "spk_mbc_auto_gains",
        jsonKey = "mbcAutoGains",
        spkJsonKey = "spkMbcAutoGains",
        defaultValue = "1;1;1;1;1",
        getHp = { it.mbc.hp.autoGains },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(autoGains = it))) },
        getSp = { it.mbc.spk.autoGains },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(autoGains = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_auto_attacks",
        spkPrefKey = "spk_mbc_auto_attacks",
        jsonKey = "mbcAutoAttacks",
        spkJsonKey = "spkMbcAutoAttacks",
        defaultValue = "1;1;1;1;1",
        getHp = { it.mbc.hp.autoAttacks },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(autoAttacks = it))) },
        getSp = { it.mbc.spk.autoAttacks },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(autoAttacks = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_auto_releases",
        spkPrefKey = "spk_mbc_auto_releases",
        jsonKey = "mbcAutoReleases",
        spkJsonKey = "spkMbcAutoReleases",
        defaultValue = "1;1;1;1;1",
        getHp = { it.mbc.hp.autoReleases },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(autoReleases = it))) },
        getSp = { it.mbc.spk.autoReleases },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(autoReleases = it))) }
    ),
    StringPref(
        hpPrefKey = "mbc_no_clips",
        spkPrefKey = "spk_mbc_no_clips",
        jsonKey = "mbcNoClips",
        spkJsonKey = "spkMbcNoClips",
        defaultValue = "1;1;1;1;1",
        getHp = { it.mbc.hp.noClips },
        setHp = { copy(mbc = mbc.copy(hp = mbc.hp.copy(noClips = it))) },
        getSp = { it.mbc.spk.noClips },
        setSp = { copy(mbc = mbc.copy(spk = mbc.spk.copy(noClips = it))) }
    ),

    // DDC
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DDC_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DDC_ENABLE}",
        jsonKey = "ddcEnabled",
        spkJsonKey = "spkDdcEnabled",
        defaultValue = false,
        getHp = { it.ddc.hp.enabled },
        setHp = { copy(ddc = ddc.copy(hp = ddc.hp.copy(enabled = it))) },
        getSp = { it.ddc.spk.enabled },
        setSp = { copy(ddc = ddc.copy(spk = ddc.spk.copy(enabled = it))) }
    ),
    StringPref(
        hpPrefKey = ViperRepository.PREF_DDC_DEVICE,
        spkPrefKey = "spk_${ViperRepository.PREF_DDC_DEVICE}",
        jsonKey = "ddcDevice",
        spkJsonKey = "spkDdcDevice",
        defaultValue = "",
        getHp = { it.ddc.hp.device },
        setHp = { copy(ddc = ddc.copy(hp = ddc.hp.copy(device = it))) },
        getSp = { it.ddc.spk.device },
        setSp = { copy(ddc = ddc.copy(spk = ddc.spk.copy(device = it))) }
    ),

    // Spectrum Extension
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE}",
        jsonKey = "vseEnabled",
        spkJsonKey = "spkVseEnabled",
        defaultValue = false,
        getHp = { it.vse.hp.enabled },
        setHp = { copy(vse = vse.copy(hp = vse.hp.copy(enabled = it))) },
        getSp = { it.vse.spk.enabled },
        setSp = { copy(vse = vse.copy(spk = vse.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK}",
        jsonKey = "vseStrength",
        spkJsonKey = "spkVseStrength",
        defaultValue = 7600,
        getHp = { it.vse.hp.strength },
        setHp = { copy(vse = vse.copy(hp = vse.hp.copy(strength = it))) },
        getSp = { it.vse.spk.strength },
        setSp = { copy(vse = vse.copy(spk = vse.spk.copy(strength = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT}",
        jsonKey = "vseExciter",
        spkJsonKey = "spkVseExciter",
        defaultValue = 0,
        getHp = { it.vse.hp.exciter },
        setHp = { copy(vse = vse.copy(hp = vse.hp.copy(exciter = it))) },
        getSp = { it.vse.spk.exciter },
        setSp = { copy(vse = vse.copy(spk = vse.spk.copy(exciter = it))) }
    ),

    // EQ
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_EQ_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_EQ_ENABLE}",
        jsonKey = "eqEnabled",
        spkJsonKey = "spkEqEnabled",
        defaultValue = false,
        getHp = { it.eq.hp.enabled },
        setHp = { copy(eq = eq.copy(hp = eq.hp.copy(enabled = it))) },
        getSp = { it.eq.spk.enabled },
        setSp = { copy(eq = eq.copy(spk = eq.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_EQ_BAND_COUNT}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_EQ_BAND_COUNT}",
        jsonKey = "eqBandCount",
        spkJsonKey = "spkEqBandCount",
        defaultValue = 10,
        getHp = { it.eq.hp.bandCount },
        setHp = { copy(eq = eq.copy(hp = eq.hp.copy(bandCount = it))) },
        getSp = { it.eq.spk.bandCount },
        setSp = { copy(eq = eq.copy(spk = eq.spk.copy(bandCount = it))) }
    ),
    StringPref(
        hpPrefKey = "${ViperParams.PARAM_HP_EQ_BAND_LEVEL}",
        spkPrefKey = "${ViperParams.PARAM_SPK_EQ_BAND_LEVEL}",
        jsonKey = "eqBands", spkJsonKey = "spkEqBands",
        defaultValue = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
        getHp = { it.eq.hp.bands }, setHp = { copy(eq = eq.copy(hp = eq.hp.copy(bands = it))) },
        getSp = { it.eq.spk.bands }, setSp = { copy(eq = eq.copy(spk = eq.spk.copy(bands = it))) }
    ),
    NullableLongPref(
        hpPrefKey = ViperRepository.PREF_EQ_PRESET_ID,
        spkPrefKey = "spk_${ViperRepository.PREF_EQ_PRESET_ID}",
        jsonKey = "eqPresetId",
        spkJsonKey = "spkEqPresetId",
        getHp = { it.eq.hp.presetId },
        setHp = { copy(eq = eq.copy(hp = eq.hp.copy(presetId = it))) },
        getSp = { it.eq.spk.presetId },
        setSp = { copy(eq = eq.copy(spk = eq.spk.copy(presetId = it))) }
    ),

    // Dynamic EQ
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_EQ_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_DYNAMIC_EQ_ENABLE}",
        jsonKey = "dynamicEqEnabled",
        spkJsonKey = "spkDynamicEqEnabled",
        defaultValue = false,
        getHp = { it.dynamicEq.hp.enabled },
        setHp = { copy(dynamicEq = dynamicEq.copy(hp = dynamicEq.hp.copy(enabled = it))) },
        getSp = { it.dynamicEq.spk.enabled },
        setSp = { copy(dynamicEq = dynamicEq.copy(spk = dynamicEq.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "dynamic_eq_band_count",
        spkPrefKey = "spk_dynamic_eq_band_count",
        jsonKey = "dynamicEqBandCount",
        spkJsonKey = "spkDynamicEqBandCount",
        defaultValue = 3,
        getHp = { it.dynamicEq.hp.bandCount },
        setHp = { copy(dynamicEq = dynamicEq.copy(hp = dynamicEq.hp.copy(bandCount = it))) },
        getSp = { it.dynamicEq.spk.bandCount },
        setSp = { copy(dynamicEq = dynamicEq.copy(spk = dynamicEq.spk.copy(bandCount = it))) }
    ),
    StringPref(
        hpPrefKey = "dynamic_eq_freqs",
        spkPrefKey = "spk_dynamic_eq_freqs",
        jsonKey = "dynamicEqFreqs",
        spkJsonKey = "spkDynamicEqFreqs",
        defaultValue = "60;150;400;1000;2500;5000;8000;12000",
        getHp = { it.dynamicEq.hp.freqs },
        setHp = { copy(dynamicEq = dynamicEq.copy(hp = dynamicEq.hp.copy(freqs = it))) },
        getSp = { it.dynamicEq.spk.freqs },
        setSp = { copy(dynamicEq = dynamicEq.copy(spk = dynamicEq.spk.copy(freqs = it))) }
    ),
    StringPref(
        hpPrefKey = "dynamic_eq_qs",
        spkPrefKey = "spk_dynamic_eq_qs",
        jsonKey = "dynamicEqQs",
        spkJsonKey = "spkDynamicEqQs",
        defaultValue = "100;100;150;150;150;200;200;200",
        getHp = { it.dynamicEq.hp.qs },
        setHp = { copy(dynamicEq = dynamicEq.copy(hp = dynamicEq.hp.copy(qs = it))) },
        getSp = { it.dynamicEq.spk.qs },
        setSp = { copy(dynamicEq = dynamicEq.copy(spk = dynamicEq.spk.copy(qs = it))) }
    ),
    StringPref(
        hpPrefKey = "dynamic_eq_gains",
        spkPrefKey = "spk_dynamic_eq_gains",
        jsonKey = "dynamicEqGains",
        spkJsonKey = "spkDynamicEqGains",
        defaultValue = "0;0;0;0;0;0;0;0",
        getHp = { it.dynamicEq.hp.gains },
        setHp = { copy(dynamicEq = dynamicEq.copy(hp = dynamicEq.hp.copy(gains = it))) },
        getSp = { it.dynamicEq.spk.gains },
        setSp = { copy(dynamicEq = dynamicEq.copy(spk = dynamicEq.spk.copy(gains = it))) }
    ),
    StringPref(
        hpPrefKey = "dynamic_eq_thresholds",
        spkPrefKey = "spk_dynamic_eq_thresholds",
        jsonKey = "dynamicEqThresholds",
        spkJsonKey = "spkDynamicEqThresholds",
        defaultValue = "-300;-300;-250;-250;-200;-200;-200;-200",
        getHp = { it.dynamicEq.hp.thresholds },
        setHp = { copy(dynamicEq = dynamicEq.copy(hp = dynamicEq.hp.copy(thresholds = it))) },
        getSp = { it.dynamicEq.spk.thresholds },
        setSp = { copy(dynamicEq = dynamicEq.copy(spk = dynamicEq.spk.copy(thresholds = it))) }
    ),
    StringPref(
        hpPrefKey = "dynamic_eq_attacks",
        spkPrefKey = "spk_dynamic_eq_attacks",
        jsonKey = "dynamicEqAttacks",
        spkJsonKey = "spkDynamicEqAttacks",
        defaultValue = "10;10;10;10;10;10;10;10",
        getHp = { it.dynamicEq.hp.attacks },
        setHp = { copy(dynamicEq = dynamicEq.copy(hp = dynamicEq.hp.copy(attacks = it))) },
        getSp = { it.dynamicEq.spk.attacks },
        setSp = { copy(dynamicEq = dynamicEq.copy(spk = dynamicEq.spk.copy(attacks = it))) }
    ),
    StringPref(
        hpPrefKey = "dynamic_eq_releases",
        spkPrefKey = "spk_dynamic_eq_releases",
        jsonKey = "dynamicEqReleases",
        spkJsonKey = "spkDynamicEqReleases",
        defaultValue = "100;100;100;100;100;100;100;100",
        getHp = { it.dynamicEq.hp.releases },
        setHp = { copy(dynamicEq = dynamicEq.copy(hp = dynamicEq.hp.copy(releases = it))) },
        getSp = { it.dynamicEq.spk.releases },
        setSp = { copy(dynamicEq = dynamicEq.copy(spk = dynamicEq.spk.copy(releases = it))) }
    ),
    StringPref(
        hpPrefKey = "dynamic_eq_filter_types",
        spkPrefKey = "spk_dynamic_eq_filter_types",
        jsonKey = "dynamicEqFilterTypes",
        spkJsonKey = "spkDynamicEqFilterTypes",
        defaultValue = "0;0;0;0;0;0;0;0",
        getHp = { it.dynamicEq.hp.filterTypes },
        setHp = { copy(dynamicEq = dynamicEq.copy(hp = dynamicEq.hp.copy(filterTypes = it))) },
        getSp = { it.dynamicEq.spk.filterTypes },
        setSp = { copy(dynamicEq = dynamicEq.copy(spk = dynamicEq.spk.copy(filterTypes = it))) }
    ),

    // Convolver
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CONVOLVER_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_CONVOLVER_ENABLE}",
        jsonKey = "convolverEnabled",
        spkJsonKey = "spkConvolverEnabled",
        defaultValue = false,
        getHp = { it.convolver.hp.enabled },
        setHp = { copy(convolver = convolver.copy(hp = convolver.hp.copy(enabled = it))) },
        getSp = { it.convolver.spk.enabled },
        setSp = { copy(convolver = convolver.copy(spk = convolver.spk.copy(enabled = it))) }
    ),
    StringPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CONVOLVER_SET_KERNEL}",
        jsonKey = "convolverKernel",
        spkJsonKey = "spkConvolverKernel",
        defaultValue = "",
        getHp = { it.convolver.hp.kernel },
        setHp = { copy(convolver = convolver.copy(hp = convolver.hp.copy(kernel = it))) },
        getSp = { it.convolver.spk.kernel },
        setSp = { copy(convolver = convolver.copy(spk = convolver.spk.copy(kernel = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL}",
        spkPrefKey = "${ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL}",
        jsonKey = "convolverCrossChannel",
        spkJsonKey = "spkConvolverCrossChannel",
        defaultValue = 0,
        getHp = { it.convolver.hp.crossChannel },
        setHp = { copy(convolver = convolver.copy(hp = convolver.hp.copy(crossChannel = it))) },
        getSp = { it.convolver.spk.crossChannel },
        setSp = { copy(convolver = convolver.copy(spk = convolver.spk.copy(crossChannel = it))) }
    ),

    // Field Surround
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE}",
        jsonKey = "fieldSurroundEnabled",
        spkJsonKey = "spkFieldSurroundEnabled",
        defaultValue = false,
        getHp = { it.fieldSurround.hp.enabled },
        setHp = { copy(fieldSurround = fieldSurround.copy(hp = fieldSurround.hp.copy(enabled = it))) },
        getSp = { it.fieldSurround.spk.enabled },
        setSp = { copy(fieldSurround = fieldSurround.copy(spk = fieldSurround.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING}",
        jsonKey = "fieldSurroundWidening",
        spkJsonKey = "spkFieldSurroundWidening",
        defaultValue = 0,
        getHp = { it.fieldSurround.hp.widening },
        setHp = { copy(fieldSurround = fieldSurround.copy(hp = fieldSurround.hp.copy(widening = it))) },
        getSp = { it.fieldSurround.spk.widening },
        setSp = { copy(fieldSurround = fieldSurround.copy(spk = fieldSurround.spk.copy(widening = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE}",
        jsonKey = "fieldSurroundMidImage",
        spkJsonKey = "spkFieldSurroundMidImage",
        defaultValue = 5,
        getHp = { it.fieldSurround.hp.midImage },
        setHp = { copy(fieldSurround = fieldSurround.copy(hp = fieldSurround.hp.copy(midImage = it))) },
        getSp = { it.fieldSurround.spk.midImage },
        setSp = { copy(fieldSurround = fieldSurround.copy(spk = fieldSurround.spk.copy(midImage = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH}",
        jsonKey = "fieldSurroundDepth",
        spkJsonKey = "spkFieldSurroundDepth",
        defaultValue = 0,
        getHp = { it.fieldSurround.hp.depth },
        setHp = { copy(fieldSurround = fieldSurround.copy(hp = fieldSurround.hp.copy(depth = it))) },
        getSp = { it.fieldSurround.spk.depth },
        setSp = { copy(fieldSurround = fieldSurround.copy(spk = fieldSurround.spk.copy(depth = it))) }
    ),

    // Diff Surround
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE}",
        jsonKey = "diffSurroundEnabled",
        spkJsonKey = "spkDiffSurroundEnabled",
        defaultValue = false,
        getHp = { it.diffSurround.hp.enabled },
        setHp = { copy(diffSurround = diffSurround.copy(hp = diffSurround.hp.copy(enabled = it))) },
        getSp = { it.diffSurround.spk.enabled },
        setSp = { copy(diffSurround = diffSurround.copy(spk = diffSurround.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DIFF_SURROUND_DELAY}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY}",
        jsonKey = "diffSurroundDelay",
        spkJsonKey = "spkDiffSurroundDelay",
        defaultValue = 5,
        getHp = { it.diffSurround.hp.delay },
        setHp = { copy(diffSurround = diffSurround.copy(hp = diffSurround.hp.copy(delay = it))) },
        getSp = { it.diffSurround.spk.delay },
        setSp = { copy(diffSurround = diffSurround.copy(spk = diffSurround.spk.copy(delay = it))) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE}",
        jsonKey = "diffSurroundReverse",
        spkJsonKey = "spkDiffSurroundReverse",
        defaultValue = false,
        getHp = { it.diffSurround.hp.reverse },
        setHp = { copy(diffSurround = diffSurround.copy(hp = diffSurround.hp.copy(reverse = it))) },
        getSp = { it.diffSurround.spk.reverse },
        setSp = { copy(diffSurround = diffSurround.copy(spk = diffSurround.spk.copy(reverse = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DIFF_SURROUND_WET_DRY_MIX}",
        spkPrefKey = "${ViperParams.PARAM_SPK_DIFF_SURROUND_WET_DRY_MIX}",
        jsonKey = "diffSurroundWetDryMix",
        spkJsonKey = "spkDiffSurroundWetDryMix",
        defaultValue = 100,
        getHp = { it.diffSurround.hp.wetDryMix },
        setHp = { copy(diffSurround = diffSurround.copy(hp = diffSurround.hp.copy(wetDryMix = it))) },
        getSp = { it.diffSurround.spk.wetDryMix },
        setSp = { copy(diffSurround = diffSurround.copy(spk = diffSurround.spk.copy(wetDryMix = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DIFF_SURROUND_LP_CUTOFF}",
        spkPrefKey = "${ViperParams.PARAM_SPK_DIFF_SURROUND_LP_CUTOFF}",
        jsonKey = "diffSurroundLpCutoff",
        spkJsonKey = "spkDiffSurroundLpCutoff",
        defaultValue = 0,
        getHp = { it.diffSurround.hp.lpCutoff },
        setHp = { copy(diffSurround = diffSurround.copy(hp = diffSurround.hp.copy(lpCutoff = it))) },
        getSp = { it.diffSurround.spk.lpCutoff },
        setSp = { copy(diffSurround = diffSurround.copy(spk = diffSurround.spk.copy(lpCutoff = it))) }
    ),

    // Stereo Imager
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_STEREO_IMAGER_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_STEREO_IMAGER_ENABLE}",
        jsonKey = "stereoImgEnabled",
        spkJsonKey = "spkStereoImgEnabled",
        defaultValue = false,
        getHp = { it.stereoImg.hp.enabled },
        setHp = { copy(stereoImg = stereoImg.copy(hp = stereoImg.hp.copy(enabled = it))) },
        getSp = { it.stereoImg.spk.enabled },
        setSp = { copy(stereoImg = stereoImg.copy(spk = stereoImg.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_STEREO_IMAGER_LOW_WIDTH}",
        spkPrefKey = "${ViperParams.PARAM_SPK_STEREO_IMAGER_LOW_WIDTH}",
        jsonKey = "stereoImgLowWidth",
        spkJsonKey = "spkStereoImgLowWidth",
        defaultValue = 100,
        getHp = { it.stereoImg.hp.lowWidth },
        setHp = { copy(stereoImg = stereoImg.copy(hp = stereoImg.hp.copy(lowWidth = it))) },
        getSp = { it.stereoImg.spk.lowWidth },
        setSp = { copy(stereoImg = stereoImg.copy(spk = stereoImg.spk.copy(lowWidth = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_STEREO_IMAGER_MID_WIDTH}",
        spkPrefKey = "${ViperParams.PARAM_SPK_STEREO_IMAGER_MID_WIDTH}",
        jsonKey = "stereoImgMidWidth",
        spkJsonKey = "spkStereoImgMidWidth",
        defaultValue = 100,
        getHp = { it.stereoImg.hp.midWidth },
        setHp = { copy(stereoImg = stereoImg.copy(hp = stereoImg.hp.copy(midWidth = it))) },
        getSp = { it.stereoImg.spk.midWidth },
        setSp = { copy(stereoImg = stereoImg.copy(spk = stereoImg.spk.copy(midWidth = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_STEREO_IMAGER_HIGH_WIDTH}",
        spkPrefKey = "${ViperParams.PARAM_SPK_STEREO_IMAGER_HIGH_WIDTH}",
        jsonKey = "stereoImgHighWidth",
        spkJsonKey = "spkStereoImgHighWidth",
        defaultValue = 100,
        getHp = { it.stereoImg.hp.highWidth },
        setHp = { copy(stereoImg = stereoImg.copy(hp = stereoImg.hp.copy(highWidth = it))) },
        getSp = { it.stereoImg.spk.highWidth },
        setSp = { copy(stereoImg = stereoImg.copy(spk = stereoImg.spk.copy(highWidth = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_STEREO_IMAGER_LOW_CROSSOVER}",
        spkPrefKey = "${ViperParams.PARAM_SPK_STEREO_IMAGER_LOW_CROSSOVER}",
        jsonKey = "stereoImgLowCrossover",
        spkJsonKey = "spkStereoImgLowCrossover",
        defaultValue = 200,
        getHp = { it.stereoImg.hp.lowCrossover },
        setHp = { copy(stereoImg = stereoImg.copy(hp = stereoImg.hp.copy(lowCrossover = it))) },
        getSp = { it.stereoImg.spk.lowCrossover },
        setSp = { copy(stereoImg = stereoImg.copy(spk = stereoImg.spk.copy(lowCrossover = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_STEREO_IMAGER_HIGH_CROSSOVER}",
        spkPrefKey = "${ViperParams.PARAM_SPK_STEREO_IMAGER_HIGH_CROSSOVER}",
        jsonKey = "stereoImgHighCrossover",
        spkJsonKey = "spkStereoImgHighCrossover",
        defaultValue = 4000,
        getHp = { it.stereoImg.hp.highCrossover },
        setHp = { copy(stereoImg = stereoImg.copy(hp = stereoImg.hp.copy(highCrossover = it))) },
        getSp = { it.stereoImg.spk.highCrossover },
        setSp = { copy(stereoImg = stereoImg.copy(spk = stereoImg.spk.copy(highCrossover = it))) }
    ),

    // VHE (Headphone Surround)
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE}",
        jsonKey = "vheEnabled",
        spkJsonKey = "spkVheEnabled",
        defaultValue = false,
        getHp = { it.vhe.hp.enabled },
        setHp = { copy(vhe = vhe.copy(hp = vhe.hp.copy(enabled = it))) },
        getSp = { it.vhe.spk.enabled },
        setSp = { copy(vhe = vhe.copy(spk = vhe.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH}",
        jsonKey = "vheQuality",
        spkJsonKey = "spkVheQuality",
        defaultValue = 0,
        getHp = { it.vhe.hp.quality },
        setHp = { copy(vhe = vhe.copy(hp = vhe.hp.copy(quality = it))) },
        getSp = { it.vhe.spk.quality },
        setSp = { copy(vhe = vhe.copy(spk = vhe.spk.copy(quality = it))) }
    ),

    // Reverb
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_REVERB_ENABLE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_REVERB_ENABLE}",
        jsonKey = "reverbEnabled",
        spkJsonKey = "spkReverbEnabled",
        defaultValue = false,
        getHp = { it.reverb.hp.enabled },
        setHp = { copy(reverb = reverb.copy(hp = reverb.hp.copy(enabled = it))) },
        getSp = { it.reverb.spk.enabled },
        setSp = { copy(reverb = reverb.copy(spk = reverb.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_REVERB_ROOM_SIZE}",
        spkPrefKey = "${ViperParams.PARAM_SPK_REVERB_ROOM_SIZE}",
        jsonKey = "reverbRoomSize",
        spkJsonKey = "spkReverbRoomSize",
        defaultValue = 0,
        getHp = { it.reverb.hp.roomSize },
        setHp = { copy(reverb = reverb.copy(hp = reverb.hp.copy(roomSize = it))) },
        getSp = { it.reverb.spk.roomSize },
        setSp = { copy(reverb = reverb.copy(spk = reverb.spk.copy(roomSize = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_REVERB_ROOM_WIDTH}",
        spkPrefKey = "${ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH}",
        jsonKey = "reverbWidth",
        spkJsonKey = "spkReverbWidth",
        defaultValue = 0,
        getHp = { it.reverb.hp.width },
        setHp = { copy(reverb = reverb.copy(hp = reverb.hp.copy(width = it))) },
        getSp = { it.reverb.spk.width },
        setSp = { copy(reverb = reverb.copy(spk = reverb.spk.copy(width = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING}",
        spkPrefKey = "${ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING}",
        jsonKey = "reverbDampening",
        spkJsonKey = "spkReverbDampening",
        defaultValue = 0,
        getHp = { it.reverb.hp.dampening },
        setHp = { copy(reverb = reverb.copy(hp = reverb.hp.copy(dampening = it))) },
        getSp = { it.reverb.spk.dampening },
        setSp = { copy(reverb = reverb.copy(spk = reverb.spk.copy(dampening = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL}",
        spkPrefKey = "${ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL}",
        jsonKey = "reverbWet",
        spkJsonKey = "spkReverbWet",
        defaultValue = 0,
        getHp = { it.reverb.hp.wet },
        setHp = { copy(reverb = reverb.copy(hp = reverb.hp.copy(wet = it))) },
        getSp = { it.reverb.spk.wet },
        setSp = { copy(reverb = reverb.copy(spk = reverb.spk.copy(wet = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL}",
        spkPrefKey = "${ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL}",
        jsonKey = "reverbDry",
        spkJsonKey = "spkReverbDry",
        defaultValue = 50,
        getHp = { it.reverb.hp.dry },
        setHp = { copy(reverb = reverb.copy(hp = reverb.hp.copy(dry = it))) },
        getSp = { it.reverb.spk.dry },
        setSp = { copy(reverb = reverb.copy(spk = reverb.spk.copy(dry = it))) }
    ),

    // Dynamic System
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE}",
        jsonKey = "dynamicSystemEnabled",
        spkJsonKey = "spkDynamicSystemEnabled",
        defaultValue = false,
        getHp = { it.dynamicSystem.hp.enabled },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(hp = dynamicSystem.hp.copy(enabled = it))) },
        getSp = { it.dynamicSystem.spk.enabled },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spk = dynamicSystem.spk.copy(enabled = it))) }
    ),
    NullableLongPref(
        hpPrefKey = ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID,
        spkPrefKey = "spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}",
        jsonKey = "dsPresetId",
        spkJsonKey = "spkDsPresetId",
        getHp = { it.dynamicSystem.hp.presetId },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(hp = dynamicSystem.hp.copy(presetId = it))) },
        getSp = { it.dynamicSystem.spk.presetId },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spk = dynamicSystem.spk.copy(presetId = it))) }
    ),
    IntPref(
        hpPrefKey = ViperRepository.PERF_DYNAMIC_SYS_DEVICE,
        spkPrefKey = "spk_${ViperRepository.PERF_DYNAMIC_SYS_DEVICE}",
        jsonKey = "dynamicSystemDevice",
        spkJsonKey = "spkDynamicSystemDevice",
        defaultValue = 0,
        getHp = { it.dynamicSystem.hp.device },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(hp = dynamicSystem.hp.copy(device = it))) },
        getSp = { it.dynamicSystem.spk.device },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spk = dynamicSystem.spk.copy(device = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH}",
        jsonKey = "dynamicSystemStrength",
        spkJsonKey = "spkDynamicSystemStrength",
        defaultValue = 50,
        getHp = { it.dynamicSystem.hp.strength },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(hp = dynamicSystem.hp.copy(strength = it))) },
        getSp = { it.dynamicSystem.spk.strength },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spk = dynamicSystem.spk.copy(strength = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
        jsonKey = "dsXLow",
        spkJsonKey = "spkDsXLow",
        defaultValue = 100,
        getHp = { it.dynamicSystem.hp.xLow },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(hp = dynamicSystem.hp.copy(xLow = it))) },
        getSp = { it.dynamicSystem.spk.xLow },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spk = dynamicSystem.spk.copy(xLow = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
        jsonKey = "dsXHigh",
        spkJsonKey = "spkDsXHigh",
        defaultValue = 5600,
        getHp = { it.dynamicSystem.hp.xHigh },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(hp = dynamicSystem.hp.copy(xHigh = it))) },
        getSp = { it.dynamicSystem.spk.xHigh },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spk = dynamicSystem.spk.copy(xHigh = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
        jsonKey = "dsYLow",
        spkJsonKey = "spkDsYLow",
        defaultValue = 40,
        getHp = { it.dynamicSystem.hp.yLow },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(hp = dynamicSystem.hp.copy(yLow = it))) },
        getSp = { it.dynamicSystem.spk.yLow },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spk = dynamicSystem.spk.copy(yLow = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
        jsonKey = "dsYHigh",
        spkJsonKey = "spkDsYHigh",
        defaultValue = 80,
        getHp = { it.dynamicSystem.hp.yHigh },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(hp = dynamicSystem.hp.copy(yHigh = it))) },
        getSp = { it.dynamicSystem.spk.yHigh },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spk = dynamicSystem.spk.copy(yHigh = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
        jsonKey = "dsSideGainLow",
        spkJsonKey = "spkDsSideGainLow",
        defaultValue = 50,
        getHp = { it.dynamicSystem.hp.sideGainLow },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(hp = dynamicSystem.hp.copy(sideGainLow = it))) },
        getSp = { it.dynamicSystem.spk.sideGainLow },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spk = dynamicSystem.spk.copy(sideGainLow = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
        jsonKey = "dsSideGainHigh",
        spkJsonKey = "spkDsSideGainHigh",
        defaultValue = 50,
        getHp = { it.dynamicSystem.hp.sideGainHigh },
        setHp = { copy(dynamicSystem = dynamicSystem.copy(hp = dynamicSystem.hp.copy(sideGainHigh = it))) },
        getSp = { it.dynamicSystem.spk.sideGainHigh },
        setSp = { copy(dynamicSystem = dynamicSystem.copy(spk = dynamicSystem.spk.copy(sideGainHigh = it))) }
    ),

    // Tube Simulator
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE}",
        jsonKey = "tubeSimulatorEnabled",
        spkJsonKey = "spkTubeSimulatorEnabled",
        defaultValue = false,
        getHp = { it.tube.hp.enabled },
        setHp = { copy(tube = tube.copy(hp = tube.hp.copy(enabled = it))) },
        getSp = { it.tube.spk.enabled },
        setSp = { copy(tube = tube.copy(spk = tube.spk.copy(enabled = it))) }
    ),

    // Bass
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_ENABLE}",
        jsonKey = "bassEnabled",
        spkJsonKey = "spkBassEnabled",
        defaultValue = false,
        getHp = { it.bass.hp.enabled },
        setHp = { copy(bass = bass.copy(hp = bass.hp.copy(enabled = it))) },
        getSp = { it.bass.spk.enabled },
        setSp = { copy(bass = bass.copy(spk = bass.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_MODE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_MODE}",
        jsonKey = "bassMode",
        spkJsonKey = "spkBassMode",
        defaultValue = 0,
        getHp = { it.bass.hp.mode },
        setHp = { copy(bass = bass.copy(hp = bass.hp.copy(mode = it))) },
        getSp = { it.bass.spk.mode },
        setSp = { copy(bass = bass.copy(spk = bass.spk.copy(mode = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_FREQUENCY}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_FREQUENCY}",
        jsonKey = "bassFrequency",
        spkJsonKey = "spkBassFrequency",
        defaultValue = 55,
        getHp = { it.bass.hp.frequency },
        setHp = { copy(bass = bass.copy(hp = bass.hp.copy(frequency = it))) },
        getSp = { it.bass.spk.frequency },
        setSp = { copy(bass = bass.copy(spk = bass.spk.copy(frequency = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_GAIN}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_GAIN}",
        jsonKey = "bassGain",
        spkJsonKey = "spkBassGain",
        defaultValue = 50,
        getHp = { it.bass.hp.gain },
        setHp = { copy(bass = bass.copy(hp = bass.hp.copy(gain = it))) },
        getSp = { it.bass.spk.gain },
        setSp = { copy(bass = bass.copy(spk = bass.spk.copy(gain = it))) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_ANTI_POP}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_ANTI_POP}",
        jsonKey = "bassAntiPop",
        spkJsonKey = "spkBassAntiPop",
        defaultValue = true,
        getHp = { it.bass.hp.antiPop },
        setHp = { copy(bass = bass.copy(hp = bass.hp.copy(antiPop = it))) },
        getSp = { it.bass.spk.antiPop },
        setSp = { copy(bass = bass.copy(spk = bass.spk.copy(antiPop = it))) }
    ),

    // Bass Mono
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_MONO_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_MONO_ENABLE}",
        jsonKey = "bassMonoEnabled",
        spkJsonKey = "spkBassMonoEnabled",
        defaultValue = false,
        getHp = { it.bassMono.hp.enabled },
        setHp = { copy(bassMono = bassMono.copy(hp = bassMono.hp.copy(enabled = it))) },
        getSp = { it.bassMono.spk.enabled },
        setSp = { copy(bassMono = bassMono.copy(spk = bassMono.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_MONO_MODE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_MONO_MODE}",
        jsonKey = "bassMonoMode",
        spkJsonKey = "spkBassMonoMode",
        defaultValue = 0,
        getHp = { it.bassMono.hp.mode },
        setHp = { copy(bassMono = bassMono.copy(hp = bassMono.hp.copy(mode = it))) },
        getSp = { it.bassMono.spk.mode },
        setSp = { copy(bassMono = bassMono.copy(spk = bassMono.spk.copy(mode = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_MONO_FREQUENCY}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY}",
        jsonKey = "bassMonoFrequency",
        spkJsonKey = "spkBassMonoFrequency",
        defaultValue = 55,
        getHp = { it.bassMono.hp.frequency },
        setHp = { copy(bassMono = bassMono.copy(hp = bassMono.hp.copy(frequency = it))) },
        getSp = { it.bassMono.spk.frequency },
        setSp = { copy(bassMono = bassMono.copy(spk = bassMono.spk.copy(frequency = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_MONO_GAIN}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_MONO_GAIN}",
        jsonKey = "bassMonoGain",
        spkJsonKey = "spkBassMonoGain",
        defaultValue = 50,
        getHp = { it.bassMono.hp.gain },
        setHp = { copy(bassMono = bassMono.copy(hp = bassMono.hp.copy(gain = it))) },
        getSp = { it.bassMono.spk.gain },
        setSp = { copy(bassMono = bassMono.copy(spk = bassMono.spk.copy(gain = it))) }
    ),
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_BASS_MONO_ANTI_POP}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP}",
        jsonKey = "bassMonoAntiPop",
        spkJsonKey = "spkBassMonoAntiPop",
        defaultValue = true,
        getHp = { it.bassMono.hp.antiPop },
        setHp = { copy(bassMono = bassMono.copy(hp = bassMono.hp.copy(antiPop = it))) },
        getSp = { it.bassMono.spk.antiPop },
        setSp = { copy(bassMono = bassMono.copy(spk = bassMono.spk.copy(antiPop = it))) }
    ),

    // Clarity
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CLARITY_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CLARITY_ENABLE}",
        jsonKey = "clarityEnabled",
        spkJsonKey = "spkClarityEnabled",
        defaultValue = false,
        getHp = { it.clarity.hp.enabled },
        setHp = { copy(clarity = clarity.copy(hp = clarity.hp.copy(enabled = it))) },
        getSp = { it.clarity.spk.enabled },
        setSp = { copy(clarity = clarity.copy(spk = clarity.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CLARITY_MODE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CLARITY_MODE}",
        jsonKey = "clarityMode",
        spkJsonKey = "spkClarityMode",
        defaultValue = 0,
        getHp = { it.clarity.hp.mode },
        setHp = { copy(clarity = clarity.copy(hp = clarity.hp.copy(mode = it))) },
        getSp = { it.clarity.spk.mode },
        setSp = { copy(clarity = clarity.copy(spk = clarity.spk.copy(mode = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CLARITY_GAIN}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CLARITY_GAIN}",
        jsonKey = "clarityGain",
        spkJsonKey = "spkClarityGain",
        defaultValue = 50,
        getHp = { it.clarity.hp.gain },
        setHp = { copy(clarity = clarity.copy(hp = clarity.hp.copy(gain = it))) },
        getSp = { it.clarity.spk.gain },
        setSp = { copy(clarity = clarity.copy(spk = clarity.spk.copy(gain = it))) }
    ),

    // Cure
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CURE_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CURE_ENABLE}",
        jsonKey = "cureEnabled",
        spkJsonKey = "spkCureEnabled",
        defaultValue = false,
        getHp = { it.cure.hp.enabled },
        setHp = { copy(cure = cure.copy(hp = cure.hp.copy(enabled = it))) },
        getSp = { it.cure.spk.enabled },
        setSp = { copy(cure = cure.copy(spk = cure.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_CURE_STRENGTH}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_CURE_STRENGTH}",
        jsonKey = "cureStrength",
        spkJsonKey = "spkCureStrength",
        defaultValue = 0,
        getHp = { it.cure.hp.strength },
        setHp = { copy(cure = cure.copy(hp = cure.hp.copy(strength = it))) },
        getSp = { it.cure.spk.strength },
        setSp = { copy(cure = cure.copy(spk = cure.spk.copy(strength = it))) }
    ),

    // AnalogX
    BoolPref(
        hpPrefKey = "${ViperParams.PARAM_HP_ANALOGX_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_ANALOGX_ENABLE}",
        jsonKey = "analogxEnabled",
        spkJsonKey = "spkAnalogxEnabled",
        defaultValue = false,
        getHp = { it.analog.hp.enabled },
        setHp = { copy(analog = analog.copy(hp = analog.hp.copy(enabled = it))) },
        getSp = { it.analog.spk.enabled },
        setSp = { copy(analog = analog.copy(spk = analog.spk.copy(enabled = it))) }
    ),
    IntPref(
        hpPrefKey = "${ViperParams.PARAM_HP_ANALOGX_MODE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_ANALOGX_MODE}",
        jsonKey = "analogxMode",
        spkJsonKey = "spkAnalogxMode",
        defaultValue = 0,
        getHp = { it.analog.hp.mode },
        setHp = { copy(analog = analog.copy(hp = analog.hp.copy(mode = it))) },
        getSp = { it.analog.spk.mode },
        setSp = { copy(analog = analog.copy(spk = analog.spk.copy(mode = it))) }
    ),

    // Speaker Correction
    BoolPref(
        hpPrefKey = "spk_${ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE}",
        spkPrefKey = "spk_${ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE}",
        jsonKey = "speakerOptEnabled",
        spkJsonKey = "speakerOptEnabled",
        defaultValue = false,
        getHp = { it.speakerCorrection.hp.enabled },
        setHp = {
            copy(
                speakerCorrection = speakerCorrection.copy(
                    hp = speakerCorrection.hp.copy(enabled = it)
                )
            )
        },
        getSp = { it.speakerCorrection.spk.enabled },
        setSp = {
            copy(
                speakerCorrection = speakerCorrection.copy(
                    spk = speakerCorrection.spk.copy(enabled = it)
                )
            )
        }
    )
)

suspend fun loadEffectPrefs(
    repository: ViperRepository,
    isSpk: Boolean,
    state: MainUiState = MainUiState()
): MainUiState {
    var s = state
    for (pref in EFFECT_PREFS) {
        s = when (pref) {
            is IntPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = repository.getIntPreference(key, pref.defaultValue).first()
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }

            is BoolPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = repository.getBooleanPreference(key, pref.defaultValue).first()
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }

            is StringPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = repository.getStringPreference(key, pref.defaultValue).first()
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }

            is NullableLongPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val raw = repository.getIntPreference(key, -1).first()
                val value = if (raw < 0) null else raw.toLong()
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }
        }
    }
    return s
}

suspend fun saveEffectPrefs(
    repository: ViperRepository,
    state: MainUiState,
    isSpk: Boolean
) {
    for (pref in EFFECT_PREFS) {
        when (pref) {
            is IntPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                repository.setIntPreference(key, value)
            }

            is BoolPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                repository.setBooleanPreference(key, value)
            }

            is StringPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                repository.setStringPreference(key, value)
            }

            is NullableLongPref -> {
                val key = if (isSpk) pref.spkPrefKey else pref.hpPrefKey
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                repository.setIntPreference(key, value?.toInt() ?: -1)
            }
        }
    }
}

fun serializeEffectPrefs(state: MainUiState, isSpk: Boolean): JSONObject {
    val obj = JSONObject()
    for (pref in EFFECT_PREFS) {
        val jsonKey = if (isSpk) pref.spkJsonKey else pref.jsonKey
        when (pref) {
            is IntPref -> {
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                obj.put(jsonKey, value)
            }

            is BoolPref -> {
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                obj.put(jsonKey, value)
            }

            is StringPref -> {
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                obj.put(jsonKey, value)
            }

            is NullableLongPref -> {
                val value = if (isSpk) pref.getSpk(state) else pref.getHp(state)
                obj.put(jsonKey, value ?: -1)
            }
        }
    }
    return obj
}

fun deserializeEffectPrefs(
    obj: JSONObject,
    state: MainUiState,
    isSpk: Boolean
): MainUiState {
    var s = state
    for (pref in EFFECT_PREFS) {
        val jsonKey = if (isSpk) pref.spkJsonKey else pref.jsonKey
        s = when (pref) {
            is IntPref -> {
                val fallback = if (isSpk) pref.getSpk(s) else pref.getHp(s)
                val value = obj.optInt(jsonKey, fallback)
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }

            is BoolPref -> {
                val fallback = if (isSpk) pref.getSpk(s) else pref.getHp(s)
                val value = obj.optBoolean(jsonKey, fallback)
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }

            is StringPref -> {
                val fallback = if (isSpk) pref.getSpk(s) else pref.getHp(s)
                val value = obj.optString(jsonKey, fallback)
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }

            is NullableLongPref -> {
                val value = obj.optInt(jsonKey, -1).let { if (it < 0) null else it.toLong() }
                if (isSpk) pref.setSpk(s, value) else pref.setHp(s, value)
            }
        }
    }
    return s
}

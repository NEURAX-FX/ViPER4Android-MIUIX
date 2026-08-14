package com.llsl.viper4android.effect

import com.llsl.viper4android.viper.ViperParams

class IemEffect : EffectGroupBuilder("iem") {
    val enable = bool(ViperParams.PARAM_IEM_ENABLE, "enable", false,
        { it.iem.general.enable },
        { copy(iem = iem.copy(general = iem.general.copy(enable = it))) })
    val wet = int(ViperParams.PARAM_IEM_WET, "wetPercent", 100,
        { it.iem.output.wetPercent },
        { copy(iem = iem.copy(output = iem.output.copy(wetPercent = it))) }, range = 0..100)
    val outputGain = int(ViperParams.PARAM_IEM_OUTPUT_GAIN, "gainDecidb", 0,
        { it.iem.output.gainDecidb },
        { copy(iem = iem.copy(output = iem.output.copy(gainDecidb = it))) }, range = -240..240)
    val order = int(ViperParams.PARAM_IEM_ORDER, "order", 3,
        { it.iem.general.order },
        { copy(iem = iem.copy(general = iem.general.copy(order = it))) }, range = 1..3)
    val encoderMode = int(ViperParams.PARAM_IEM_ENCODER_MODE, "encoderMode", 0,
        { it.iem.general.encoderMode },
        { copy(iem = iem.copy(general = iem.general.copy(encoderMode = it))) }, range = 0..3)
    val renderMode = int(ViperParams.PARAM_IEM_RENDER_MODE, "renderMode", 2,
        { it.iem.general.renderMode },
        { copy(iem = iem.copy(general = iem.general.copy(renderMode = it))) }, range = 0..2)
    val latencyProfile = int(ViperParams.PARAM_IEM_LATENCY_PROFILE, "latencyProfile", 1,
        { it.iem.output.latencyProfile },
        { copy(iem = iem.copy(output = iem.output.copy(latencyProfile = it))) }, range = 0..2)
    val limiterEnable = bool(ViperParams.PARAM_IEM_LIMITER_ENABLE, "limiterEnabled", true,
        { it.iem.output.limiterEnabled },
        { copy(iem = iem.copy(output = iem.output.copy(limiterEnabled = it))) })
    val limiterCeiling = int(ViperParams.PARAM_IEM_LIMITER_CEILING,
        "limiterCeilingCentidb", -30, { it.iem.output.limiterCeilingCentidb },
        { copy(iem = iem.copy(output = iem.output.copy(limiterCeilingCentidb = it))) },
        range = -1200..0)

    val stereoAzimuth = stereoInt(ViperParams.PARAM_IEM_STEREO_AZIMUTH,
        "stereoAzimuth", 0, -18000..18000, { it.azimuthCentidegrees },
        { copy(azimuthCentidegrees = it) })
    val stereoElevation = stereoInt(ViperParams.PARAM_IEM_STEREO_ELEVATION,
        "stereoElevation", 0, -18000..18000, { it.elevationCentidegrees },
        { copy(elevationCentidegrees = it) })
    val stereoRoll = stereoInt(ViperParams.PARAM_IEM_STEREO_ROLL,
        "stereoRoll", 0, -18000..18000, { it.rollCentidegrees },
        { copy(rollCentidegrees = it) })
    val stereoWidth = stereoInt(ViperParams.PARAM_IEM_STEREO_WIDTH,
        "stereoWidth", 6000, -36000..36000, { it.widthCentidegrees },
        { copy(widthCentidegrees = it) })
    val stereoSampleWise = bool(ViperParams.PARAM_IEM_STEREO_SAMPLE_WISE,
        "stereoSampleWise", false, { it.iem.stereo.sampleWise },
        { copy(iem = iem.copy(stereo = iem.stereo.copy(sampleWise = it))) })

    val multiAzimuth = intList(ViperParams.PARAM_IEM_MULTI_AZIMUTH, "multiAzimuth",
        listOf(-3000, 3000), { it.iem.multi.azimuthCentidegrees },
        { copy(iem = iem.copy(multi = iem.multi.copy(azimuthCentidegrees = it))) },
        range = -18000..18000)
    val multiElevation = intList(ViperParams.PARAM_IEM_MULTI_ELEVATION, "multiElevation",
        listOf(0, 0), { it.iem.multi.elevationCentidegrees },
        { copy(iem = iem.copy(multi = iem.multi.copy(elevationCentidegrees = it))) },
        range = -18000..18000)
    val multiGain = intList(ViperParams.PARAM_IEM_MULTI_GAIN, "multiGain", listOf(0, 0),
        { it.iem.multi.gainDecidb },
        { copy(iem = iem.copy(multi = iem.multi.copy(gainDecidb = it))) }, range = -600..100)
    val multiMute = boolList(ViperParams.PARAM_IEM_MULTI_MUTE, "multiMute",
        listOf(false, false), { it.iem.multi.mute },
        { copy(iem = iem.copy(multi = iem.multi.copy(mute = it))) })

    val granularAzimuth = granularInt(ViperParams.PARAM_IEM_GRANULAR_AZIMUTH,
        "granularAzimuth", 0, -18000..18000, { it.azimuthCentidegrees },
        { copy(azimuthCentidegrees = it) })
    val granularElevation = granularInt(ViperParams.PARAM_IEM_GRANULAR_ELEVATION,
        "granularElevation", 0, -18000..18000, { it.elevationCentidegrees },
        { copy(elevationCentidegrees = it) })
    val granularShape = granularInt(ViperParams.PARAM_IEM_GRANULAR_SHAPE,
        "granularShape", 0, -100..100, { it.shapeTenths }, { copy(shapeTenths = it) })
    val granularSize = granularInt(ViperParams.PARAM_IEM_GRANULAR_SIZE,
        "granularSize", 18000, 0..36000, { it.sizeCentidegrees },
        { copy(sizeCentidegrees = it) })
    val granularRoll = granularInt(ViperParams.PARAM_IEM_GRANULAR_ROLL,
        "granularRoll", 0, -18000..18000, { it.rollCentidegrees },
        { copy(rollCentidegrees = it) })
    val granularWidth = granularInt(ViperParams.PARAM_IEM_GRANULAR_WIDTH,
        "granularWidth", 0, -36000..36000, { it.widthCentidegrees },
        { copy(widthCentidegrees = it) })
    val granularDeltaTime = granularInt(ViperParams.PARAM_IEM_GRANULAR_DELTA_TIME,
        "granularDeltaTime", 5000, 1000..2000000, { it.deltaTimeUs },
        { copy(deltaTimeUs = it) })
    val granularDeltaTimeMod = granularInt(ViperParams.PARAM_IEM_GRANULAR_DELTA_TIME_MOD,
        "granularDeltaTimeMod", 0, 0..1000, { it.deltaTimeModTenthsPercent },
        { copy(deltaTimeModTenthsPercent = it) })
    val granularGrainLength = granularInt(ViperParams.PARAM_IEM_GRANULAR_GRAIN_LENGTH,
        "granularGrainLength", 250000, 1000..2000000, { it.grainLengthUs },
        { copy(grainLengthUs = it) })
    val granularGrainLengthMod = granularInt(ViperParams.PARAM_IEM_GRANULAR_GRAIN_LENGTH_MOD,
        "granularGrainLengthMod", 0, 0..1000, { it.grainLengthModTenthsPercent },
        { copy(grainLengthModTenthsPercent = it) })
    val granularReadPosition = granularInt(ViperParams.PARAM_IEM_GRANULAR_READ_POSITION,
        "granularReadPosition", 0, 0..4000000, { it.readPositionUs },
        { copy(readPositionUs = it) })
    val granularPositionMod = granularInt(ViperParams.PARAM_IEM_GRANULAR_POSITION_MOD,
        "granularPositionMod", 50000, 0..4000000, { it.positionModUs },
        { copy(positionModUs = it) })
    val granularPitch = granularInt(ViperParams.PARAM_IEM_GRANULAR_PITCH,
        "granularPitch", 0, -12000..12000, { it.pitchMilliSemitones },
        { copy(pitchMilliSemitones = it) })
    val granularPitchMod = granularInt(ViperParams.PARAM_IEM_GRANULAR_PITCH_MOD,
        "granularPitchMod", 0, 0..12000, { it.pitchModMilliSemitones },
        { copy(pitchModMilliSemitones = it) })
    val granularAttack = granularInt(ViperParams.PARAM_IEM_GRANULAR_WINDOW_ATTACK,
        "granularAttack", 500, 0..500, { it.attackTenthsPercent },
        { copy(attackTenthsPercent = it) })
    val granularAttackMod = granularInt(ViperParams.PARAM_IEM_GRANULAR_ATTACK_MOD,
        "granularAttackMod", 0, 0..1000, { it.attackModTenthsPercent },
        { copy(attackModTenthsPercent = it) })
    val granularDecay = granularInt(ViperParams.PARAM_IEM_GRANULAR_WINDOW_DECAY,
        "granularDecay", 500, 0..500, { it.decayTenthsPercent },
        { copy(decayTenthsPercent = it) })
    val granularDecayMod = granularInt(ViperParams.PARAM_IEM_GRANULAR_DECAY_MOD,
        "granularDecayMod", 0, 0..1000, { it.decayModTenthsPercent },
        { copy(decayModTenthsPercent = it) })
    val granularMix = granularInt(ViperParams.PARAM_IEM_GRANULAR_MIX,
        "granularMix", 500, 0..1000, { it.mixTenthsPercent },
        { copy(mixTenthsPercent = it) })
    val granularSourceProbability = granularInt(ViperParams.PARAM_IEM_GRANULAR_SOURCE_PROBABILITY,
        "granularSourceProbability", 0, -100..100, { it.sourceProbabilityHundredths },
        { copy(sourceProbabilityHundredths = it) })
    val granularSpatialMode = granularInt(ViperParams.PARAM_IEM_GRANULAR_SPATIAL_MODE,
        "granularSpatialMode", 0, 0..1, { it.spatialMode }, { copy(spatialMode = it) })
    val granularSampleWise = bool(ViperParams.PARAM_IEM_GRANULAR_SAMPLE_WISE,
        "granularSampleWise", false, { it.iem.granular.sampleWise },
        { copy(iem = iem.copy(granular = iem.granular.copy(sampleWise = it))) })

    val rotationYaw = rotationInt(ViperParams.PARAM_IEM_ROTATION_YAW,
        "rotationYaw", 0, -18000..18000, { it.yawCentidegrees },
        { copy(yawCentidegrees = it) })
    val rotationPitch = rotationInt(ViperParams.PARAM_IEM_ROTATION_PITCH,
        "rotationPitch", 0, -18000..18000, { it.pitchCentidegrees },
        { copy(pitchCentidegrees = it) })
    val rotationRoll = rotationInt(ViperParams.PARAM_IEM_ROTATION_ROLL,
        "rotationRoll", 0, -18000..18000, { it.rollCentidegrees },
        { copy(rollCentidegrees = it) })
    val invertYaw = rotationBool(ViperParams.PARAM_IEM_ROTATION_INVERT_YAW,
        "invertYaw", { it.invertYaw }, { copy(invertYaw = it) })
    val invertPitch = rotationBool(ViperParams.PARAM_IEM_ROTATION_INVERT_PITCH,
        "invertPitch", { it.invertPitch }, { copy(invertPitch = it) })
    val invertRoll = rotationBool(ViperParams.PARAM_IEM_ROTATION_INVERT_ROLL,
        "invertRoll", { it.invertRoll }, { copy(invertRoll = it) })
    val invertOverall = rotationBool(ViperParams.PARAM_IEM_ROTATION_INVERT_OVERALL,
        "invertOverall", { it.invertOverall }, { copy(invertOverall = it) })
    val rotationSequence = rotationInt(ViperParams.PARAM_IEM_ROTATION_SEQUENCE,
        "rotationSequence", 1, 0..1, { it.sequence }, { copy(sequence = it) })
    val headphoneEq = int(ViperParams.PARAM_IEM_HEADPHONE_EQ, "headphoneEq", -1,
        { it.iem.decoder.headphoneEq },
        { copy(iem = iem.copy(decoder = iem.decoder.copy(headphoneEq = it))) }, range = -1..22)

    val haloDialogIsolate = haloInt(ViperParams.PARAM_IEM_HALO_DIALOG_ISOLATE,
        "haloDialogIsolate", 0, { it.dialogIsolateThousandths }, { copy(dialogIsolateThousandths = it) })
    val haloDialogAggress = haloInt(ViperParams.PARAM_IEM_HALO_DIALOG_AGGRESS,
        "haloDialogAggress", 500, { it.dialogAggressThousandths }, { copy(dialogAggressThousandths = it) })
    val haloDialogAttack = haloInt(ViperParams.PARAM_IEM_HALO_DIALOG_ATTACK,
        "haloDialogAttack", 300, { it.dialogAttackThousandths }, { copy(dialogAttackThousandths = it) })
    val haloDialogRelease = haloInt(ViperParams.PARAM_IEM_HALO_DIALOG_RELEASE,
        "haloDialogRelease", 750, { it.dialogReleaseThousandths }, { copy(dialogReleaseThousandths = it) })
    val haloDialogMixIn = haloInt(ViperParams.PARAM_IEM_HALO_DIALOG_MIX_IN,
        "haloDialogMixIn", 0, { it.dialogMixInThousandths }, { copy(dialogMixInThousandths = it) })
    val haloDivergence = haloInt(ViperParams.PARAM_IEM_HALO_DIVERGENCE,
        "haloDivergence", 500, { it.divergenceThousandths }, { copy(divergenceThousandths = it) })
    val haloFade = haloInt(ViperParams.PARAM_IEM_HALO_FADE,
        "haloFade", 300, { it.fadeThousandths }, { copy(fadeThousandths = it) })
    val haloFadeRears = haloInt(ViperParams.PARAM_IEM_HALO_FADE_REARS,
        "haloFadeRears", 200, { it.fadeRearsThousandths }, { copy(fadeRearsThousandths = it) })
    val haloDiffusion = haloInt(ViperParams.PARAM_IEM_HALO_DIFFUSION,
        "haloDiffusion", 200, { it.diffusionThousandths }, { copy(diffusionThousandths = it) })
    val haloSpace = haloInt(ViperParams.PARAM_IEM_HALO_SPACE,
        "haloSpace", 800, { it.spaceThousandths }, { copy(spaceThousandths = it) })
    val haloBackBoost = bool(ViperParams.PARAM_IEM_HALO_BACK_BOOST, "haloBackBoost", true,
        { it.iem.halo.backBoost }, { copy(iem = iem.copy(halo = iem.halo.copy(backBoost = it))) })
    val haloRearShelfEnable = bool(ViperParams.PARAM_IEM_HALO_REAR_SHELF_ENABLE,
        "haloRearShelfEnable", true, { it.iem.halo.rearShelfEnable },
        { copy(iem = iem.copy(halo = iem.halo.copy(rearShelfEnable = it))) })
    val haloRearShelfFreq = haloInt(ViperParams.PARAM_IEM_HALO_REAR_SHELF_FREQ,
        "haloRearShelfFreq", 816, { it.rearShelfFreqThousandths }, { copy(rearShelfFreqThousandths = it) })
    val haloRearShelfGain = haloInt(ViperParams.PARAM_IEM_HALO_REAR_SHELF_GAIN,
        "haloRearShelfGain", 475, { it.rearShelfGainThousandths }, { copy(rearShelfGainThousandths = it) })
    val haloLfeEnable = bool(ViperParams.PARAM_IEM_HALO_LFE_ENABLE, "haloLfeEnabled", true,
        { it.iem.halo.lfeEnabled }, { copy(iem = iem.copy(halo = iem.halo.copy(lfeEnabled = it))) })
    val haloLfeFrequency = int(ViperParams.PARAM_IEM_HALO_LFE_FREQUENCY,
        "haloLfeFrequencyMillionths", 750000, { it.iem.halo.lfeFrequencyMillionths },
        { copy(iem = iem.copy(halo = iem.halo.copy(lfeFrequencyMillionths = it))) },
        range = 0..1000000)
    val haloLfeSplit = int(ViperParams.PARAM_IEM_HALO_LFE_SPLIT,
        "haloLfeSplitMillionths", 0, { it.iem.halo.lfeSplitMillionths },
        { copy(iem = iem.copy(halo = iem.halo.copy(lfeSplitMillionths = it))) },
        range = 0..1000000)
    val haloLfeGain = int(ViperParams.PARAM_IEM_HALO_LFE_GAIN,
        "haloLfeGainMillionths", 272727, { it.iem.halo.lfeGainMillionths },
        { copy(iem = iem.copy(halo = iem.halo.copy(lfeGainMillionths = it))) },
        range = 0..1000000)

    private fun stereoInt(param: Int, key: String, default: Int, range: IntRange,
        getValue: (IemStereoState) -> Int,
        setValue: IemStereoState.(Int) -> IemStereoState): IntPref =
        int(param, key, default, { getValue(it.iem.stereo) },
            { copy(iem = iem.copy(stereo = setValue.invoke(iem.stereo, it))) }, range = range)

    private fun granularInt(param: Int, key: String, default: Int, range: IntRange,
        getValue: (IemGranularState) -> Int,
        setValue: IemGranularState.(Int) -> IemGranularState): IntPref =
        int(param, key, default, { getValue(it.iem.granular) },
            { copy(iem = iem.copy(granular = setValue.invoke(iem.granular, it))) }, range = range)

    private fun rotationInt(param: Int, key: String, default: Int, range: IntRange,
        getValue: (IemRotationState) -> Int,
        setValue: IemRotationState.(Int) -> IemRotationState): IntPref =
        int(param, key, default, { getValue(it.iem.rotation) },
            { copy(iem = iem.copy(rotation = setValue.invoke(iem.rotation, it))) }, range = range)

    private fun rotationBool(param: Int, key: String,
        getValue: (IemRotationState) -> Boolean,
        setValue: IemRotationState.(Boolean) -> IemRotationState): BoolPref =
        bool(param, key, false, { getValue(it.iem.rotation) },
            { copy(iem = iem.copy(rotation = setValue.invoke(iem.rotation, it))) })

    private fun haloInt(param: Int, key: String, default: Int,
        getValue: (IemHaloState) -> Int,
        setValue: IemHaloState.(Int) -> IemHaloState): IntPref =
        int(param, key, default, { getValue(it.iem.halo) },
            { copy(iem = iem.copy(halo = setValue.invoke(iem.halo, it))) }, range = 0..1000)
}

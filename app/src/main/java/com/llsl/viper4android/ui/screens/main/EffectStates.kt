package com.llsl.viper4android.ui.screens.main

import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset

data class OutputValues(
    val volume: Int = 100,
    val channelPan: Int = 0,
    val limiter: Int = 100
)

data class OutputState(
    val hp: OutputValues = OutputValues(),
    val spk: OutputValues = OutputValues()
) {
    fun forType(fxType: Int): OutputValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: OutputValues.() -> OutputValues): OutputState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class AgcValues(
    val enabled: Boolean = false,
    val strength: Int = 100,
    val maxGain: Int = 100,
    val outputThreshold: Int = 100
)

data class AgcState(
    val hp: AgcValues = AgcValues(),
    val spk: AgcValues = AgcValues()
) {
    fun forType(fxType: Int): AgcValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: AgcValues.() -> AgcValues): AgcState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class LufsValues(
    val enabled: Boolean = false,
    val target: Int = 140,
    val maxGain: Int = 60,
    val speed: Int = 1
)

data class LufsState(
    val hp: LufsValues = LufsValues(),
    val spk: LufsValues = LufsValues()
) {
    fun forType(fxType: Int): LufsValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: LufsValues.() -> LufsValues): LufsState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class FetValues(
    val enabled: Boolean = false,
    val threshold: Int = -18,
    val ratio: Int = 100,
    val autoKnee: Boolean = true,
    val knee: Int = 0,
    val kneeMulti: Int = 0,
    val autoGain: Boolean = true,
    val gain: Int = 0,
    val autoAttack: Boolean = true,
    val attack: Int = 1,
    val maxAttack: Int = 44,
    val autoRelease: Boolean = true,
    val release: Int = 100,
    val maxRelease: Int = 200,
    val crest: Int = 100,
    val adapt: Int = 50,
    val noClip: Boolean = true
)

data class FetState(
    val hp: FetValues = FetValues(),
    val spk: FetValues = FetValues()
) {
    fun forType(fxType: Int): FetValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: FetValues.() -> FetValues): FetState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class MbcValues(
    val enabled: Boolean = false,
    val bandEnables: String = "1;1;1;1;1",
    val crossovers: String = "120;500;4000;8000",
    val thresholds: String = "-18;-18;-18;-18;-18",
    val ratios: String = "50;50;50;50;50",
    val gains: String = "24;24;24;24;24",
    val knees: String = "0;0;0;0;0",
    val kneeMultis: String = "0;0;0;0;0",
    val attacks: String = "1;1;1;1;1",
    val maxAttacks: String = "44;44;44;44;44",
    val releases: String = "100;100;100;100;100",
    val maxReleases: String = "200;200;200;200;200",
    val crests: String = "100;100;100;100;100",
    val adapts: String = "50;50;50;50;50",
    val autoKnees: String = "1;1;1;1;1",
    val autoGains: String = "1;1;1;1;1",
    val autoAttacks: String = "1;1;1;1;1",
    val autoReleases: String = "1;1;1;1;1",
    val noClips: String = "1;1;1;1;1"
)

data class MbcState(
    val hp: MbcValues = MbcValues(),
    val spk: MbcValues = MbcValues()
) {
    fun forType(fxType: Int): MbcValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: MbcValues.() -> MbcValues): MbcState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class DdcValues(
    val enabled: Boolean = false,
    val device: String = ""
)

data class DdcState(
    val hp: DdcValues = DdcValues(),
    val spk: DdcValues = DdcValues()
) {
    fun forType(fxType: Int): DdcValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: DdcValues.() -> DdcValues): DdcState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class VseValues(
    val enabled: Boolean = false,
    val strength: Int = 7600,
    val exciter: Int = 0
)

data class VseState(
    val hp: VseValues = VseValues(),
    val spk: VseValues = VseValues()
) {
    fun forType(fxType: Int): VseValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: VseValues.() -> VseValues): VseState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class EqValues(
    val enabled: Boolean = false,
    val bandCount: Int = 10,
    val presetId: Long? = null,
    val bands: String = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
    val bandsMap: Map<Int, String> = mapOf(10 to "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;"),
    val presets: List<EqPreset> = emptyList()
)

data class EqState(
    val hp: EqValues = EqValues(),
    val spk: EqValues = EqValues()
) {
    fun forType(fxType: Int): EqValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: EqValues.() -> EqValues): EqState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class DynamicValues(
    val enabled: Boolean = false,
    val bandCount: Int = 3,
    val freqs: String = "60;150;400;1000;2500;5000;8000;12000",
    val qs: String = "100;100;150;150;150;200;200;200",
    val gains: String = "0;0;0;0;0;0;0;0",
    val thresholds: String = "-300;-300;-250;-250;-200;-200;-200;-200",
    val attacks: String = "10;10;10;10;10;10;10;10",
    val releases: String = "100;100;100;100;100;100;100;100",
    val filterTypes: String = "0;0;0;0;0;0;0;0"
)

data class DynamicEqState(
    val hp: DynamicValues = DynamicValues(),
    val spk: DynamicValues = DynamicValues()
) {
    fun forType(fxType: Int): DynamicValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: DynamicValues.() -> DynamicValues): DynamicEqState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class ConvolverValues(
    val enabled: Boolean = false,
    val kernel: String = "",
    val crossChannel: Int = 0
)

data class ConvolverState(
    val hp: ConvolverValues = ConvolverValues(),
    val spk: ConvolverValues = ConvolverValues()
) {
    fun forType(fxType: Int): ConvolverValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: ConvolverValues.() -> ConvolverValues): ConvolverState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class FieldSurroundValues(
    val enabled: Boolean = false,
    val widening: Int = 0,
    val midImage: Int = 5,
    val depth: Int = 0
)

data class FieldSurroundState(
    val hp: FieldSurroundValues = FieldSurroundValues(),
    val spk: FieldSurroundValues = FieldSurroundValues()
) {
    fun forType(fxType: Int): FieldSurroundValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(
        fxType: Int,
        transform: FieldSurroundValues.() -> FieldSurroundValues
    ): FieldSurroundState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class DiffSurroundValues(
    val enabled: Boolean = false,
    val delay: Int = 5,
    val reverse: Boolean = false,
    val wetDryMix: Int = 100,
    val lpCutoff: Int = 0
)

data class DiffSurroundState(
    val hp: DiffSurroundValues = DiffSurroundValues(),
    val spk: DiffSurroundValues = DiffSurroundValues()
) {
    fun forType(fxType: Int): DiffSurroundValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(
        fxType: Int,
        transform: DiffSurroundValues.() -> DiffSurroundValues
    ): DiffSurroundState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class StereoImagerValues(
    val enabled: Boolean = false,
    val lowWidth: Int = 100,
    val midWidth: Int = 100,
    val highWidth: Int = 100,
    val lowCrossover: Int = 200,
    val highCrossover: Int = 4000
)

data class StereoImagerState(
    val hp: StereoImagerValues = StereoImagerValues(),
    val spk: StereoImagerValues = StereoImagerValues()
) {
    fun forType(fxType: Int): StereoImagerValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(
        fxType: Int,
        transform: StereoImagerValues.() -> StereoImagerValues
    ): StereoImagerState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class VheValues(
    val enabled: Boolean = false,
    val quality: Int = 0
)

data class VheState(
    val hp: VheValues = VheValues(),
    val spk: VheValues = VheValues()
) {
    fun forType(fxType: Int): VheValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: VheValues.() -> VheValues): VheState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class ReverbValues(
    val enabled: Boolean = false,
    val roomSize: Int = 0,
    val width: Int = 0,
    val dampening: Int = 0,
    val wet: Int = 0,
    val dry: Int = 50
)

data class ReverbState(
    val hp: ReverbValues = ReverbValues(),
    val spk: ReverbValues = ReverbValues()
) {
    fun forType(fxType: Int): ReverbValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: ReverbValues.() -> ReverbValues): ReverbState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class DynamicSystemValues(
    val enabled: Boolean = false,
    val device: Int = 0,
    val strength: Int = 50,
    val presetId: Long? = null,
    val presets: List<DsPreset> = emptyList(),
    val xLow: Int = 100,
    val xHigh: Int = 5600,
    val yLow: Int = 40,
    val yHigh: Int = 80,
    val sideGainLow: Int = 50,
    val sideGainHigh: Int = 50
)

data class DynamicSystemState(
    val hp: DynamicSystemValues = DynamicSystemValues(),
    val spk: DynamicSystemValues = DynamicSystemValues()
) {
    fun forType(fxType: Int): DynamicSystemValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(
        fxType: Int,
        transform: DynamicSystemValues.() -> DynamicSystemValues
    ): DynamicSystemState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class BassValues(
    val enabled: Boolean = false,
    val mode: Int = 0,
    val frequency: Int = 55,
    val gain: Int = 50,
    val antiPop: Boolean = true
)

data class BassState(
    val hp: BassValues = BassValues(),
    val spk: BassValues = BassValues()
) {
    fun forType(fxType: Int): BassValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: BassValues.() -> BassValues): BassState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class BassMonoValues(
    val enabled: Boolean = false,
    val mode: Int = 0,
    val frequency: Int = 55,
    val gain: Int = 50,
    val antiPop: Boolean = true
)

data class BassMonoState(
    val hp: BassMonoValues = BassMonoValues(),
    val spk: BassMonoValues = BassMonoValues()
) {
    fun forType(fxType: Int): BassMonoValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: BassMonoValues.() -> BassMonoValues): BassMonoState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class ClarityValues(
    val enabled: Boolean = false,
    val mode: Int = 0,
    val gain: Int = 50
)

data class ClarityState(
    val hp: ClarityValues = ClarityValues(),
    val spk: ClarityValues = ClarityValues()
) {
    fun forType(fxType: Int): ClarityValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: ClarityValues.() -> ClarityValues): ClarityState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class CureValues(
    val enabled: Boolean = false,
    val strength: Int = 0
)

data class CureState(
    val hp: CureValues = CureValues(),
    val spk: CureValues = CureValues()
) {
    fun forType(fxType: Int): CureValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: CureValues.() -> CureValues): CureState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class AnalogXValues(
    val enabled: Boolean = false,
    val mode: Int = 0
)

data class AnalogXState(
    val hp: AnalogXValues = AnalogXValues(),
    val spk: AnalogXValues = AnalogXValues()
) {
    fun forType(fxType: Int): AnalogXValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(fxType: Int, transform: AnalogXValues.() -> AnalogXValues): AnalogXState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class TubeSimulatorValues(
    val enabled: Boolean = false
)

data class TubeSimulatorState(
    val hp: TubeSimulatorValues = TubeSimulatorValues(),
    val spk: TubeSimulatorValues = TubeSimulatorValues()
) {
    fun forType(fxType: Int): TubeSimulatorValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(
        fxType: Int,
        transform: TubeSimulatorValues.() -> TubeSimulatorValues
    ): TubeSimulatorState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

data class SpeakerCorrectionValues(
    val enabled: Boolean = false
)

data class SpeakerCorrectionState(
    val hp: SpeakerCorrectionValues = SpeakerCorrectionValues(),
    val spk: SpeakerCorrectionValues = SpeakerCorrectionValues()
) {
    fun forType(fxType: Int): SpeakerCorrectionValues =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) spk else hp

    fun updateType(
        fxType: Int,
        transform: SpeakerCorrectionValues.() -> SpeakerCorrectionValues
    ): SpeakerCorrectionState =
        if (fxType == ViperParams.FX_TYPE_SPEAKER) copy(spk = spk.transform())
        else copy(hp = hp.transform())
}

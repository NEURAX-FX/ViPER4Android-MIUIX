package com.llsl.viper4android.effect

import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset

data class OutputState(
    val volume: Int = 100,
    val channelPan: Int = 0,
    val limiter: Int = 100,
)

data class PlaybackGainControlState(
    val enable: Boolean = false,
    val strength: Int = 100,
    val maxGain: Int = 100,
    val outputThreshold: Int = 100,
)

data class LufsState(
    val enable: Boolean = false,
    val target: Int = 140,
    val maxGain: Int = 60,
    val speed: Int = 1,
)

data class FetCompressorState(
    val enable: Boolean = false,
    val threshold: Int = 100,
    val ratio: Int = 100,
    val kneeAuto: Boolean = true,
    val knee: Int = 0,
    val kneeMulti: Int = 0,
    val gainAuto: Boolean = true,
    val gain: Int = 0,
    val attackAuto: Boolean = true,
    val attack: Int = 20,
    val maxAttack: Int = 80,
    val releaseAuto: Boolean = true,
    val release: Int = 50,
    val maxRelease: Int = 100,
    val crest: Int = 100,
    val adapt: Int = 50,
    val noClip: Boolean = true,
)

data class MultibandCompressorState(
    val enable: Boolean = false,
    val bandEnables: List<Boolean> = listOf(true, true, true, true, true),
    val crossovers: List<Int> = listOf(120, 500, 4000, 8000),
    val thresholds: List<Int> = listOf(-18, -18, -18, -18, -18),
    val ratios: List<Int> = listOf(50, 50, 50, 50, 50),
    val gains: List<Int> = listOf(0, 0, 0, 0, 0),
    val knees: List<Int> = listOf(0, 0, 0, 0, 0),
    val kneeMultis: List<Int> = listOf(0, 0, 0, 0, 0),
    val attacks: List<Int> = listOf(1, 1, 1, 1, 1),
    val maxAttacks: List<Int> = listOf(44, 44, 44, 44, 44),
    val releases: List<Int> = listOf(100, 100, 100, 100, 100),
    val maxReleases: List<Int> = listOf(200, 200, 200, 200, 200),
    val crests: List<Int> = listOf(100, 100, 100, 100, 100),
    val adapts: List<Int> = listOf(50, 50, 50, 50, 50),
    val kneeAutos: List<Boolean> = listOf(true, true, true, true, true),
    val gainAutos: List<Boolean> = listOf(true, true, true, true, true),
    val attackAutos: List<Boolean> = listOf(true, true, true, true, true),
    val releaseAutos: List<Boolean> = listOf(true, true, true, true, true),
    val noClips: List<Boolean> = listOf(true, true, true, true, true),
)

data class DdcState(
    val enable: Boolean = false,
    val device: String = "",
)

data class SpectrumExtensionState(
    val enable: Boolean = false,
    val strength: Int = 7600,
    val exciter: Int = 0,
)

data class EqState(
    val enable: Boolean = false,
    val bandCount: Int = 10,
    val presetId: Long? = null,
    val bands: List<Double> = List(10) { 0.0 },
    val bandsMap: Map<Int, List<Double>> = mapOf(10 to List(10) { 0.0 }),
    val presets: List<EqPreset> = emptyList(),
)

data class DynamicEqState(
    val enable: Boolean = false,
    val bandCount: Int = 3,
    val freqs: List<Int> = listOf(60, 150, 400),
    val qs: List<Int> = listOf(100, 100, 150),
    val gains: List<Int> = listOf(0, 0, 0),
    val thresholds: List<Int> = listOf(-200, -200, -200),
    val attacks: List<Int> = listOf(10, 10, 10),
    val releases: List<Int> = listOf(100, 100, 100),
    val filterTypes: List<Int> = listOf(0, 0, 0),
)

data class ConvolverState(
    val enable: Boolean = false,
    val kernelFile: String = "",
    val crossChannel: Int = 0,
    val wet: Int = 100,
    val outputGain: Int = 0,
    val routing: Int = 0,
    val crossDelay100Ns: Int = 3125,
)

data class FieldSurroundState(
    val enable: Boolean = false,
    val widening: Int = 0,
    val midImage: Int = 5,
    val depth: Int = 0,
)

data class DiffSurroundState(
    val enable: Boolean = false,
    val delay: Int = 5,
    val reverse: Boolean = false,
    val wetDryMix: Int = 100,
    val lpCutoff: Int = 0,
)

data class StereoImagerState(
    val enable: Boolean = false,
    val lowWidth: Int = 100,
    val midWidth: Int = 100,
    val highWidth: Int = 100,
    val lowCrossover: Int = 200,
    val highCrossover: Int = 4000,
)

data class HeadphoneSurroundState(
    val enable: Boolean = false,
    val quality: Int = 0,
)

data class ReverbState(
    val enable: Boolean = false,
    val roomSize: Int = 0,
    val width: Int = 0,
    val damp: Int = 50,
    val wet: Int = 0,
    val dry: Int = 100,
)

data class DynamicSystemState(
    val enable: Boolean = false,
    val xLow: Int = 0,
    val xHigh: Int = 0,
    val yLow: Int = 0,
    val yHigh: Int = 0,
    val sideGainLow: Int = 0,
    val sideGainHigh: Int = 0,
    val strength: Int = 0,
    val device: Int = 0,
    val presetId: Long? = null,
    val presets: List<DsPreset> = emptyList(),
)

data class PsychoacousticBassState(
    val enable: Boolean = false,
    val cutoff: Int = 80,
    val intensity: Int = 50,
    val harmonicOrder: Int = 3,
    val originalLevel: Int = 100,
)

data class BassState(
    val enable: Boolean = false,
    val mode: Int = 0,
    val frequency: Int = 60,
    val gain: Int = 0,
    val antiPop: Boolean = false,
)

data class BassMonoState(
    val enable: Boolean = false,
    val mode: Int = 0,
    val frequency: Int = 60,
    val gain: Int = 0,
    val antiPop: Boolean = false,
)

data class ClarityState(
    val enable: Boolean = false,
    val mode: Int = 0,
    val gain: Int = 0,
)

data class CureState(
    val enable: Boolean = false,
    val crossfeedPreset: Int = 0,
)

data class AnalogXState(
    val enable: Boolean = false,
    val mode: Int = 0,
)

data class TubeSimulatorState(
    val enable: Boolean = false,
)

data class SpeakerCorrectionState(
    val enable: Boolean = false,
)

data class IemGeneralState(
    val enable: Boolean = false,
    val encoderMode: Int = 0,
    val order: Int = 3,
    val renderMode: Int = 2,
)

data class IemHaloState(
    val dialogIsolateThousandths: Int = 0,
    val dialogAggressThousandths: Int = 500,
    val dialogAttackThousandths: Int = 300,
    val dialogReleaseThousandths: Int = 750,
    val dialogMixInThousandths: Int = 0,
    val divergenceThousandths: Int = 500,
    val fadeThousandths: Int = 300,
    val fadeRearsThousandths: Int = 200,
    val diffusionThousandths: Int = 200,
    val spaceThousandths: Int = 800,
    val backBoost: Boolean = true,
    val rearShelfEnable: Boolean = true,
    val rearShelfFreqThousandths: Int = 816,
    val rearShelfGainThousandths: Int = 475,
)

data class IemStereoState(
    val azimuthCentidegrees: Int = 0,
    val elevationCentidegrees: Int = 0,
    val rollCentidegrees: Int = 0,
    val widthCentidegrees: Int = 6000,
    val sampleWise: Boolean = false,
)

data class IemMultiState(
    val azimuthCentidegrees: List<Int> = listOf(-3000, 3000),
    val elevationCentidegrees: List<Int> = listOf(0, 0),
    val gainDecidb: List<Int> = listOf(0, 0),
    val mute: List<Boolean> = listOf(false, false),
)

data class IemGranularState(
    val azimuthCentidegrees: Int = 0,
    val elevationCentidegrees: Int = 0,
    val shapeTenths: Int = 0,
    val sizeCentidegrees: Int = 18000,
    val rollCentidegrees: Int = 0,
    val widthCentidegrees: Int = 0,
    val deltaTimeUs: Int = 5000,
    val deltaTimeModTenthsPercent: Int = 0,
    val grainLengthUs: Int = 250000,
    val grainLengthModTenthsPercent: Int = 0,
    val readPositionUs: Int = 0,
    val positionModUs: Int = 50000,
    val pitchMilliSemitones: Int = 0,
    val pitchModMilliSemitones: Int = 0,
    val attackTenthsPercent: Int = 500,
    val attackModTenthsPercent: Int = 0,
    val decayTenthsPercent: Int = 500,
    val decayModTenthsPercent: Int = 0,
    val mixTenthsPercent: Int = 500,
    val sourceProbabilityHundredths: Int = 0,
    val spatialMode: Int = 0,
    val sampleWise: Boolean = false,
)

data class IemRotationState(
    val yawCentidegrees: Int = 0,
    val pitchCentidegrees: Int = 0,
    val rollCentidegrees: Int = 0,
    val invertYaw: Boolean = false,
    val invertPitch: Boolean = false,
    val invertRoll: Boolean = false,
    val invertOverall: Boolean = false,
    val sequence: Int = 1,
)

data class IemDecoderState(
    val headphoneEq: Int = -1,
)

data class IemOutputState(
    val wetPercent: Int = 100,
    val gainDecidb: Int = 0,
    val latencyProfile: Int = 1,
    val limiterEnabled: Boolean = true,
    val limiterCeilingCentidb: Int = -30,
)

data class IemState(
    val general: IemGeneralState = IemGeneralState(),
    val stereo: IemStereoState = IemStereoState(),
    val multi: IemMultiState = IemMultiState(),
    val granular: IemGranularState = IemGranularState(),
    val rotation: IemRotationState = IemRotationState(),
    val decoder: IemDecoderState = IemDecoderState(),
    val halo: IemHaloState = IemHaloState(),
    val output: IemOutputState = IemOutputState(),
    val freeze: Boolean = false,
)

fun normalizeIemState(state: IemState): IemState {
    fun <T> pair(values: List<T>, defaults: List<T>): List<T> =
        List(2) { index -> values.getOrNull(index) ?: defaults[index] }
    val defaults = IemMultiState()
    return state.copy(
        general = state.general.copy(
            encoderMode = state.general.encoderMode.coerceIn(0, 3),
            order = state.general.order.coerceIn(1, 3),
            renderMode = state.general.renderMode.coerceIn(0, 2),
        ),
        multi = state.multi.copy(
            azimuthCentidegrees = pair(state.multi.azimuthCentidegrees, defaults.azimuthCentidegrees),
            elevationCentidegrees = pair(state.multi.elevationCentidegrees, defaults.elevationCentidegrees),
            gainDecidb = pair(state.multi.gainDecidb, defaults.gainDecidb),
            mute = pair(state.multi.mute, defaults.mute),
        ),
        halo = state.halo.copy(
            dialogIsolateThousandths = state.halo.dialogIsolateThousandths.coerceIn(0, 1000),
            dialogAggressThousandths = state.halo.dialogAggressThousandths.coerceIn(0, 1000),
            dialogAttackThousandths = state.halo.dialogAttackThousandths.coerceIn(0, 1000),
            dialogReleaseThousandths = state.halo.dialogReleaseThousandths.coerceIn(0, 1000),
            dialogMixInThousandths = state.halo.dialogMixInThousandths.coerceIn(0, 1000),
            divergenceThousandths = state.halo.divergenceThousandths.coerceIn(0, 1000),
            fadeThousandths = state.halo.fadeThousandths.coerceIn(0, 1000),
            fadeRearsThousandths = state.halo.fadeRearsThousandths.coerceIn(0, 1000),
            diffusionThousandths = state.halo.diffusionThousandths.coerceIn(0, 1000),
            spaceThousandths = state.halo.spaceThousandths.coerceIn(0, 1000),
            rearShelfFreqThousandths = state.halo.rearShelfFreqThousandths.coerceIn(0, 1000),
            rearShelfGainThousandths = state.halo.rearShelfGainThousandths.coerceIn(0, 1000),
        ),
        freeze = false,
    )
}

data class EffectState(
    val masterEnable: Boolean = false,
    val out: OutputState = OutputState(),
    val playbackGainControl: PlaybackGainControlState = PlaybackGainControlState(),
    val lufs: LufsState = LufsState(),
    val fetCompressor: FetCompressorState = FetCompressorState(),
    val multibandCompressor: MultibandCompressorState = MultibandCompressorState(),
    val ddc: DdcState = DdcState(),
    val spectrumExtension: SpectrumExtensionState = SpectrumExtensionState(),
    val eq: EqState = EqState(),
    val dynamicEq: DynamicEqState = DynamicEqState(),
    val convolver: ConvolverState = ConvolverState(),
    val fieldSurround: FieldSurroundState = FieldSurroundState(),
    val diffSurround: DiffSurroundState = DiffSurroundState(),
    val stereoImager: StereoImagerState = StereoImagerState(),
    val headphoneSurround: HeadphoneSurroundState = HeadphoneSurroundState(),
    val reverb: ReverbState = ReverbState(),
    val dynamicSystem: DynamicSystemState = DynamicSystemState(),
    val psychoacousticBass: PsychoacousticBassState = PsychoacousticBassState(),
    val bass: BassState = BassState(),
    val bassMono: BassMonoState = BassMonoState(),
    val clarity: ClarityState = ClarityState(),
    val cure: CureState = CureState(),
    val analogX: AnalogXState = AnalogXState(),
    val tubeSimulator: TubeSimulatorState = TubeSimulatorState(),
    val speakerCorrection: SpeakerCorrectionState = SpeakerCorrectionState(),
    val iem: IemState = IemState(),
    val activeDeviceName: String = "",
    val activeDeviceId: String = "",
)

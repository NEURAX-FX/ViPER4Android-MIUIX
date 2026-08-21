package com.llsl.viper4android.viper

import com.llsl.viper4android.R
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.EqBandSpec
import com.llsl.viper4android.effect.ParamRaw
import com.llsl.viper4android.effect.loadEffectPrefs
import com.llsl.viper4android.utils.FileLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ViperDispatcher {
    internal sealed interface IemWrite {
        data class Scalar(val param: Int, val value: Int) : IemWrite
        data class Indexed(val param: Int, val index: Int, val value: Int) : IemWrite
    }
    data class BuiltinEqPreset(
        val key: String,
        val nameRes: Int,
        val bands10: String,
        val bands15: String,
        val bands25: String,
        val bands31: String,
    )

    val BUILTIN_EQ_PRESETS: List<BuiltinEqPreset> =
        listOf(
            BuiltinEqPreset(
                key = "eq_preset_acoustic",
                nameRes = R.string.eq_preset_acoustic,
                bands10 = "4.5;4.5;3.5;1.2;1.0;0.5;1.4;1.75;3.5;2.5;",
                bands15 = "4.5;4.5;4.5;4.0;2.5;1.0;1.0;1.0;0.5;1.0;1.5;2.0;3.0;3.0;2.5;",
                bands25 = "4.5;4.5;4.5;4.5;4.0;4.0;3.5;2.5;1.0;1.0;1.0;1.0;0.5;0.5;1.0;1.0;1.5;1.5;2.0;2.5;3.5;3.0;3.0;2.5;2.5;",
                bands31 = "4.5;4.5;4.5;4.5;4.5;4.5;4.0;4.0;3.5;2.5;2.0;1.0;1.0;1.0;1.0;1.0;0.5;0.5;1.0;1.0;1.5;1.5;1.5;2.0;2.5;3.0;3.5;3.0;3.0;2.5;2.5;",
            ),
            BuiltinEqPreset(
                key = "eq_preset_bass_booster",
                nameRes = R.string.eq_preset_bass_booster,
                bands10 = "6.0;4.0;2.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
                bands15 = "6.0;5.5;4.0;2.5;1.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
                bands25 = "6.0;6.0;5.5;4.5;3.5;2.5;2.0;1.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
                bands31 = "6.0;6.0;6.0;5.5;4.5;4.0;3.5;2.5;2.0;1.5;0.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
            ),
            BuiltinEqPreset(
                key = "eq_preset_bass_reducer",
                nameRes = R.string.eq_preset_bass_reducer,
                bands10 = "-6.0;-4.0;-2.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
                bands15 = "-6.0;-5.5;-4.0;-2.5;-1.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
                bands25 = "-6.0;-6.0;-5.5;-4.5;-3.5;-2.5;-2.0;-1.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
                bands31 = "-6.0;-6.0;-6.0;-5.5;-4.5;-4.0;-3.5;-2.5;-2.0;-1.5;-0.5;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
            ),
            BuiltinEqPreset(
                key = "eq_preset_classical",
                nameRes = R.string.eq_preset_classical,
                bands10 = "0.0;0.0;0.0;0.0;0.0;0.0;-3.0;-3.0;-3.0;-5.0;",
                bands15 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-2.0;-3.0;-3.0;-3.0;-3.5;-5.0;",
                bands25 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-1.0;-2.0;-3.0;-3.0;-3.0;-3.0;-3.0;-3.5;-4.5;-5.0;-5.0;",
                bands31 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-1.0;-2.0;-3.0;-3.0;-3.0;-3.0;-3.0;-3.0;-3.0;-3.5;-4.5;-5.0;-5.0;",
            ),
            BuiltinEqPreset(
                key = "eq_preset_deep",
                nameRes = R.string.eq_preset_deep,
                bands10 = "3.0;2.0;1.0;0.5;0.5;0.0;-1.0;-2.0;-3.0;-3.5;",
                bands15 = "3.0;2.5;2.0;1.5;1.0;0.5;0.5;0.5;0.0;-0.5;-1.5;-2.0;-2.5;-3.0;-3.5;",
                bands25 = "3.0;3.0;2.5;2.5;1.5;1.5;1.0;1.0;0.5;0.5;0.5;0.5;0.0;0.0;-0.5;-0.5;-1.5;-1.5;-2.0;-2.5;-3.0;-3.0;-3.5;-3.5;-3.5;",
                bands31 = "3.0;3.0;3.0;2.5;2.5;2.0;1.5;1.5;1.0;1.0;0.5;0.5;0.5;0.5;0.5;0.5;0.0;0.0;-0.5;-0.5;-1.0;-1.5;-1.5;-2.0;-2.5;-2.5;-3.0;-3.0;-3.5;-3.5;-3.5;",
            ),
            BuiltinEqPreset(
                key = "eq_preset_flat",
                nameRes = R.string.eq_preset_flat,
                bands10 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
                bands15 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
                bands25 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
                bands31 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
            ),
            BuiltinEqPreset(
                key = "eq_preset_rnb",
                nameRes = R.string.eq_preset_rnb,
                bands10 = "3.0;6.0;4.0;1.0;-1.0;-0.5;1.0;1.5;2.5;3.0;",
                bands15 = "3.0;4.0;6.0;4.5;3.0;1.0;-0.5;-1.0;-0.5;0.5;1.0;1.5;2.0;2.5;3.0;",
                bands25 = "3.0;3.0;4.0;5.0;5.5;4.5;4.0;3.0;1.0;0.5;-0.5;-1.0;-0.5;-0.5;0.0;0.5;1.0;1.5;1.5;2.0;2.5;2.5;3.0;3.0;3.0;",
                bands31 = "3.0;3.0;3.0;4.0;5.0;6.0;5.5;4.5;4.0;3.0;2.0;1.0;0.5;-0.5;-1.0;-1.0;-0.5;-0.5;0.0;0.5;1.0;1.0;1.5;1.5;2.0;2.0;2.5;2.5;3.0;3.0;3.0;",
            ),
            BuiltinEqPreset(
                key = "eq_preset_rock",
                nameRes = R.string.eq_preset_rock,
                bands10 = "4.0;3.0;1.0;0.0;-0.5;0.0;1.5;2.5;3.5;4.0;",
                bands15 = "4.0;3.5;3.0;1.5;0.5;0.0;-0.5;-0.5;0.0;1.0;2.0;2.5;3.0;3.5;4.0;",
                bands25 = "4.0;4.0;3.5;3.5;2.5;1.5;1.0;0.5;0.0;0.0;-0.5;-0.5;0.0;0.0;0.5;1.0;2.0;2.0;2.5;3.0;3.5;3.5;4.0;4.0;4.0;",
                bands31 = "4.0;4.0;4.0;3.5;3.5;3.0;2.5;1.5;1.0;0.5;0.5;0.0;0.0;-0.5;-0.5;-0.5;0.0;0.0;0.5;1.0;1.5;2.0;2.0;2.5;3.0;3.0;3.5;3.5;4.0;4.0;4.0;",
            ),
            BuiltinEqPreset(
                key = "eq_preset_small_speakers",
                nameRes = R.string.eq_preset_small_speakers,
                bands10 = "3.0;2.0;1.5;1.0;0.5;-0.5;-1.5;-2.0;-3.0;-3.5;",
                bands15 = "3.0;2.5;2.0;1.5;1.5;1.0;0.5;0.0;-0.5;-1.0;-1.5;-2.0;-2.5;-3.0;-3.5;",
                bands25 = "3.0;3.0;2.5;2.5;2.0;1.5;1.5;1.5;1.0;1.0;0.5;0.5;0.0;-0.5;-1.0;-1.0;-1.5;-2.0;-2.0;-2.5;-3.0;-3.0;-3.5;-3.5;-3.5;",
                bands31 = "3.0;3.0;3.0;2.5;2.5;2.0;2.0;1.5;1.5;1.5;1.0;1.0;1.0;0.5;0.5;0.0;0.0;-0.5;-1.0;-1.0;-1.5;-1.5;-2.0;-2.0;-2.5;-2.5;-3.0;-3.0;-3.5;-3.5;-3.5;",
            ),
            BuiltinEqPreset(
                key = "eq_preset_treble_booster",
                nameRes = R.string.eq_preset_treble_booster,
                bands10 = "0.0;0.0;0.0;0.0;0.0;1.0;2.0;3.0;4.0;5.0;",
                bands15 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.5;1.0;1.5;2.5;3.0;3.5;4.5;5.0;",
                bands25 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.5;1.0;1.5;1.5;2.5;2.5;3.0;3.5;4.0;4.5;4.5;5.0;5.0;",
                bands31 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.5;0.5;1.0;1.5;1.5;2.0;2.5;2.5;3.0;3.5;3.5;4.0;4.5;4.5;5.0;5.0;",
            ),
            BuiltinEqPreset(
                key = "eq_preset_treble_reducer",
                nameRes = R.string.eq_preset_treble_reducer,
                bands10 = "0.0;0.0;0.0;0.0;0.0;-1.0;-2.0;-3.0;-4.0;-5.0;",
                bands15 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;-0.5;-1.0;-1.5;-2.5;-3.0;-3.5;-4.5;-5.0;",
                bands25 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-0.5;-1.0;-1.5;-1.5;-2.5;-2.5;-3.0;-3.5;-4.0;-4.5;-4.5;-5.0;-5.0;",
                bands31 = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;-0.5;-0.5;-1.0;-1.5;-1.5;-2.0;-2.5;-2.5;-3.0;-3.5;-3.5;-4.0;-4.5;-4.5;-5.0;-5.0;",
            ),
            BuiltinEqPreset(
                key = "eq_preset_vocal_booster",
                nameRes = R.string.eq_preset_vocal_booster,
                bands10 = "-1.0;-0.5;0.0;1.5;3.0;3.0;2.0;1.0;0.0;-1.0;",
                bands15 = "-1.0;-1.0;-0.5;0.0;0.5;1.5;2.5;3.0;3.0;2.5;1.5;1.0;0.5;-0.5;-1.0;",
                bands25 = "-1.0;-1.0;-1.0;-0.5;-0.5;0.0;0.0;0.5;1.5;2.0;2.5;3.0;3.0;3.0;2.5;2.5;1.5;1.5;1.0;0.5;0.0;-0.5;-0.5;-1.0;-1.0;",
                bands31 = "-1.0;-1.0;-1.0;-1.0;-0.5;-0.5;-0.5;0.0;0.0;0.5;1.0;1.5;2.0;2.5;3.0;3.0;3.0;3.0;2.5;2.5;2.0;1.5;1.5;1.0;0.5;0.5;0.0;-0.5;-0.5;-1.0;-1.0;",
            ),
        )

    val EQ_PRESET_NAME_RES: Map<String, Int> =
        BUILTIN_EQ_PRESETS.associate { it.key to it.nameRes }

    data class BuiltinDsPreset(
        val key: String,
        val nameRes: Int,
        val xLow: Int,
        val xHigh: Int,
        val yLow: Int,
        val yHigh: Int,
        val sideGainLow: Int,
        val sideGainHigh: Int,
    )

    val BUILTIN_DS_PRESETS: List<BuiltinDsPreset> =
        listOf(
            BuiltinDsPreset(
                key = "ds_device_extreme_headphone_v2",
                nameRes = R.string.ds_device_extreme_headphone_v2,
                xLow = 140,
                xHigh = 6200,
                yLow = 40,
                yHigh = 60,
                sideGainLow = 10,
                sideGainHigh = 80,
            ),
            BuiltinDsPreset(
                key = "ds_device_high_end_headphone_v2",
                nameRes = R.string.ds_device_high_end_headphone_v2,
                xLow = 180,
                xHigh = 5800,
                yLow = 55,
                yHigh = 80,
                sideGainLow = 10,
                sideGainHigh = 70,
            ),
            BuiltinDsPreset(
                key = "ds_device_common_headphone_v2",
                nameRes = R.string.ds_device_common_headphone_v2,
                xLow = 300,
                xHigh = 5600,
                yLow = 60,
                yHigh = 105,
                sideGainLow = 10,
                sideGainHigh = 50,
            ),
            BuiltinDsPreset(
                key = "ds_device_low_end_headphone_v2",
                nameRes = R.string.ds_device_low_end_headphone_v2,
                xLow = 600,
                xHigh = 5400,
                yLow = 60,
                yHigh = 105,
                sideGainLow = 10,
                sideGainHigh = 20,
            ),
            BuiltinDsPreset(
                key = "ds_device_common_earphone_v2",
                nameRes = R.string.ds_device_common_earphone_v2,
                xLow = 100,
                xHigh = 5600,
                yLow = 40,
                yHigh = 80,
                sideGainLow = 50,
                sideGainHigh = 50,
            ),
            BuiltinDsPreset(
                key = "ds_device_extreme_headphone_v1",
                nameRes = R.string.ds_device_extreme_headphone_v1,
                xLow = 1200,
                xHigh = 6200,
                yLow = 40,
                yHigh = 80,
                sideGainLow = 0,
                sideGainHigh = 20,
            ),
            BuiltinDsPreset(
                key = "ds_device_high_end_headphone_v1",
                nameRes = R.string.ds_device_high_end_headphone_v1,
                xLow = 1000,
                xHigh = 6200,
                yLow = 40,
                yHigh = 80,
                sideGainLow = 0,
                sideGainHigh = 10,
            ),
            BuiltinDsPreset(
                key = "ds_device_common_headphone_v1",
                nameRes = R.string.ds_device_common_headphone_v1,
                xLow = 800,
                xHigh = 6200,
                yLow = 40,
                yHigh = 80,
                sideGainLow = 10,
                sideGainHigh = 0,
            ),
            BuiltinDsPreset(
                key = "ds_device_common_earphone_v1",
                nameRes = R.string.ds_device_common_earphone_v1,
                xLow = 400,
                xHigh = 6200,
                yLow = 40,
                yHigh = 80,
                sideGainLow = 10,
                sideGainHigh = 0,
            ),
        )

    val DS_PRESET_NAME_RES: Map<String, Int> =
        BUILTIN_DS_PRESETS.associate { it.key to it.nameRes }

    val EQ_BAND_LABELS_10 =
        listOf(
            "31Hz",
            "62Hz",
            "125Hz",
            "250Hz",
            "500Hz",
            "1kHz",
            "2kHz",
            "4kHz",
            "8kHz",
            "16kHz",
        )
    val EQ_BAND_LABELS_15 =
        listOf(
            "25Hz",
            "40Hz",
            "63Hz",
            "100Hz",
            "160Hz",
            "250Hz",
            "400Hz",
            "630Hz",
            "1kHz",
            "1.6kHz",
            "2.5kHz",
            "4kHz",
            "6.3kHz",
            "10kHz",
            "16kHz",
        )
    val EQ_BAND_LABELS_25 =
        listOf(
            "20Hz",
            "31Hz",
            "40Hz",
            "50Hz",
            "80Hz",
            "100Hz",
            "125Hz",
            "160Hz",
            "250Hz",
            "315Hz",
            "400Hz",
            "500Hz",
            "800Hz",
            "1kHz",
            "1.25kHz",
            "1.6kHz",
            "2.5kHz",
            "3.15kHz",
            "4kHz",
            "5kHz",
            "8kHz",
            "10kHz",
            "12.5kHz",
            "16kHz",
            "20kHz",
        )
    val EQ_BAND_LABELS_31 =
        listOf(
            "20Hz",
            "25Hz",
            "31Hz",
            "40Hz",
            "50Hz",
            "63Hz",
            "80Hz",
            "100Hz",
            "125Hz",
            "160Hz",
            "200Hz",
            "250Hz",
            "315Hz",
            "400Hz",
            "500Hz",
            "630Hz",
            "800Hz",
            "1kHz",
            "1.25kHz",
            "1.6kHz",
            "2kHz",
            "2.5kHz",
            "3.15kHz",
            "4kHz",
            "5kHz",
            "6.3kHz",
            "8kHz",
            "10kHz",
            "12.5kHz",
            "16kHz",
            "20kHz",
        )

    fun eqBandLabelsForCount(count: Int): List<String> =
        when (count) {
            15 -> EQ_BAND_LABELS_15
            25 -> EQ_BAND_LABELS_25
            31 -> EQ_BAND_LABELS_31
            else -> EQ_BAND_LABELS_10
        }

    private fun ensureBandCount(
        rawBands: List<Double>,
        expectedCount: Int,
    ): List<Double> =
        if (rawBands.size != expectedCount) {
            List(expectedCount) { 0.0 }
        } else {
            rawBands
        }

    // Labels are derived from the driver's fixed band table so a band index can never be
    // labelled with a frequency the DSP does not use.
    val EQ_GRAPH_LABELS_10 = EqBandSpec.labelsFor(10)
    val EQ_GRAPH_LABELS_15 = EqBandSpec.labelsFor(15)
    val EQ_GRAPH_LABELS_25 = EqBandSpec.labelsFor(25)
    val EQ_GRAPH_LABELS_31 = EqBandSpec.labelsFor(31)

    fun eqGraphLabelsForCount(count: Int): List<String> = EqBandSpec.labelsFor(count)

    fun dispatchFullState(
        effect: ParamSink,
        state: EffectState,
        masterEnabled: Boolean,
    ) {
        FileLogger.d(
            "Dispatch",
            "Dispatch: fullState master=${if (masterEnabled) "ON" else "OFF"}",
        )
        dispatchState(effect, state)
    }

    fun dispatchState(
        effect: ParamSink,
        state: EffectState,
    ) {
        // Output
        effect.setParameter(ViperParams.PARAM_MASTER_LIMITER_OUTPUT_VOLUME, state.out.volume)
        effect.setParameter(ViperParams.PARAM_MASTER_LIMITER_CHANNEL_PAN, state.out.channelPan)
        effect.setParameter(ViperParams.PARAM_MASTER_LIMITER_THRESHOLD, state.out.limiter)

        // AGC
        effect.setParameter(ViperParams.PARAM_PLAYBACK_GAIN_CONTROL_ENABLE, if (state.playbackGainControl.enable) 1 else 0)
        if (state.playbackGainControl.enable) {
            effect.setParameter(ViperParams.PARAM_PLAYBACK_GAIN_CONTROL_STRENGTH, state.playbackGainControl.strength)
            effect.setParameter(ViperParams.PARAM_PLAYBACK_GAIN_CONTROL_MAX_GAIN, state.playbackGainControl.maxGain)
            effect.setParameter(ViperParams.PARAM_PLAYBACK_GAIN_CONTROL_OUTPUT_THRESHOLD, state.playbackGainControl.outputThreshold)
        }

        // LUFS
        effect.setParameter(ViperParams.PARAM_LUFS_ENABLE, if (state.lufs.enable) 1 else 0)
        if (state.lufs.enable) {
            effect.setParameter(ViperParams.PARAM_LUFS_TARGET, state.lufs.target)
            effect.setParameter(ViperParams.PARAM_LUFS_MAX_GAIN, state.lufs.maxGain)
            effect.setParameter(ViperParams.PARAM_LUFS_SPEED, state.lufs.speed)
        }

        // FET Compressor
        effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_ENABLE, if (state.fetCompressor.enable) 100 else 0)
        if (state.fetCompressor.enable) {
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_THRESHOLD, ParamRaw.fetCompressorThreshold(state.fetCompressor.threshold))
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_RATIO, state.fetCompressor.ratio)
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_KNEE_AUTO, if (state.fetCompressor.kneeAuto) 100 else 0)
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_KNEE, ParamRaw.fetCompressorKnee(state.fetCompressor.knee))
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_KNEE_MULTI, state.fetCompressor.kneeMulti)
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_GAIN_AUTO, if (state.fetCompressor.gainAuto) 100 else 0)
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_GAIN, ParamRaw.fetCompressorGain(state.fetCompressor.gain))
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_ATTACK_AUTO, if (state.fetCompressor.attackAuto) 100 else 0)
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_ATTACK, ParamRaw.fetCompressorAttackMs(state.fetCompressor.attack))
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_MAX_ATTACK, ParamRaw.fetCompressorAttackMs(state.fetCompressor.maxAttack))
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_RELEASE_AUTO, if (state.fetCompressor.releaseAuto) 100 else 0)
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_RELEASE, ParamRaw.fetCompressorReleaseMs(state.fetCompressor.release))
            effect.setParameter(
                ViperParams.PARAM_FET_COMPRESSOR_MAX_RELEASE,
                ParamRaw.fetCompressorReleaseMs(state.fetCompressor.maxRelease),
            )
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_CREST, ParamRaw.fetCompressorReleaseMs(state.fetCompressor.crest))
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_ADAPT, state.fetCompressor.adapt)
            effect.setParameter(ViperParams.PARAM_FET_COMPRESSOR_NO_CLIP, if (state.fetCompressor.noClip) 100 else 0)
        }

        // Multiband Compressor
        effect.setParameter(ViperParams.PARAM_MULTIBAND_COMPRESSOR_ENABLE, if (state.multibandCompressor.enable) 1 else 0)
        if (state.multibandCompressor.enable) {
            effect.setParameter(ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_COUNT, 5)
            val mbc = state.multibandCompressor
            val mbcCrossoverDefaults = intArrayOf(120, 500, 4000, 8000)
            for (i in mbcCrossoverDefaults.indices) {
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_CROSSOVER_FREQUENCY,
                    i,
                    mbc.crossovers.getOrElse(i) { mbcCrossoverDefaults[i] },
                )
            }
            for (b in 0 until 5) {
                val bandEnabled = mbc.bandEnables.getOrElse(b) { true }
                effect.setParameter(ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_ENABLE, b, if (bandEnabled) 100 else 0)
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_THRESHOLD,
                    b,
                    ParamRaw.fetCompressorThreshold(mbc.thresholds.getOrElse(b) { -18 }),
                )
                effect.setParameter(ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_RATIO, b, mbc.ratios.getOrElse(b) { 50 })
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_GAIN,
                    b,
                    ParamRaw.fetCompressorGain(mbc.gains.getOrElse(b) { 0 }),
                )
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_ATTACK,
                    b,
                    ParamRaw.fetCompressorAttackMs(mbc.attacks.getOrElse(b) { 1 }),
                )
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_RELEASE,
                    b,
                    ParamRaw.fetCompressorReleaseMs(mbc.releases.getOrElse(b) { 100 }),
                )
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_KNEE,
                    b,
                    ParamRaw.fetCompressorKnee(mbc.knees.getOrElse(b) { 0 }),
                )
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_GAIN_AUTO,
                    b,
                    if (mbc.gainAutos.getOrElse(b) { true }) 100 else 0,
                )
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_ATTACK_AUTO,
                    b,
                    if (mbc.attackAutos.getOrElse(b) { true }) 100 else 0,
                )
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_RELEASE_AUTO,
                    b,
                    if (mbc.releaseAutos.getOrElse(b) { true }) 100 else 0,
                )
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_KNEE_AUTO,
                    b,
                    if (mbc.kneeAutos.getOrElse(b) { true }) 100 else 0,
                )
                effect.setParameter(ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_KNEE_MULTI, b, mbc.kneeMultis.getOrElse(b) { 0 })
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_MAX_ATTACK,
                    b,
                    ParamRaw.fetCompressorAttackMs(mbc.maxAttacks.getOrElse(b) { 44 }),
                )
                effect
                    .setParameter(
                        ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_MAX_RELEASE,
                        b,
                        ParamRaw.fetCompressorReleaseMs(mbc.maxReleases.getOrElse(b) { 200 }),
                    )
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_CREST,
                    b,
                    ParamRaw.fetCompressorReleaseMs(mbc.crests.getOrElse(b) { 100 }),
                )
                effect.setParameter(ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_ADAPT, b, mbc.adapts.getOrElse(b) { 50 })
                effect.setParameter(
                    ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_NO_CLIP,
                    b,
                    if (mbc.noClips.getOrElse(b) { true }) 100 else 0,
                )
            }
        }

        // DDC
        effect.setParameter(ViperParams.PARAM_DDC_ENABLE, if (state.ddc.enable) 1 else 0)

        // Spectrum Extension
        effect.setParameter(ViperParams.PARAM_SPECTRUM_EXTENSION_ENABLE, if (state.spectrumExtension.enable) 1 else 0)
        if (state.spectrumExtension.enable) {
            effect.setParameter(ViperParams.PARAM_SPECTRUM_EXTENSION_STRENGTH, state.spectrumExtension.strength)
            effect.setParameter(
                ViperParams.PARAM_SPECTRUM_EXTENSION_EXCITER,
                ParamRaw.spectrumExtensionExciter(state.spectrumExtension.exciter),
            )
        }

        // EQ
        effect.setParameter(ViperParams.PARAM_EQUALIZER_ENABLE, if (state.eq.enable) 1 else 0)
        if (state.eq.enable) {
            effect.setParameter(ViperParams.PARAM_EQUALIZER_BAND_COUNT, state.eq.bandCount)
            effect.setParameter(ViperParams.PARAM_EQUALIZER_BAND_LEVELS, eqBandLevelsToBytes(state.eq.bands))
        }

        // Dynamic EQ
        effect.setParameter(ViperParams.PARAM_DYNAMIC_EQ_ENABLE, if (state.dynamicEq.enable) 1 else 0)
        if (state.dynamicEq.enable) {
            val deq = state.dynamicEq
            for (b in 0 until deq.bandCount) {
                effect.setParameter(ViperParams.PARAM_DYNAMIC_EQ_BAND_FREQUENCY, b, deq.freqs.getOrElse(b) { 1000 })
                effect.setParameter(ViperParams.PARAM_DYNAMIC_EQ_BAND_Q, b, deq.qs.getOrElse(b) { 150 })
                effect.setParameter(ViperParams.PARAM_DYNAMIC_EQ_BAND_GAIN, b, deq.gains.getOrElse(b) { 0 })
                effect.setParameter(ViperParams.PARAM_DYNAMIC_EQ_BAND_THRESHOLD, b, deq.thresholds.getOrElse(b) { -300 })
                effect.setParameter(ViperParams.PARAM_DYNAMIC_EQ_BAND_ATTACK, b, deq.attacks.getOrElse(b) { 10 })
                effect.setParameter(ViperParams.PARAM_DYNAMIC_EQ_BAND_RELEASE, b, deq.releases.getOrElse(b) { 100 })
                effect.setParameter(ViperParams.PARAM_DYNAMIC_EQ_BAND_FILTER_TYPE, b, deq.filterTypes.getOrElse(b) { 0 })
            }
            effect.setParameter(ViperParams.PARAM_DYNAMIC_EQ_BAND_COUNT, state.dynamicEq.bandCount)
        }

        // Convolver
        effect.setParameter(ViperParams.PARAM_CONVOLVER_ENABLE, if (state.convolver.enable) 1 else 0)
        if (state.convolver.enable) {
            effect.setParameter(ViperParams.PARAM_CONVOLVER_CROSS_CHANNEL, state.convolver.crossChannel)
            effect.setParameter(ViperParams.PARAM_CONVOLVER_WET, state.convolver.wet)
            effect.setParameter(ViperParams.PARAM_CONVOLVER_OUTPUT_GAIN, state.convolver.outputGain)
            effect.setParameter(ViperParams.PARAM_CONVOLVER_ROUTING, state.convolver.routing)
            effect.setParameter(
                ViperParams.PARAM_CONVOLVER_CROSS_DELAY,
                state.convolver.crossDelay100Ns,
            )
        }

        dispatchIemState(effect, state)

        // Field Surround
        effect.setParameter(ViperParams.PARAM_FIELD_SURROUND_ENABLE, if (state.fieldSurround.enable) 1 else 0)
        if (state.fieldSurround.enable) {
            effect.setParameter(ViperParams.PARAM_FIELD_SURROUND_WIDENING, ParamRaw.fieldSurroundWidening(state.fieldSurround.widening))
            effect.setParameter(ViperParams.PARAM_FIELD_SURROUND_MID_IMAGE, ParamRaw.fieldSurroundMidImage(state.fieldSurround.midImage))
            effect.setParameter(ViperParams.PARAM_FIELD_SURROUND_DEPTH, ParamRaw.fieldSurroundDepth(state.fieldSurround.depth))
        }

        // Diff Surround
        effect.setParameter(ViperParams.PARAM_DIFF_SURROUND_ENABLE, if (state.diffSurround.enable) 1 else 0)
        if (state.diffSurround.enable) {
            effect.setParameter(ViperParams.PARAM_DIFF_SURROUND_DELAY, ParamRaw.diffSurroundDelay(state.diffSurround.delay))
            effect.setParameter(ViperParams.PARAM_DIFF_SURROUND_REVERSE, if (state.diffSurround.reverse) 1 else 0)
            effect.setParameter(ViperParams.PARAM_DIFF_SURROUND_WET_DRY_MIX, state.diffSurround.wetDryMix)
            effect.setParameter(ViperParams.PARAM_DIFF_SURROUND_LP_CUTOFF, state.diffSurround.lpCutoff)
        }

        // Stereo Imager
        effect.setParameter(ViperParams.PARAM_STEREO_IMAGER_ENABLE, if (state.stereoImager.enable) 1 else 0)
        if (state.stereoImager.enable) {
            effect.setParameter(ViperParams.PARAM_STEREO_IMAGER_LOW_WIDTH, state.stereoImager.lowWidth)
            effect.setParameter(ViperParams.PARAM_STEREO_IMAGER_MID_WIDTH, state.stereoImager.midWidth)
            effect.setParameter(ViperParams.PARAM_STEREO_IMAGER_HIGH_WIDTH, state.stereoImager.highWidth)
            effect.setParameter(ViperParams.PARAM_STEREO_IMAGER_LOW_CROSSOVER, state.stereoImager.lowCrossover)
            effect.setParameter(ViperParams.PARAM_STEREO_IMAGER_HIGH_CROSSOVER, state.stereoImager.highCrossover)
        }

        // Headphone Surround
        effect.setParameter(ViperParams.PARAM_HEADPHONE_SURROUND_ENABLE, if (state.headphoneSurround.enable) 1 else 0)
        if (state.headphoneSurround.enable) {
            effect.setParameter(ViperParams.PARAM_HEADPHONE_SURROUND_QUALITY, state.headphoneSurround.quality)
        }

        // Reverb
        effect.setParameter(ViperParams.PARAM_REVERB_ENABLE, if (state.reverb.enable) 1 else 0)
        if (state.reverb.enable) {
            effect.setParameter(ViperParams.PARAM_REVERB_ROOM_SIZE, ParamRaw.reverbRoomSize(state.reverb.roomSize))
            effect.setParameter(ViperParams.PARAM_REVERB_WIDTH, ParamRaw.reverbWidth(state.reverb.width))
            effect.setParameter(ViperParams.PARAM_REVERB_DAMP, ParamRaw.reverbDamp(state.reverb.damp))
            effect.setParameter(ViperParams.PARAM_REVERB_WET, state.reverb.wet)
            effect.setParameter(ViperParams.PARAM_REVERB_DRY, state.reverb.dry)
        }

        // Dynamic System
        effect.setParameter(ViperParams.PARAM_DYNAMIC_SYSTEM_ENABLE, if (state.dynamicSystem.enable) 1 else 0)
        if (state.dynamicSystem.enable) {
            effect.setParameter(ViperParams.PARAM_DYNAMIC_SYSTEM_STRENGTH, ParamRaw.dynamicSystemStrength(state.dynamicSystem.strength))
            effect.setParameter(ViperParams.PARAM_DYNAMIC_SYSTEM_X_LOW, state.dynamicSystem.xLow)
            effect.setParameter(ViperParams.PARAM_DYNAMIC_SYSTEM_X_HIGH, state.dynamicSystem.xHigh)
            effect.setParameter(ViperParams.PARAM_DYNAMIC_SYSTEM_Y_LOW, state.dynamicSystem.yLow)
            effect.setParameter(ViperParams.PARAM_DYNAMIC_SYSTEM_Y_HIGH, state.dynamicSystem.yHigh)
            effect.setParameter(ViperParams.PARAM_DYNAMIC_SYSTEM_SIDE_GAIN_LOW, state.dynamicSystem.sideGainLow)
            effect.setParameter(ViperParams.PARAM_DYNAMIC_SYSTEM_SIDE_GAIN_HIGH, state.dynamicSystem.sideGainHigh)
        }

        // Tube Simulator
        effect.setParameter(ViperParams.PARAM_TUBE_SIMULATOR_ENABLE, if (state.tubeSimulator.enable) 1 else 0)

        // Psycho Bass
        effect.setParameter(ViperParams.PARAM_PSYCHOACOUSTIC_BASS_ENABLE, if (state.psychoacousticBass.enable) 1 else 0)
        if (state.psychoacousticBass.enable) {
            effect.setParameter(ViperParams.PARAM_PSYCHOACOUSTIC_BASS_CUTOFF, state.psychoacousticBass.cutoff)
            effect.setParameter(ViperParams.PARAM_PSYCHOACOUSTIC_BASS_INTENSITY, state.psychoacousticBass.intensity)
            effect.setParameter(ViperParams.PARAM_PSYCHOACOUSTIC_BASS_HARMONIC_ORDER, state.psychoacousticBass.harmonicOrder)
            effect.setParameter(ViperParams.PARAM_PSYCHOACOUSTIC_BASS_ORIGINAL_LEVEL, state.psychoacousticBass.originalLevel)
        }

        // Bass
        effect.setParameter(ViperParams.PARAM_BASS_ENABLE, if (state.bass.enable) 1 else 0)
        if (state.bass.enable) {
            effect.setParameter(ViperParams.PARAM_BASS_MODE, state.bass.mode)
            effect.setParameter(ViperParams.PARAM_BASS_FREQUENCY, ParamRaw.bassFrequency(state.bass.frequency))
            effect.setParameter(ViperParams.PARAM_BASS_GAIN, state.bass.gain)
            effect.setParameter(ViperParams.PARAM_BASS_ANTI_POP, if (state.bass.antiPop) 1 else 0)
        }

        // Bass Mono
        effect.setParameter(ViperParams.PARAM_BASS_MONO_ENABLE, if (state.bassMono.enable) 1 else 0)
        if (state.bassMono.enable) {
            effect.setParameter(ViperParams.PARAM_BASS_MONO_MODE, state.bassMono.mode)
            effect.setParameter(ViperParams.PARAM_BASS_MONO_FREQUENCY, ParamRaw.bassFrequency(state.bassMono.frequency))
            effect.setParameter(ViperParams.PARAM_BASS_MONO_GAIN, state.bassMono.gain)
            effect.setParameter(ViperParams.PARAM_BASS_MONO_ANTI_POP, if (state.bassMono.antiPop) 1 else 0)
        }

        // Clarity
        effect.setParameter(ViperParams.PARAM_CLARITY_ENABLE, if (state.clarity.enable) 1 else 0)
        if (state.clarity.enable) {
            effect.setParameter(ViperParams.PARAM_CLARITY_MODE, state.clarity.mode)
            effect.setParameter(ViperParams.PARAM_CLARITY_GAIN, state.clarity.gain)
        }

        // Cure
        effect.setParameter(ViperParams.PARAM_CURE_ENABLE, if (state.cure.enable) 1 else 0)
        if (state.cure.enable) {
            effect.setParameter(ViperParams.PARAM_CURE_CROSSFEED_PRESET, state.cure.crossfeedPreset)
        }

        // AnalogX
        effect.setParameter(ViperParams.PARAM_ANALOG_X_ENABLE, if (state.analogX.enable) 1 else 0)
        if (state.analogX.enable) {
            effect.setParameter(ViperParams.PARAM_ANALOG_X_MODE, state.analogX.mode)
        }

        // Speaker Correction
        effect.setParameter(ViperParams.PARAM_SPEAKER_CORRECTION_ENABLE, if (state.speakerCorrection.enable) 1 else 0)
    }

    internal fun dispatchIemState(
        effect: ParamSink,
        state: EffectState,
    ) {
        iemWrites(state).forEach { write ->
            when (write) {
                is IemWrite.Scalar -> effect.setParameter(write.param, write.value)
                is IemWrite.Indexed -> effect.setParameter(write.param, write.index, write.value)
            }
        }
    }

    internal fun iemWrites(state: EffectState): List<IemWrite> = buildList {
        val iem = state.iem
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_ENABLE, 0))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_WET, iem.output.wetPercent))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_OUTPUT_GAIN, iem.output.gainDecidb))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_ORDER, iem.general.order))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_ENCODER_MODE, iem.general.encoderMode))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_RENDER_MODE, iem.general.renderMode))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_LATENCY_PROFILE, iem.output.latencyProfile))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_LIMITER_ENABLE, if (iem.output.limiterEnabled) 1 else 0))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_LIMITER_CEILING, iem.output.limiterCeilingCentidb))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_STEREO_AZIMUTH, iem.stereo.azimuthCentidegrees))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_STEREO_ELEVATION, iem.stereo.elevationCentidegrees))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_STEREO_ROLL, iem.stereo.rollCentidegrees))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_STEREO_WIDTH, iem.stereo.widthCentidegrees))
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_STEREO_SAMPLE_WISE, if (iem.stereo.sampleWise) 1 else 0))
        for (source in 0..1) {
            add(IemWrite.Indexed(ViperParams.PARAM_IEM_MULTI_AZIMUTH, source,
                iem.multi.azimuthCentidegrees.getOrElse(source) { if (source == 0) -3000 else 3000 }))
            add(IemWrite.Indexed(ViperParams.PARAM_IEM_MULTI_ELEVATION, source,
                iem.multi.elevationCentidegrees.getOrElse(source) { 0 }))
            add(IemWrite.Indexed(ViperParams.PARAM_IEM_MULTI_GAIN, source,
                iem.multi.gainDecidb.getOrElse(source) { 0 }))
            add(IemWrite.Indexed(ViperParams.PARAM_IEM_MULTI_MUTE, source,
                if (iem.multi.mute.getOrElse(source) { false }) 1 else 0))
        }
        val granular = iem.granular
        listOf(
            ViperParams.PARAM_IEM_GRANULAR_AZIMUTH to granular.azimuthCentidegrees,
            ViperParams.PARAM_IEM_GRANULAR_ELEVATION to granular.elevationCentidegrees,
            ViperParams.PARAM_IEM_GRANULAR_SHAPE to granular.shapeTenths,
            ViperParams.PARAM_IEM_GRANULAR_SIZE to granular.sizeCentidegrees,
            ViperParams.PARAM_IEM_GRANULAR_ROLL to granular.rollCentidegrees,
            ViperParams.PARAM_IEM_GRANULAR_WIDTH to granular.widthCentidegrees,
            ViperParams.PARAM_IEM_GRANULAR_DELTA_TIME to granular.deltaTimeUs,
            ViperParams.PARAM_IEM_GRANULAR_DELTA_TIME_MOD to granular.deltaTimeModTenthsPercent,
            ViperParams.PARAM_IEM_GRANULAR_GRAIN_LENGTH to granular.grainLengthUs,
            ViperParams.PARAM_IEM_GRANULAR_GRAIN_LENGTH_MOD to granular.grainLengthModTenthsPercent,
            ViperParams.PARAM_IEM_GRANULAR_READ_POSITION to granular.readPositionUs,
            ViperParams.PARAM_IEM_GRANULAR_POSITION_MOD to granular.positionModUs,
            ViperParams.PARAM_IEM_GRANULAR_PITCH to granular.pitchMilliSemitones,
            ViperParams.PARAM_IEM_GRANULAR_PITCH_MOD to granular.pitchModMilliSemitones,
            ViperParams.PARAM_IEM_GRANULAR_WINDOW_ATTACK to granular.attackTenthsPercent,
            ViperParams.PARAM_IEM_GRANULAR_ATTACK_MOD to granular.attackModTenthsPercent,
            ViperParams.PARAM_IEM_GRANULAR_WINDOW_DECAY to granular.decayTenthsPercent,
            ViperParams.PARAM_IEM_GRANULAR_DECAY_MOD to granular.decayModTenthsPercent,
            ViperParams.PARAM_IEM_GRANULAR_MIX to granular.mixTenthsPercent,
            ViperParams.PARAM_IEM_GRANULAR_SOURCE_PROBABILITY to granular.sourceProbabilityHundredths,
            ViperParams.PARAM_IEM_GRANULAR_SPATIAL_MODE to granular.spatialMode,
            ViperParams.PARAM_IEM_GRANULAR_SAMPLE_WISE to if (granular.sampleWise) 1 else 0,
        ).forEach { (param, value) -> add(IemWrite.Scalar(param, value)) }
        val halo = iem.halo
        listOf(
            ViperParams.PARAM_IEM_HALO_DIALOG_ISOLATE to halo.dialogIsolateThousandths,
            ViperParams.PARAM_IEM_HALO_DIALOG_AGGRESS to halo.dialogAggressThousandths,
            ViperParams.PARAM_IEM_HALO_DIALOG_ATTACK to halo.dialogAttackThousandths,
            ViperParams.PARAM_IEM_HALO_DIALOG_RELEASE to halo.dialogReleaseThousandths,
            ViperParams.PARAM_IEM_HALO_DIALOG_MIX_IN to halo.dialogMixInThousandths,
            ViperParams.PARAM_IEM_HALO_DIVERGENCE to halo.divergenceThousandths,
            ViperParams.PARAM_IEM_HALO_FADE to halo.fadeThousandths,
            ViperParams.PARAM_IEM_HALO_FADE_REARS to halo.fadeRearsThousandths,
            ViperParams.PARAM_IEM_HALO_DIFFUSION to halo.diffusionThousandths,
            ViperParams.PARAM_IEM_HALO_SPACE to halo.spaceThousandths,
            ViperParams.PARAM_IEM_HALO_BACK_BOOST to if (halo.backBoost) 1 else 0,
            ViperParams.PARAM_IEM_HALO_REAR_SHELF_ENABLE to if (halo.rearShelfEnable) 1 else 0,
            ViperParams.PARAM_IEM_HALO_REAR_SHELF_FREQ to halo.rearShelfFreqThousandths,
            ViperParams.PARAM_IEM_HALO_REAR_SHELF_GAIN to halo.rearShelfGainThousandths,
            ViperParams.PARAM_IEM_HALO_LFE_ENABLE to if (halo.lfeEnabled) 1 else 0,
            ViperParams.PARAM_IEM_HALO_LFE_FREQUENCY to halo.lfeFrequencyMillionths,
            ViperParams.PARAM_IEM_HALO_LFE_SPLIT to halo.lfeSplitMillionths,
            ViperParams.PARAM_IEM_HALO_LFE_GAIN to halo.lfeGainMillionths,
        ).forEach { (param, value) -> add(IemWrite.Scalar(param, value)) }
        val rotation = iem.rotation
        listOf(
            ViperParams.PARAM_IEM_ROTATION_YAW to rotation.yawCentidegrees,
            ViperParams.PARAM_IEM_ROTATION_PITCH to rotation.pitchCentidegrees,
            ViperParams.PARAM_IEM_ROTATION_ROLL to rotation.rollCentidegrees,
            ViperParams.PARAM_IEM_ROTATION_INVERT_YAW to if (rotation.invertYaw) 1 else 0,
            ViperParams.PARAM_IEM_ROTATION_INVERT_PITCH to if (rotation.invertPitch) 1 else 0,
            ViperParams.PARAM_IEM_ROTATION_INVERT_ROLL to if (rotation.invertRoll) 1 else 0,
            ViperParams.PARAM_IEM_ROTATION_INVERT_OVERALL to if (rotation.invertOverall) 1 else 0,
            ViperParams.PARAM_IEM_ROTATION_SEQUENCE to rotation.sequence,
            ViperParams.PARAM_IEM_HEADPHONE_EQ to iem.decoder.headphoneEq,
        ).forEach { (param, value) -> add(IemWrite.Scalar(param, value)) }
        val downmix = iem.decoder.downmix
        listOf(
            ViperParams.PARAM_IEM_DOWNMIX_DELAY_ENABLE to if (downmix.delayEnabled) 1 else 0,
            ViperParams.PARAM_IEM_DOWNMIX_LS_DELAY to downmix.lsDelayUs,
            ViperParams.PARAM_IEM_DOWNMIX_RS_DELAY to downmix.rsDelayUs,
            ViperParams.PARAM_IEM_DOWNMIX_LSR_DELAY to downmix.lsrDelayUs,
            ViperParams.PARAM_IEM_DOWNMIX_RSR_DELAY to downmix.rsrDelayUs,
            ViperParams.PARAM_IEM_DOWNMIX_SIDE_SHELF_ENABLE to if (downmix.sideShelfEnabled) 1 else 0,
            ViperParams.PARAM_IEM_DOWNMIX_SIDE_SHELF_FREQUENCY to downmix.sideShelfFrequencyMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_SIDE_SHELF_GAIN to downmix.sideShelfGainMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_REAR_SHELF_ENABLE to if (downmix.rearShelfEnabled) 1 else 0,
            ViperParams.PARAM_IEM_DOWNMIX_REAR_SHELF_FREQUENCY to downmix.rearShelfFrequencyMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_REAR_SHELF_GAIN to downmix.rearShelfGainMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_PAN_LEFT to downmix.panLeftMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_PAN_RIGHT to downmix.panRightMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_CENTER_DIVERGENCE to downmix.centerDivergenceMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_FRONT_MID_TRIM to downmix.frontMidTrimMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_FRONT_SIDE_TRIM to downmix.frontSideTrimMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_CENTER_TRIM to downmix.centerTrimMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_SURROUND_MID_TRIM to downmix.surroundMidTrimMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_SURROUND_SIDE_TRIM to downmix.surroundSideTrimMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_REAR_MID_TRIM to downmix.rearMidTrimMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_REAR_SIDE_TRIM to downmix.rearSideTrimMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_LFE_TRIM to downmix.lfeTrimMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_LFE_LPF_ENABLE to if (downmix.lfeLpfEnabled) 1 else 0,
            ViperParams.PARAM_IEM_DOWNMIX_LFE_LPF_FREQUENCY to downmix.lfeLpfFrequencyMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_SCALE_INPUT_BY_OUTPUT_COUNT to if (downmix.scaleInputByOutputCount) 1 else 0,
            ViperParams.PARAM_IEM_DOWNMIX_OUTPUT_HPF_ENABLE to if (downmix.outputHpfEnabled) 1 else 0,
            ViperParams.PARAM_IEM_DOWNMIX_OUTPUT_HPF_FREQUENCY to downmix.outputHpfFrequencyMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_OUTPUT_LEFT_TRIM to downmix.outputLeftTrimMillionths,
            ViperParams.PARAM_IEM_DOWNMIX_OUTPUT_RIGHT_TRIM to downmix.outputRightTrimMillionths,
        ).forEach { (param, value) -> add(IemWrite.Scalar(param, value)) }
        add(IemWrite.Scalar(ViperParams.PARAM_IEM_ENABLE, if (iem.general.enable) 1 else 0))
    }

    fun eqBandLevelsToBytes(bands: List<Double>): ByteArray {
        val bytes = ByteArray(256)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(bands.size)
        for (b in bands) buf.putFloat(b.toFloat())
        return bytes
    }

    suspend fun loadFullStateFromPrefs(repository: ViperRepository): EffectState {
        val s = loadEffectPrefs(repository)
        val eqBands = ensureBandCount(s.eq.bands, s.eq.bandCount)
        return s.copy(eq = s.eq.copy(bands = eqBands))
    }
}

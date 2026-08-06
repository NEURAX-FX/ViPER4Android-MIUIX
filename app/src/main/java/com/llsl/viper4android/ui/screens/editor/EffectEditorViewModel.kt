package com.llsl.viper4android.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llsl.viper4android.effect.BoolPref
import com.llsl.viper4android.effect.DoubleListPref
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.EffectStateStore
import com.llsl.viper4android.effect.IntListPref
import com.llsl.viper4android.effect.IntPref
import com.llsl.viper4android.effect.ListPref
import com.llsl.viper4android.effect.Effects
import com.llsl.viper4android.effect.MULTIBAND_BAND_COUNT
import com.llsl.viper4android.effect.MULTIBAND_CROSSOVER_COUNT
import com.llsl.viper4android.effect.normalizeMultibandCompressorState
import com.llsl.viper4android.effect.setTo
import com.llsl.viper4android.dsp.DEFAULT_GRAPH_SAMPLE_RATE
import com.llsl.viper4android.dsp.safeMultibandCrossoverMax
import com.llsl.viper4android.dsp.sanitizeGraphSampleRate
import com.llsl.viper4android.viper.ConfigChannel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EffectEditorViewModel @Inject constructor(
    private val store: EffectStateStore,
) : ViewModel() {
    val state: StateFlow<EffectState> = store.state
    val isServiceConnected: StateFlow<Boolean> = store.isServiceConnected

    /**
     * Output sample rate the graphs must use.
     *
     * Every driver filter coefficient depends on the sample rate, so a preview drawn at a
     * hardcoded 48 kHz would not match the audio on a 44.1 kHz stream. The driver reports 0
     * until a stream is attached, which [sanitizeGraphSampleRate] turns into a documented
     * fallback.
     */
    private val _graphSampleRate = MutableStateFlow(DEFAULT_GRAPH_SAMPLE_RATE)
    val graphSampleRate: StateFlow<Int> = _graphSampleRate.asStateFlow()

    private val history = EditorHistory<EffectState>()
    private val _undoCount = MutableStateFlow(0)
    val undoCount: StateFlow<Int> = _undoCount.asStateFlow()
    private val _redoCount = MutableStateFlow(0)
    val redoCount: StateFlow<Int> = _redoCount.asStateFlow()

    init {
        refreshGraphSampleRate()
    }

    /**
     * Re-reads the driver's reported output rate. Cheap enough to call on screen entry.
     *
     * The read is best-effort: if the service is unreachable (or, in unit tests, the Log
     * shim is unavailable), the graph keeps its documented default rate instead of the
     * editor crashing over a preview nicety.
     */
    fun refreshGraphSampleRate() {
        val reported = try {
            ConfigChannel.readStatus()?.sampleRate ?: 0
        } catch (unavailable: RuntimeException) {
            0
        }
        val sampleRate = sanitizeGraphSampleRate(reported)
        _graphSampleRate.value = sampleRate
        val normalized =
            normalizeMultibandCompressorState(
                state.value.multibandCompressor,
                maxCrossoverFrequency = safeMultibandCrossoverMax(sampleRate),
            )
        if (normalized.crossovers != state.value.multibandCompressor.crossovers) {
            store.applyTransaction(
                listOf(Effects.multibandCompressor.crossovers setTo normalized.crossovers),
            )
        }
    }

    fun beginGesture() {
        history.beginGesture(state.value)
    }

    fun settleGesture() {
        history.settleGesture(state.value)
        publishHistorySize()
    }

    fun undo() {
        history.undo()?.let(store::restoreState)
        publishHistorySize()
    }

    fun redo() {
        history.redo()?.let(store::restoreState)
        publishHistorySize()
    }

    fun setEnabled(kind: EditorKind, enabled: Boolean) {
        when (kind) {
            EditorKind.FIR_EQUALIZER -> store.updatePref(Effects.equalizer.enable, enabled)
            EditorKind.DYNAMIC_EQUALIZER -> store.updatePref(Effects.dynamicEq.enable, enabled)
            EditorKind.MULTIBAND_COMPRESSOR -> store.updatePref(Effects.multibandCompressor.enable, enabled)
        }
    }

    fun performDiscreteEdit(edit: () -> Unit) {
        beginGesture()
        edit()
        settleGesture()
    }

    fun updateFirBand(index: Int, gain: Double, last: Boolean = true) {
        val bands = state.value.eq.bands.toMutableList()
        while (bands.size <= index) bands += 0.0
        bands[index] = gain.coerceIn(-12.0, 12.0)
        store.updatePref(Effects.equalizer.bands, bands.take(state.value.eq.bandCount), last)
    }

    fun updateFirBandCount(count: Int) {
        store.updatePref(Effects.equalizer.bandCount, count.coerceIn(10, 31))
    }

    fun updateDynamicFrequency(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(Effects.dynamicEq.freqs, index, value.coerceIn(20, 20_000), count = state.value.dynamicEq.bandCount, last = last)
    }

    fun updateDynamicGain(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(Effects.dynamicEq.gains, index, value.coerceIn(-120, 120), count = state.value.dynamicEq.bandCount, last = last)
    }

    fun updateDynamicQ(index: Int, value: Int) {
        store.updateBandPref(Effects.dynamicEq.qs, index, value.coerceIn(50, 800), count = state.value.dynamicEq.bandCount)
    }

    fun updateDynamicThreshold(index: Int, value: Int) {
        store.updateBandPref(Effects.dynamicEq.thresholds, index, value.coerceIn(-800, 0), count = state.value.dynamicEq.bandCount)
    }

    fun updateDynamicAttack(index: Int, value: Int) {
        store.updateBandPref(Effects.dynamicEq.attacks, index, value.coerceIn(1, 100), count = state.value.dynamicEq.bandCount)
    }

    fun updateDynamicRelease(index: Int, value: Int) {
        store.updateBandPref(Effects.dynamicEq.releases, index, value.coerceIn(10, 500), count = state.value.dynamicEq.bandCount)
    }

    fun updateDynamicFilterType(index: Int, value: Int) {
        store.updateBandPref(Effects.dynamicEq.filterTypes, index, value.coerceIn(0, 5), count = state.value.dynamicEq.bandCount)
    }

    fun updateMultibandCrossover(index: Int, value: Int, last: Boolean = true) {
        val constrained =
            constrainCrossovers(
                values = state.value.multibandCompressor.crossovers,
                changedIndex = index,
                requestedFrequency = value,
                maxFrequency = safeMultibandCrossoverMax(graphSampleRate.value),
            )
        // Crossovers are an IntListPref, so a whole-list updatePref would never emit a
        // dispatch command and the running DSP would keep the old split points. Send the
        // bands that actually moved instead.
        val previous = state.value.multibandCompressor.crossovers
        var dispatched = false
        constrained.forEachIndexed { band, frequency ->
            if (previous.getOrNull(band) != frequency) {
                dispatched = true
                store.updateBandPref(
                    Effects.multibandCompressor.crossovers,
                    band,
                    frequency,
                    count = constrained.size,
                    last = last && band == index,
                )
            }
        }
        if (!dispatched && last) {
            store.updateBandPref(
                Effects.multibandCompressor.crossovers,
                index,
                constrained[index],
                count = constrained.size,
                last = true,
            )
        }
    }

    fun updateMultibandThreshold(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.thresholds,
            index,
            value.coerceIn(-48, 0),
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandRatio(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.ratios,
            index,
            value.coerceIn(0, 200),
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandGain(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.gains,
            index,
            value.coerceIn(0, 24),
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandKnee(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.knees,
            index,
            value.coerceIn(0, 12),
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandKneeMulti(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.kneeMultis,
            index,
            value.coerceIn(0, 100),
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandAttack(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.attacks,
            index,
            value.coerceIn(1, 100),
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandMaxAttack(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.maxAttacks,
            index,
            value.coerceIn(1, 100),
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandRelease(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.releases,
            index,
            value.coerceIn(5, 500),
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandMaxRelease(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.maxReleases,
            index,
            value.coerceIn(5, 500),
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandCrest(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.crests,
            index,
            value.coerceIn(5, 300),
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandAdapt(index: Int, value: Int, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.adapts,
            index,
            value.coerceIn(0, 200),
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandBandEnable(index: Int, enabled: Boolean, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.bandEnables,
            index,
            enabled,
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandKneeAuto(index: Int, enabled: Boolean, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.kneeAutos,
            index,
            enabled,
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandGainAuto(index: Int, enabled: Boolean, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.gainAutos,
            index,
            enabled,
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandAttackAuto(index: Int, enabled: Boolean, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.attackAutos,
            index,
            enabled,
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandReleaseAuto(index: Int, enabled: Boolean, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.releaseAutos,
            index,
            enabled,
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandNoClip(index: Int, enabled: Boolean, last: Boolean = true) {
        store.updateBandPref(
            Effects.multibandCompressor.noClips,
            index,
            enabled,
            count = MULTIBAND_BAND_COUNT,
            last = last,
        )
    }

    fun updateMultibandCrossoverHandle(
        crossover: Int,
        frequency: Int,
        gain: Int,
        last: Boolean = true,
    ) {
        require(crossover in 0 until MULTIBAND_CROSSOVER_COUNT)
        val band = crossover
        val gainIsManual = !state.value.multibandCompressor.gainAutos[band]
        updateMultibandCrossover(band, frequency, last = last && !gainIsManual)
        if (gainIsManual) updateMultibandGain(band, gain, last = last)
    }

    fun handleMultibandEditorAction(action: MultibandEditorAction) {
        when (action) {
            MultibandEditorAction.BeginGesture -> beginGesture()
            MultibandEditorAction.SettleGesture -> settleGesture()
            MultibandEditorAction.Flush -> flush()
            is MultibandEditorAction.SetCrossoverHandle ->
                updateMultibandCrossoverHandle(
                    crossover = action.crossover,
                    frequency = action.frequency,
                    gain = action.gain,
                    last = action.last,
                )
            is MultibandEditorAction.SetInt -> updateMultibandInt(action)
            is MultibandEditorAction.SetBoolean -> updateMultibandBoolean(action)
        }
    }

    private fun updateMultibandInt(action: MultibandEditorAction.SetInt) {
        when (action.control) {
            MultibandIntControl.THRESHOLD -> updateMultibandThreshold(action.band, action.value, action.last)
            MultibandIntControl.RATIO -> updateMultibandRatio(action.band, action.value, action.last)
            MultibandIntControl.GAIN -> updateMultibandGain(action.band, action.value, action.last)
            MultibandIntControl.KNEE -> updateMultibandKnee(action.band, action.value, action.last)
            MultibandIntControl.KNEE_MULTI -> updateMultibandKneeMulti(action.band, action.value, action.last)
            MultibandIntControl.ATTACK -> updateMultibandAttack(action.band, action.value, action.last)
            MultibandIntControl.MAX_ATTACK -> updateMultibandMaxAttack(action.band, action.value, action.last)
            MultibandIntControl.RELEASE -> updateMultibandRelease(action.band, action.value, action.last)
            MultibandIntControl.MAX_RELEASE -> updateMultibandMaxRelease(action.band, action.value, action.last)
            MultibandIntControl.CREST -> updateMultibandCrest(action.band, action.value, action.last)
            MultibandIntControl.ADAPT -> updateMultibandAdapt(action.band, action.value, action.last)
        }
    }

    private fun updateMultibandBoolean(action: MultibandEditorAction.SetBoolean) {
        when (action.control) {
            MultibandBooleanControl.BAND_ENABLE -> updateMultibandBandEnable(action.band, action.value, action.last)
            MultibandBooleanControl.KNEE_AUTO -> updateMultibandKneeAuto(action.band, action.value, action.last)
            MultibandBooleanControl.GAIN_AUTO -> updateMultibandGainAuto(action.band, action.value, action.last)
            MultibandBooleanControl.ATTACK_AUTO -> updateMultibandAttackAuto(action.band, action.value, action.last)
            MultibandBooleanControl.RELEASE_AUTO -> updateMultibandReleaseAuto(action.band, action.value, action.last)
            MultibandBooleanControl.NO_CLIP -> updateMultibandNoClip(action.band, action.value, action.last)
        }
    }

    fun reset(kind: EditorKind) {
        when (kind) {
            EditorKind.FIR_EQUALIZER -> {
                store.applyTransaction(
                    listOf(
                        Effects.equalizer.bands setTo List(state.value.eq.bandCount) { 0.0 },
                        Effects.equalizer.presetId setTo null,
                    ),
                )
            }
            EditorKind.DYNAMIC_EQUALIZER -> {
                // bandCount must move with the lists, otherwise the dispatcher keeps
                // iterating bands the editor no longer shows.
                store.applyTransaction(
                    listOf(
                        Effects.dynamicEq.bandCount setTo 3,
                        Effects.dynamicEq.freqs setTo listOf(60, 150, 400),
                        Effects.dynamicEq.qs setTo listOf(100, 100, 150),
                        Effects.dynamicEq.gains setTo listOf(0, 0, 0),
                        Effects.dynamicEq.thresholds setTo listOf(-200, -200, -200),
                        Effects.dynamicEq.attacks setTo listOf(10, 10, 10),
                        Effects.dynamicEq.releases setTo listOf(100, 100, 100),
                        Effects.dynamicEq.filterTypes setTo listOf(0, 0, 0),
                    ),
                )
            }
            EditorKind.MULTIBAND_COMPRESSOR -> {
                beginGesture()
                store.applyTransaction(
                    listOf(
                        Effects.multibandCompressor.bandEnables setTo List(MULTIBAND_BAND_COUNT) { true },
                        Effects.multibandCompressor.crossovers setTo listOf(120, 500, 4000, 8000),
                        Effects.multibandCompressor.thresholds setTo List(MULTIBAND_BAND_COUNT) { -18 },
                        Effects.multibandCompressor.ratios setTo List(MULTIBAND_BAND_COUNT) { 50 },
                        Effects.multibandCompressor.gains setTo List(MULTIBAND_BAND_COUNT) { 0 },
                        Effects.multibandCompressor.knees setTo List(MULTIBAND_BAND_COUNT) { 0 },
                        Effects.multibandCompressor.kneeMultis setTo List(MULTIBAND_BAND_COUNT) { 0 },
                        Effects.multibandCompressor.attacks setTo List(MULTIBAND_BAND_COUNT) { 1 },
                        Effects.multibandCompressor.maxAttacks setTo List(MULTIBAND_BAND_COUNT) { 44 },
                        Effects.multibandCompressor.releases setTo List(MULTIBAND_BAND_COUNT) { 100 },
                        Effects.multibandCompressor.maxReleases setTo List(MULTIBAND_BAND_COUNT) { 200 },
                        Effects.multibandCompressor.crests setTo List(MULTIBAND_BAND_COUNT) { 100 },
                        Effects.multibandCompressor.adapts setTo List(MULTIBAND_BAND_COUNT) { 50 },
                        Effects.multibandCompressor.kneeAutos setTo List(MULTIBAND_BAND_COUNT) { true },
                        Effects.multibandCompressor.gainAutos setTo List(MULTIBAND_BAND_COUNT) { true },
                        Effects.multibandCompressor.attackAutos setTo List(MULTIBAND_BAND_COUNT) { true },
                        Effects.multibandCompressor.releaseAutos setTo List(MULTIBAND_BAND_COUNT) { true },
                        Effects.multibandCompressor.noClips setTo List(MULTIBAND_BAND_COUNT) { true },
                    ),
                )
                settleGesture()
            }
        }
    }

    fun flush() {
        viewModelScope.launch { store.flush() }
    }

    private fun publishHistorySize() {
        _undoCount.value = history.undoSize
        _redoCount.value = history.redoSize
    }
}

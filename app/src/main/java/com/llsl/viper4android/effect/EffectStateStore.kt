package com.llsl.viper4android.effect

import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.service.ViperService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface EffectDispatchCommand {
    data class Scalar(
        val paramId: Int,
        val rawValue: Int,
        val last: Boolean,
    ) : EffectDispatchCommand

    data class Band(
        val paramId: Int,
        val band: Int,
        val rawValue: Int,
        val last: Boolean,
    ) : EffectDispatchCommand

    class Binary(
        val paramId: Int,
        val rawValue: ByteArray,
        val last: Boolean,
    ) : EffectDispatchCommand

    data class FullState(
        val state: EffectState,
    ) : EffectDispatchCommand
}

data class EffectPreferenceWrite(
    val pref: EffectPref<*>,
    val value: Any?,
)

interface EffectPreferenceWriter {
    suspend fun write(
        pref: EffectPref<*>,
        value: Any?,
    )

    suspend fun writeBatch(writes: List<EffectPreferenceWrite>) {
        writes.forEach { write(it.pref, it.value) }
    }
}

interface EffectDispatchTarget {
    fun setStateProvider(provider: () -> EffectState)

    fun dispatch(command: EffectDispatchCommand)
}

private class RepositoryEffectPreferenceWriter(
    private val repository: ViperRepository,
) : EffectPreferenceWriter {
    @Suppress("UNCHECKED_CAST")
    override suspend fun write(
        pref: EffectPref<*>,
        value: Any?,
    ) {
        when (pref) {
            is IntPref -> repository.setIntPreference(pref.prefKey, value as Int)
            is BoolPref -> repository.setBooleanPreference(pref.prefKey, value as Boolean)
            is StringPref -> repository.setStringPreference(pref.prefKey, value as String)
            is NullableLongPref -> repository.setIntPreference(pref.prefKey, (value as Long?)?.toInt() ?: -1)
            is IntListPref -> repository.setStringPreference(pref.prefKey, (value as List<Int>).joinToString(";"))
            is BoolListPref -> {
                repository.setStringPreference(
                    pref.prefKey,
                    (value as List<Boolean>).joinToString(";") { if (it) "1" else "0" },
                )
            }
            is DoubleListPref -> {
                repository.setStringPreference(
                    pref.prefKey,
                    (value as List<Double>).joinToString(";") { String.format(Locale.US, "%.1f", it) },
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun writeBatch(writes: List<EffectPreferenceWrite>) {
        val booleans = linkedMapOf<String, Boolean>()
        val ints = linkedMapOf<String, Int>()
        val strings = linkedMapOf<String, String>()
        writes.forEach { write ->
            when (val pref = write.pref) {
                is IntPref -> ints[pref.prefKey] = write.value as Int
                is BoolPref -> booleans[pref.prefKey] = write.value as Boolean
                is StringPref -> strings[pref.prefKey] = write.value as String
                is NullableLongPref -> ints[pref.prefKey] = (write.value as Long?)?.toInt() ?: -1
                is IntListPref -> strings[pref.prefKey] = (write.value as List<Int>).joinToString(";")
                is BoolListPref -> {
                    strings[pref.prefKey] =
                        (write.value as List<Boolean>).joinToString(";") { if (it) "1" else "0" }
                }
                is DoubleListPref -> {
                    strings[pref.prefKey] =
                        (write.value as List<Double>).joinToString(";") { String.format(Locale.US, "%.1f", it) }
                }
            }
        }
        repository.setPreferences(booleans = booleans, ints = ints, strings = strings)
    }
}

private class ViperServiceDispatchTarget(
    private val service: ViperService,
) : EffectDispatchTarget {
    override fun setStateProvider(provider: () -> EffectState) {
        service.setStateProvider(provider)
    }

    override fun dispatch(command: EffectDispatchCommand) {
        when (command) {
            is EffectDispatchCommand.Scalar -> {
                service.dispatchParam(command.paramId, command.rawValue, republishAidl = command.last)
            }
            is EffectDispatchCommand.Band -> {
                service.dispatchParam(
                    command.paramId,
                    command.band,
                    command.rawValue,
                    0,
                    republishAidl = command.last,
                )
            }
            is EffectDispatchCommand.Binary -> {
                service.dispatchParam(command.paramId, command.rawValue, republishAidl = command.last)
            }
            is EffectDispatchCommand.FullState -> service.dispatchFullState(command.state)
        }
    }
}

/** One preference assignment inside an [EffectStateStore.applyTransaction] batch. */
class PrefEdit<T>(
    val pref: EffectPref<T>,
    val value: T,
) {
    internal fun apply(state: EffectState): EffectState = pref.set(state, value)
}

/** Convenience builder so call sites read as `pref setTo value`. */
infix fun <T> EffectPref<T>.setTo(value: T): PrefEdit<T> = PrefEdit(this, value)

@Singleton
class EffectStateStore internal constructor(
    private val preferenceWriter: EffectPreferenceWriter,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(repository: ViperRepository) : this(
        preferenceWriter = RepositoryEffectPreferenceWriter(repository),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    private val mutableState = MutableStateFlow(EffectState())
    val state: StateFlow<EffectState> = mutableState.asStateFlow()
    internal val mutableStateFlow: MutableStateFlow<EffectState> = mutableState

    private val mutableServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = mutableServiceConnected.asStateFlow()

    private val pendingWrites = Collections.synchronizedSet(mutableSetOf<Job>())
    private var dispatchTarget: EffectDispatchTarget? = null

    fun replaceState(state: EffectState) {
        val normalized = normalizeEffectState(state)
        mutableState.value = normalized
        changedMultibandWrites(state, normalized).takeIf { it.isNotEmpty() }?.let(::scheduleWriteBatch)
    }

    fun updateState(transform: (EffectState) -> EffectState) {
        mutableState.update { normalizeEffectState(transform(it)) }
    }

    fun attachService(service: ViperService?) {
        attachDispatchTarget(service?.let(::ViperServiceDispatchTarget))
    }

    fun attachDispatchTarget(target: EffectDispatchTarget?) {
        dispatchTarget = target
        mutableServiceConnected.value = target != null
        if (target != null) {
            target.setStateProvider { mutableState.value }
            target.dispatch(EffectDispatchCommand.FullState(mutableState.value))
        }
    }

    fun dispatchFullState() {
        dispatchTarget?.dispatch(EffectDispatchCommand.FullState(mutableState.value))
    }

    fun restoreState(state: EffectState) {
        val previous = mutableState.value
        val normalized = normalizeEffectState(state)
        mutableState.value = normalized
        changedMultibandWrites(previous, normalized).takeIf { it.isNotEmpty() }?.let(::scheduleWriteBatch)
        dispatchFullState()
    }

    fun <T> updatePref(
        pref: EffectPref<T>,
        value: T,
        last: Boolean = true,
    ) {
        var finalValue = value
        mutableState.update {
            normalizeEffectState(pref.set(it, value)).also { normalized -> finalValue = pref.get(normalized) }
        }
        scheduleWrite(pref, finalValue)
        if (pref.paramId == -1 || !mutableState.value.masterEnable || !shouldDispatch(pref)) return

        val command =
            when (pref) {
                is DoubleListPref -> {
                    @Suppress("UNCHECKED_CAST")
                    EffectDispatchCommand.Binary(pref.paramId, pref.toRawArray(finalValue as List<Double>), last)
                }
                is IntListPref, is BoolListPref -> null
                else -> EffectDispatchCommand.Scalar(pref.paramId, pref.toRaw(finalValue), last)
            }
        command?.let { dispatchTarget?.dispatch(it) }
    }

    fun <E> updateBandPref(
        pref: ListPref<E>,
        band: Int,
        value: E,
        count: Int = 5,
        last: Boolean = true,
    ) {
        require(band in 0 until count) { "Band index $band is outside 0 until $count" }
        var finalList = pref.get(mutableState.value)
        mutableState.update {
            val updated = replaceAt(pref.get(it), band, value, pref.padValue, count)
            normalizeEffectState(pref.set(it, updated)).also { normalized -> finalList = pref.get(normalized) }
        }
        val finalValue = finalList[band]
        scheduleWrite(pref, finalList)
        if (!mutableState.value.masterEnable || !shouldDispatch(pref)) return
        dispatchTarget?.dispatch(
            EffectDispatchCommand.Band(
                paramId = pref.paramId,
                band = band,
                rawValue = pref.elementToRaw(finalValue),
                last = last,
            ),
        )
    }

    /**
     * Applies several preference edits as one unit.
     *
     * Multi-list edits such as an effect reset cannot go through [updatePref] one call at
     * a time: list prefs produce no incremental dispatch command, so the running DSP would
     * keep the previous values until some unrelated full-state publish happened. Batching
     * also avoids leaving a half-written snapshot behind if the process dies mid-reset.
     */
    fun applyTransaction(edits: List<PrefEdit<*>>) {
        if (edits.isEmpty()) return
        mutableState.update { current ->
            normalizeEffectState(edits.fold(current) { state, edit -> edit.apply(state) })
        }
        scheduleWriteBatch(
            edits
                .distinctBy { it.pref }
                .map { EffectPreferenceWrite(it.pref, it.pref.get(mutableState.value)) },
        )
        if (!mutableState.value.masterEnable) return
        dispatchFullState()
    }

    fun replaceEqBands(
        bands: List<Double>,
        last: Boolean = true,
    ) {
        updatePref(Effects.equalizer.bands, bands, last)
    }

    suspend fun flush() {
        while (true) {
            val jobs = synchronized(pendingWrites) { pendingWrites.toList() }
            if (jobs.isEmpty()) return
            jobs.joinAll()
        }
    }

    private fun shouldDispatch(pref: EffectPref<*>): Boolean {
        val enablePref = ENABLE_PREF_BY_EFFECT_KEY[pref.effectKey] ?: return true
        if (pref === enablePref) return true
        return enablePref.get(mutableState.value)
    }

    private fun <E> replaceAt(
        list: List<E>,
        index: Int,
        value: E,
        pad: E,
        count: Int,
    ): List<E> {
        require(index >= 0) { "Band index must be non-negative" }
        val mutable = list.toMutableList()
        while (mutable.size <= index) mutable.add(pad)
        mutable[index] = value
        return if (mutable.size > count) mutable.take(count) else mutable.toList()
    }

    private fun scheduleWrite(
        pref: EffectPref<*>,
        value: Any?,
    ) {
        trackWriteJob(
            scope.launch {
                preferenceWriter.write(pref, value)
            },
        )
    }

    private fun scheduleWriteBatch(writes: List<EffectPreferenceWrite>) {
        if (writes.isEmpty()) return
        trackWriteJob(
            scope.launch {
                preferenceWriter.writeBatch(writes)
            },
        )
    }

    private fun trackWriteJob(job: Job) {
        pendingWrites += job
        job.invokeOnCompletion { pendingWrites -= job }
    }

    private fun normalizeEffectState(state: EffectState): EffectState =
        state.copy(multibandCompressor = normalizeMultibandCompressorState(state.multibandCompressor))

    private fun changedMultibandWrites(
        before: EffectState,
        after: EffectState,
    ): List<EffectPreferenceWrite> =
        MULTIBAND_PREFS.mapNotNull { pref ->
            val beforeValue = pref.get(before)
            val afterValue = pref.get(after)
            EffectPreferenceWrite(pref, afterValue).takeIf { beforeValue != afterValue }
        }

    private companion object {
        val MULTIBAND_PREFS: List<EffectPref<*>> =
            EFFECT_GROUPS.first { it.effectKey == Effects.multibandCompressor.effectKey }.prefs
    }
}

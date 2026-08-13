package com.llsl.viper4android.effect

import com.llsl.viper4android.viper.DriverTelemetry
import com.llsl.viper4android.viper.IemDriverTelemetry
import com.llsl.viper4android.viper.ViperParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectStateStoreTest {
    @Test
    fun editorEnableActionUpdatesStateDispatchesOnceAndPersists() =
        runBlocking {
            val writer = RecordingPreferenceWriter()
            val target = RecordingDispatchTarget()
            val store = newStore(writer)
            store.replaceState(EffectState(masterEnable = true))

            store.attachDispatchTarget(target)
            store.updatePref(Effects.equalizer.enable, true)
            store.flush()

            assertTrue(store.state.value.eq.enable)
            assertEquals(2, target.commands.size)
            assertTrue(target.commands[0] is EffectDispatchCommand.FullState)
            assertEquals(
                EffectDispatchCommand.Scalar(
                    paramId = Effects.equalizer.enable.paramId,
                    rawValue = 1,
                    last = true,
                ),
                target.commands[1],
            )
            assertEquals(
                listOf(EffectPreferenceWrite(Effects.equalizer.enable, true)),
                writer.writes,
            )
        }

    @Test
    fun editorBandActionLinksStateBandDispatchAndListPersistence() =
        runBlocking {
            val writer = RecordingPreferenceWriter()
            val target = RecordingDispatchTarget()
            val store = newStore(writer)
            store.replaceState(
                EffectState(
                    masterEnable = true,
                    multibandCompressor = MultibandCompressorState(enable = true),
                ),
            )
            store.attachDispatchTarget(target)
            target.commands.clear()

            store.updateBandPref(
                pref = Effects.multibandCompressor.thresholds,
                band = 2,
                value = -24,
            )
            store.flush()

            assertEquals(-24, store.state.value.multibandCompressor.thresholds[2])
            assertEquals(
                listOf(
                    EffectDispatchCommand.Band(
                        paramId = Effects.multibandCompressor.thresholds.paramId,
                        band = 2,
                        rawValue = 40,
                        last = true,
                    ),
                ),
                target.commands,
            )
            val persisted = writer.writes.single()
            assertEquals(Effects.multibandCompressor.thresholds, persisted.pref)
            @Suppress("UNCHECKED_CAST")
            assertEquals(-24, (persisted.value as List<Int>)[2])
        }

    @Test
    fun disabledEffectPersistsEditorChangeWithoutAmbiguousBackendExecution() =
        runBlocking {
            val writer = RecordingPreferenceWriter()
            val target = RecordingDispatchTarget()
            val store = newStore(writer)
            store.replaceState(EffectState(masterEnable = true))
            store.attachDispatchTarget(target)
            target.commands.clear()

            store.updateBandPref(
                pref = Effects.dynamicEq.gains,
                band = 0,
                value = 350,
                count = 1,
            )
            store.flush()

            assertEquals(350, store.state.value.dynamicEq.gains[0])
            assertTrue(target.commands.isEmpty())
            assertEquals(1, writer.writes.size)
        }

    @Test
    fun reconnectPublishesLatestOfflineStateBeforeIncrementalCommands() =
        runBlocking {
            val writer = RecordingPreferenceWriter()
            val target = RecordingDispatchTarget()
            val store = newStore(writer)
            store.replaceState(EffectState(masterEnable = true))

            store.updatePref(Effects.dynamicEq.enable, true)
            store.flush()
            assertFalse(store.isServiceConnected.value)

            store.attachDispatchTarget(target)
            store.updateBandPref(
                pref = Effects.dynamicEq.thresholds,
                band = 0,
                value = -1800,
                count = 1,
            )
            store.flush()

            assertTrue(store.isServiceConnected.value)
            val fullState = target.commands[0] as EffectDispatchCommand.FullState
            assertTrue(fullState.state.dynamicEq.enable)
            assertEquals(
                EffectDispatchCommand.Band(
                    paramId = Effects.dynamicEq.thresholds.paramId,
                    band = 0,
                    rawValue = -1800,
                    last = true,
                ),
                target.commands[1],
            )
        }

    @Test
    fun replaceStateNormalizesAndBatchPersistsMalformedMultibandState() =
        runBlocking {
            val writer = RecordingPreferenceWriter()
            val store = newStore(writer)

            store.replaceState(
                EffectState(
                    multibandCompressor =
                        MultibandCompressorState(
                            crossovers = listOf(16_000, 30),
                            thresholds = listOf(-60),
                        ),
                ),
            )
            store.flush()

            val multiband = store.state.value.multibandCompressor
            assertEquals(MULTIBAND_CROSSOVER_COUNT, multiband.crossovers.size)
            assertEquals(listOf(-48, -18, -18, -18, -18), multiband.thresholds)
            assertEquals(1, writer.batches.size)
            assertTrue(writer.writes.isEmpty())
            assertEquals(
                setOf(
                    Effects.multibandCompressor.crossovers,
                    Effects.multibandCompressor.thresholds,
                ),
                writer.batches.single().map { it.pref }.toSet(),
            )
        }

    @Test
    fun transactionNormalizesStateDispatchesOnceAndPersistsOneBatch() =
        runBlocking {
            val writer = RecordingPreferenceWriter()
            val target = RecordingDispatchTarget()
            val store = newStore(writer)
            store.replaceState(
                EffectState(
                    masterEnable = true,
                    multibandCompressor = MultibandCompressorState(enable = true),
                ),
            )
            store.flush()
            writer.clear()
            store.attachDispatchTarget(target)
            target.commands.clear()

            store.applyTransaction(
                listOf(
                    Effects.multibandCompressor.crossovers setTo listOf(16_000, 30),
                    Effects.multibandCompressor.thresholds setTo listOf(-60),
                ),
            )
            store.flush()

            assertEquals(MULTIBAND_CROSSOVER_COUNT, store.state.value.multibandCompressor.crossovers.size)
            assertEquals(listOf(-48, -18, -18, -18, -18), store.state.value.multibandCompressor.thresholds)
            assertEquals(1, target.commands.size)
            assertTrue(target.commands.single() is EffectDispatchCommand.FullState)
            assertEquals(1, writer.batches.size)
            assertTrue(writer.writes.isEmpty())
            assertEquals(
                setOf(
                    Effects.multibandCompressor.crossovers,
                    Effects.multibandCompressor.thresholds,
                ),
                writer.batches.single().map { it.pref }.toSet(),
            )
        }

    @Test
    fun bandUpdateDispatchesAndPersistsTheNormalizedValue() =
        runBlocking {
            val writer = RecordingPreferenceWriter()
            val target = RecordingDispatchTarget()
            val store = newStore(writer)
            store.replaceState(
                EffectState(
                    masterEnable = true,
                    multibandCompressor = MultibandCompressorState(enable = true),
                ),
            )
            store.attachDispatchTarget(target)
            target.commands.clear()

            store.updateBandPref(
                pref = Effects.multibandCompressor.gains,
                band = 1,
                value = -12,
            )
            store.flush()

            assertEquals(0, store.state.value.multibandCompressor.gains[1])
            assertEquals(
                EffectDispatchCommand.Band(
                    paramId = Effects.multibandCompressor.gains.paramId,
                    band = 1,
                    rawValue = 0,
                    last = true,
                ),
                target.commands.single(),
            )
            @Suppress("UNCHECKED_CAST")
            assertEquals(0, (writer.writes.single().value as List<Int>)[1])
        }

    @Test
    fun telemetryReadsDelegateOnlyWhileServiceIsAttached() {
        val target = RecordingDispatchTarget()
        val store = newStore(RecordingPreferenceWriter())
        target.telemetry =
            DriverTelemetry(
                sequence = 9,
                sampleRate = 48_000,
                fftSize = 2048,
                validMask = DriverTelemetry.SPECTRUM_VALID,
                overrunCount = 0,
                spectrumDb = List(64) { -40f },
                meterDb = List(8) { 0f },
            )

        assertEquals(null, store.readTelemetry())
        store.attachDispatchTarget(target)
        assertEquals(9, store.readTelemetry()?.sequence)
        store.attachDispatchTarget(null)
        assertEquals(null, store.readTelemetry())
    }

    @Test
    fun freezeCommandUpdatesTransientStateWithoutPreferenceWrite() =
        runBlocking {
            val writer = RecordingPreferenceWriter()
            val target = RecordingDispatchTarget()
            val store = newStore(writer)
            store.replaceState(EffectState(masterEnable = true))
            store.attachDispatchTarget(target)
            target.commands.clear()

            store.dispatchTransientIemCommand(ViperParams.COMMAND_IEM_GRANULAR_FREEZE, 1)
            store.flush()

            assertTrue(store.state.value.iem.freeze)
            assertEquals(
                listOf(
                    EffectDispatchCommand.Scalar(
                        ViperParams.COMMAND_IEM_GRANULAR_FREEZE,
                        1,
                        true,
                    ),
                ),
                target.commands,
            )
            assertTrue(writer.writes.isEmpty())
        }

    private fun newStore(writer: RecordingPreferenceWriter): EffectStateStore =
        EffectStateStore(
            preferenceWriter = writer,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
}

private class RecordingPreferenceWriter : EffectPreferenceWriter {
    val writes = mutableListOf<EffectPreferenceWrite>()
    val batches = mutableListOf<List<EffectPreferenceWrite>>()

    override suspend fun write(
        pref: EffectPref<*>,
        value: Any?,
    ) {
        writes += EffectPreferenceWrite(pref, value)
    }

    override suspend fun writeBatch(writes: List<EffectPreferenceWrite>) {
        batches += writes
    }

    fun clear() {
        writes.clear()
        batches.clear()
    }
}

private class RecordingDispatchTarget : EffectDispatchTarget {
    val commands = mutableListOf<EffectDispatchCommand>()
    private var stateProvider: (() -> EffectState)? = null
    var telemetry: DriverTelemetry? = null
    var iemTelemetry: IemDriverTelemetry? = null

    override fun setStateProvider(provider: () -> EffectState) {
        stateProvider = provider
    }

    override fun dispatch(command: EffectDispatchCommand) {
        commands += command
    }

    override fun readTelemetry(): DriverTelemetry? = telemetry

    override fun readIemTelemetry(): IemDriverTelemetry? = iemTelemetry
}

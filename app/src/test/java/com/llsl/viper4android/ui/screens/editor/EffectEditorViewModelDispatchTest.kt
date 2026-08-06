package com.llsl.viper4android.ui.screens.editor

import com.llsl.viper4android.effect.EffectDispatchCommand
import com.llsl.viper4android.effect.EffectDispatchTarget
import com.llsl.viper4android.effect.EffectPref
import com.llsl.viper4android.effect.EffectPreferenceWriter
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.EffectStateStore
import com.llsl.viper4android.effect.Effects
import com.llsl.viper4android.effect.DynamicEqState
import com.llsl.viper4android.effect.MultibandCompressorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectEditorViewModelDispatchTest {
    @Test
    fun crossoverEditDispatchesToTheRunningDsp() =
        runBlocking {
            val target = RecordingTarget()
            val store = newStore()
            store.replaceState(
                EffectState(
                    masterEnable = true,
                    multibandCompressor = MultibandCompressorState(enable = true),
                ),
            )
            store.attachDispatchTarget(target)
            target.commands.clear()
            val viewModel = EffectEditorViewModel(store)

            viewModel.updateMultibandCrossover(1, 800)

            assertEquals(800, store.state.value.multibandCompressor.crossovers[1])
            assertTrue(
                "crossover edit must reach the DSP, got ${target.commands}",
                target.commands.isNotEmpty(),
            )
        }

    @Test
    fun dynamicEqResetKeepsBandCountConsistentWithBandLists() =
        runBlocking {
            val store = newStore()
            store.replaceState(
                EffectState(
                    masterEnable = true,
                    dynamicEq = DynamicEqState(
                        enable = true,
                        bandCount = 10,
                        freqs = List(10) { 1000 },
                        qs = List(10) { 100 },
                        gains = List(10) { 30 },
                        thresholds = List(10) { -200 },
                        attacks = List(10) { 10 },
                        releases = List(10) { 100 },
                        filterTypes = List(10) { 0 },
                    ),
                ),
            )
            val viewModel = EffectEditorViewModel(store)

            viewModel.reset(EditorKind.DYNAMIC_EQUALIZER)

            val dynamic = store.state.value.dynamicEq
            assertEquals(
                "bandCount must match the reset band lists",
                dynamic.freqs.size,
                dynamic.bandCount,
            )
            assertEquals(3, dynamic.bandCount)
        }

    @Test
    fun multibandResetDispatchesToTheRunningDsp() =
        runBlocking {
            val target = RecordingTarget()
            val store = newStore()
            store.replaceState(
                EffectState(
                    masterEnable = true,
                    multibandCompressor = MultibandCompressorState(enable = true),
                ),
            )
            store.attachDispatchTarget(target)
            target.commands.clear()
            val viewModel = EffectEditorViewModel(store)

            viewModel.reset(EditorKind.MULTIBAND_COMPRESSOR)

            assertTrue(
                "reset must reach the DSP, got ${target.commands}",
                target.commands.isNotEmpty(),
            )
        }

    @Test
    fun dynamicEqResetDispatchesToTheRunningDsp() =
        runBlocking {
            val target = RecordingTarget()
            val store = newStore()
            store.replaceState(
                EffectState(
                    masterEnable = true,
                    dynamicEq = DynamicEqState(enable = true),
                ),
            )
            store.attachDispatchTarget(target)
            target.commands.clear()
            val viewModel = EffectEditorViewModel(store)

            viewModel.reset(EditorKind.DYNAMIC_EQUALIZER)

            assertTrue(
                "reset must reach the DSP, got ${target.commands}",
                target.commands.isNotEmpty(),
            )
        }

    @Test
    fun crossoverEditsStayOrderedAfterDispatch() =
        runBlocking {
            val store = newStore()
            store.replaceState(
                EffectState(
                    masterEnable = true,
                    multibandCompressor = MultibandCompressorState(enable = true),
                ),
            )
            val viewModel = EffectEditorViewModel(store)

            viewModel.updateMultibandCrossover(0, 19_000)

            val crossovers = store.state.value.multibandCompressor.crossovers
            assertTrue(
                "crossovers must stay strictly increasing, got $crossovers",
                crossovers.zipWithNext().all { (a, b) -> a < b },
            )
        }

    @Test
    fun multibandActionProtocolDispatchesEveryControlFamily() =
        runBlocking {
            val target = RecordingTarget()
            val store = newStore()
            store.replaceState(
                EffectState(
                    masterEnable = true,
                    multibandCompressor = MultibandCompressorState(enable = true),
                ),
            )
            store.attachDispatchTarget(target)
            target.commands.clear()
            val viewModel = EffectEditorViewModel(store)

            listOf(
                MultibandEditorAction.SetInt(MultibandIntControl.THRESHOLD, 0, -12, true),
                MultibandEditorAction.SetInt(MultibandIntControl.RATIO, 0, 75, true),
                MultibandEditorAction.SetInt(MultibandIntControl.GAIN, 0, 6, true),
                MultibandEditorAction.SetInt(MultibandIntControl.KNEE, 0, 6, true),
                MultibandEditorAction.SetInt(MultibandIntControl.KNEE_MULTI, 0, 40, true),
                MultibandEditorAction.SetInt(MultibandIntControl.ATTACK, 0, 20, true),
                MultibandEditorAction.SetInt(MultibandIntControl.MAX_ATTACK, 0, 60, true),
                MultibandEditorAction.SetInt(MultibandIntControl.RELEASE, 0, 250, true),
                MultibandEditorAction.SetInt(MultibandIntControl.MAX_RELEASE, 0, 400, true),
                MultibandEditorAction.SetInt(MultibandIntControl.CREST, 0, 120, true),
                MultibandEditorAction.SetInt(MultibandIntControl.ADAPT, 0, 80, true),
                MultibandEditorAction.SetBoolean(MultibandBooleanControl.BAND_ENABLE, 0, false, true),
                MultibandEditorAction.SetBoolean(MultibandBooleanControl.KNEE_AUTO, 0, false, true),
                MultibandEditorAction.SetBoolean(MultibandBooleanControl.GAIN_AUTO, 0, false, true),
                MultibandEditorAction.SetBoolean(MultibandBooleanControl.ATTACK_AUTO, 0, false, true),
                MultibandEditorAction.SetBoolean(MultibandBooleanControl.RELEASE_AUTO, 0, false, true),
                MultibandEditorAction.SetBoolean(MultibandBooleanControl.NO_CLIP, 0, false, true),
            ).forEach(viewModel::handleMultibandEditorAction)

            val compressor = store.state.value.multibandCompressor
            assertEquals(-12, compressor.thresholds[0])
            assertEquals(75, compressor.ratios[0])
            assertEquals(6, compressor.gains[0])
            assertEquals(6, compressor.knees[0])
            assertEquals(40, compressor.kneeMultis[0])
            assertEquals(20, compressor.attacks[0])
            assertEquals(60, compressor.maxAttacks[0])
            assertEquals(250, compressor.releases[0])
            assertEquals(400, compressor.maxReleases[0])
            assertEquals(120, compressor.crests[0])
            assertEquals(80, compressor.adapts[0])
            assertEquals(false, compressor.bandEnables[0])
            assertEquals(false, compressor.kneeAutos[0])
            assertEquals(false, compressor.gainAutos[0])
            assertEquals(false, compressor.attackAutos[0])
            assertEquals(false, compressor.releaseAutos[0])
            assertEquals(false, compressor.noClips[0])
            assertEquals(17, target.commands.size)
            val commands = target.commands.map { it as EffectDispatchCommand.Band }
            assertEquals(Effects.multibandCompressor.thresholds.elementToRaw(-12), commands[0].rawValue)
            assertEquals(Effects.multibandCompressor.ratios.elementToRaw(75), commands[1].rawValue)
            assertEquals(Effects.multibandCompressor.gains.elementToRaw(6), commands[2].rawValue)
            assertEquals(Effects.multibandCompressor.bandEnables.elementToRaw(false), commands[11].rawValue)
            assertTrue(commands.all { it.last })
        }

    @Test
    fun twoAxisCrossoverGestureSettlesAsOneUndoOperation() =
        runBlocking {
            val store = newStore()
            store.replaceState(
                EffectState(
                    masterEnable = true,
                    multibandCompressor =
                        MultibandCompressorState(
                            enable = true,
                            gainAutos = List(5) { false },
                        ),
                ),
            )
            val target = RecordingTarget()
            store.attachDispatchTarget(target)
            target.commands.clear()
            val viewModel = EffectEditorViewModel(store)
            val original = store.state.value.multibandCompressor

            viewModel.handleMultibandEditorAction(MultibandEditorAction.BeginGesture)
            viewModel.handleMultibandEditorAction(
                MultibandEditorAction.SetCrossoverHandle(1, 700, 4, last = false),
            )
            viewModel.handleMultibandEditorAction(
                MultibandEditorAction.SetCrossoverHandle(1, 900, 8, last = false),
            )
            viewModel.handleMultibandEditorAction(
                MultibandEditorAction.SetCrossoverHandle(1, 900, 8, last = true),
            )
            viewModel.handleMultibandEditorAction(MultibandEditorAction.SettleGesture)

            assertEquals(1, viewModel.undoCount.value)
            assertEquals(900, store.state.value.multibandCompressor.crossovers[1])
            assertEquals(8, store.state.value.multibandCompressor.gains[1])
            val commands = target.commands.map { it as EffectDispatchCommand.Band }
            assertTrue(commands.dropLast(1).all { !it.last })
            assertTrue(commands.last().last)
            viewModel.undo()
            assertEquals(original, store.state.value.multibandCompressor)
            viewModel.redo()
            assertEquals(900, store.state.value.multibandCompressor.crossovers[1])
            assertEquals(8, store.state.value.multibandCompressor.gains[1])
        }

    @Test
    fun multibandResetRestoresAdvancedParametersAndPreservesEffectEnable() =
        runBlocking {
            val store = newStore()
            store.replaceState(
                EffectState(
                    masterEnable = true,
                    multibandCompressor =
                        MultibandCompressorState(
                            enable = true,
                            bandEnables = List(5) { false },
                            crossovers = listOf(200, 800, 3000, 12_000),
                            thresholds = List(5) { -6 },
                            ratios = List(5) { 90 },
                            gains = List(5) { 12 },
                            knees = List(5) { 12 },
                            kneeMultis = List(5) { 80 },
                            attacks = List(5) { 40 },
                            maxAttacks = List(5) { 80 },
                            releases = List(5) { 400 },
                            maxReleases = List(5) { 500 },
                            crests = List(5) { 200 },
                            adapts = List(5) { 150 },
                            kneeAutos = List(5) { false },
                            gainAutos = List(5) { false },
                            attackAutos = List(5) { false },
                            releaseAutos = List(5) { false },
                            noClips = List(5) { false },
                        ),
                ),
            )
            val viewModel = EffectEditorViewModel(store)

            viewModel.reset(EditorKind.MULTIBAND_COMPRESSOR)

            assertTrue(store.state.value.masterEnable)
            assertEquals(MultibandCompressorState().copy(enable = true), store.state.value.multibandCompressor)
            assertEquals(1, viewModel.undoCount.value)
        }

    private fun newStore(): EffectStateStore =
        EffectStateStore(
            preferenceWriter = NoopWriter(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
}

private class NoopWriter : EffectPreferenceWriter {
    override suspend fun write(
        pref: EffectPref<*>,
        value: Any?,
    ) = Unit
}

private class RecordingTarget : EffectDispatchTarget {
    val commands = mutableListOf<EffectDispatchCommand>()

    override fun setStateProvider(provider: () -> EffectState) = Unit

    override fun dispatch(command: EffectDispatchCommand) {
        commands += command
    }
}

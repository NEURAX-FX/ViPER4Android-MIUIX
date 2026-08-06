# Multiband Compressor Editor Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Use `superpowers:test-driven-development` for every production change and `superpowers:verification-before-completion` before any completion claim.

**Goal:** Replace the current incomplete multiband compressor editor with the approved two-graph, five-band editor while restoring every original DSP parameter, honest Auto-mode behavior, live dispatch, persistence, undo, reset, and preview-only main-card behavior.

**Architecture:** Keep `EffectStateStore` as the sole mutable state owner. Add a canonical multiband contract/normalizer, a pure compressor transfer model, richer graph presentation models, and a dedicated `MultibandCompressorEditor` composable. Reuse the API 29 Android `RenderNode` graph path and extend it only for reference curves, per-band colors, region selection, locked axes, and frame-coalesced drag delivery.

**Tech Stack:** Kotlin, Jetpack Compose, MiuiX KMP 0.9.x, Android `RenderNode`, Hilt, StateFlow, DataStore Preferences, JUnit, Compose UI tests, Android bound service.

**Design Spec:** `docs/superpowers/specs/2026-08-06-multiband-compressor-editor-redesign.md`

## Global Constraints

- Run every Gradle build and test through the configured remote shell. Do not run local Gradle tasks.
- Use raw remote Gradle output. Do not pipe build logs through `grep`, `tail`, or other filters.
- Before a remote command, sync only files intentionally changed by the current task into `~/ViPER4Android`; do not use destructive sync flags such as `--delete`.
- Use MiuiX public APIs and project-local wrappers. Do not edit MiuiX source.
- Do not add Material3 UI components. Keep Material Icons.
- Keep five bands, four crossover frequencies, all original audio parameters, and existing raw driver payloads.
- Do not claim that a static graph is live gain reduction.
- Keep the main card preview-only and retain explicit editor access in no-graph mode.
- Keep each task buildable and testable before starting the next task.
- Do not create commits unless the user explicitly requests them.

## Remote Commands

Example targeted unit test:

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:testDebugUnitTest --tests 'com.llsl.viper4android.effect.MultibandCompressorContractTest' --stacktrace --no-daemon 2>&1"
```

All unit tests:

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:testDebugUnitTest --stacktrace --no-daemon 2>&1"
```

Debug build:

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"
```

Instrumented tests when a device is available:

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:connectedDebugAndroidTest --stacktrace --no-daemon 2>&1"
```

---

### Task 1: Canonical Multiband State And Atomic Persistence

**Files:**

- Create: `app/src/main/java/com/llsl/viper4android/effect/MultibandCompressorContract.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/effect/EffectStateStore.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/data/repository/ViperRepository.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/GraphMapping.kt`
- Create: `app/src/test/java/com/llsl/viper4android/effect/MultibandCompressorContractTest.kt`
- Modify: `app/src/test/java/com/llsl/viper4android/effect/EffectStateStoreTest.kt`
- Modify: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/GraphMappingTest.kt`

**Interfaces:**

- Produces `MULTIBAND_BAND_COUNT = 5` and `MULTIBAND_CROSSOVER_COUNT = 4`.
- Produces `normalizeMultibandCompressorState(state)`.
- Produces `normalizeMultibandCrossovers(values, maxFrequency)`.
- Keeps `constrainCrossovers(...)` as the editor-facing changed-index constraint helper.
- Produces one batched DataStore edit for `EffectStateStore.applyTransaction(...)`.

- [ ] **Step 1: Add failing contract tests**

Test all lists using malformed short, long, and out-of-range input. Assert exact canonical lengths, defaults, clamping, and one-semitone crossover ordering.

```kotlin
@Test
fun normalizeStatePadsEveryBandListAndTrimsExtras() {
    val normalized = normalizeMultibandCompressorState(
        MultibandCompressorState(
            crossovers = listOf(120),
            thresholds = listOf(-60, -12, 4, -24, -18, -9),
            ratios = emptyList(),
            gains = listOf(-8, 30),
            bandEnables = listOf(false),
        ),
    )

    assertEquals(listOf(120, 500, 4000, 8000), normalized.crossovers)
    assertEquals(listOf(-48, -12, 0, -24, -18), normalized.thresholds)
    assertEquals(listOf(50, 50, 50, 50, 50), normalized.ratios)
    assertEquals(listOf(0, 24, 0, 0, 0), normalized.gains)
    assertEquals(listOf(false, true, true, true, true), normalized.bandEnables)
}
```

Add crossover cases where all values are `30`, all are `16000`, and neighbors are closer than one semitone. Assert the returned values remain ordered and bounded.

- [ ] **Step 2: Add a failing store batch test**

Extend the fake writer to record `writeBatch` separately from individual writes. Call `applyTransaction` with all multiband preferences and assert:

- State is normalized before publication.
- One full DSP dispatch occurs.
- One writer batch occurs.
- No individual persistence calls occur for that transaction.

- [ ] **Step 3: Run the targeted tests and confirm RED**

Run the contract, store, and graph mapping test classes through SSH. Expected failures are unresolved contract functions and missing batch writer support.

- [ ] **Step 4: Implement the canonical contract**

Use canonical defaults matching `MultibandCompressorState` and `MultibandCompressorEffect`.

```kotlin
const val MULTIBAND_BAND_COUNT = 5
const val MULTIBAND_CROSSOVER_COUNT = 4
const val MULTIBAND_MIN_FREQUENCY = 30
const val MULTIBAND_MAX_FREQUENCY = 16_000
val MULTIBAND_SPACING_RATIO: Double = 2.0.pow(1.0 / 12.0)

fun normalizeMultibandCompressorState(
    state: MultibandCompressorState,
    maxCrossoverFrequency: Int = MULTIBAND_MAX_FREQUENCY,
): MultibandCompressorState
```

Normalize every per-band list to five entries and crossovers to four entries. Use the matching canonical default for missing entries, discard entries after the required count, and clamp values to the ranges declared in `EffectGroups.kt`.

Implement crossover normalization as clamp, forward spacing pass, then backward spacing pass. Keep the old `constrainCrossovers` symbol in `GraphMapping.kt`, but delegate its shared spacing math to the canonical helper so store normalization and drag behavior cannot diverge.

- [ ] **Step 5: Normalize every store mutation boundary**

Normalize after `replaceState`, `restoreState`, `updateState`, `updatePref`, `updateBandPref`, and `applyTransaction`, before assigning `mutableState.value`, dispatching, or persisting. If `replaceState` repairs malformed loaded preferences, persist the corrected multiband preference set in one batch so the next launch does not reload the same malformed data.

Do not persist the caller's raw value if normalization changed it. Read the final value back from the normalized state and persist that value.

- [ ] **Step 6: Add batched repository writes**

Add a typed repository batch method that performs one `dataStore.edit` block.

```kotlin
suspend fun setPreferences(
    booleans: Map<String, Boolean> = emptyMap(),
    ints: Map<String, Int> = emptyMap(),
    strings: Map<String, String> = emptyMap(),
) {
    ensureInitialized()
    dataStore.edit { preferences ->
        booleans.forEach { (key, value) -> preferences[booleanPreferencesKey(key)] = value }
        ints.forEach { (key, value) -> preferences[intPreferencesKey(key)] = value }
        strings.forEach { (key, value) -> preferences[stringPreferencesKey(key)] = value }
    }
}
```

Extend `EffectPreferenceWriter` with `writeBatch(edits)`. The production writer groups edits by raw value type and calls `ViperRepository.setPreferences`. Preserve `write(...)` for normal single-parameter gestures.

- [ ] **Step 7: Run targeted tests and all unit tests**

Expected: contract, store, mapping, and existing unit tests pass. Review the raw logs before proceeding.

---

### Task 2: Exact Compressor Transfer Model And Ratio Labels

**Files:**

- Create: `app/src/main/java/com/llsl/viper4android/dsp/MultibandCompressorTransfer.kt`
- Create: `app/src/test/java/com/llsl/viper4android/dsp/MultibandCompressorTransferTest.kt`

**Interfaces:**

- Produces `MultibandTransferSpec`.
- Produces `compressorOutputDb(inputDb, spec)`.
- Produces `multibandTransferCurve(spec, sampleCount)`.
- Produces `ratioCoefficientForOutput(...)` for graph Y dragging.
- Produces `multibandRatioLabel(rawRatio)` as a localizable semantic value.

- [ ] **Step 1: Write failing transfer tests**

Cover:

- Hard knee below, at, and above Threshold.
- Soft knee lower edge, center, and upper edge.
- Manual Makeup Gain vertical offset.
- `Knee = 0` without division by zero.
- Ratio coefficient inversion from a dragged endpoint.
- Ratio label models for `0`, `50`, `75`, `90`, `95`, `100`, `101`, and `200`.
- Finite output for every canonical parameter extreme.

```kotlin
@Test
fun hardKneeRatioFiftyProducesTwoToOneSlope() {
    val spec = MultibandTransferSpec(
        thresholdDb = -18.0,
        ratioRaw = 50,
        kneeDb = 0.0,
        makeupGainDb = 0.0,
    )

    assertEquals(-24.0, compressorOutputDb(-24.0, spec), 1e-6)
    assertEquals(-18.0, compressorOutputDb(-18.0, spec), 1e-6)
    assertEquals(-12.0, compressorOutputDb(-6.0, spec), 1e-6)
}
```

- [ ] **Step 2: Run the test and confirm RED**

Expected: unresolved transfer-model symbols.

- [ ] **Step 3: Implement the driver-equivalent steady-state math**

```kotlin
data class MultibandTransferSpec(
    val thresholdDb: Double,
    val ratioRaw: Int,
    val kneeDb: Double,
    val makeupGainDb: Double,
)

fun compressorOutputDb(inputDb: Double, spec: MultibandTransferSpec): Double {
    val difference = inputDb - spec.thresholdDb
    val halfKnee = spec.kneeDb / 2.0
    val reductionBase = when {
        spec.kneeDb <= 0.0 -> difference.coerceAtLeast(0.0)
        difference <= -halfKnee -> 0.0
        difference >= halfKnee -> difference
        else -> (difference + halfKnee).pow(2.0) / (2.0 * spec.kneeDb)
    }
    return inputDb + spec.makeupGainDb - reductionBase * (spec.ratioRaw / 100.0)
}
```

Generate normalized graph points over `-60..0 dB` input and `-60..24 dB` output. Clamp only graph coordinates, not the mathematical output returned by `compressorOutputDb`.

Implement inverse Ratio calculation using the reduction basis at the fixed high-input handle. If the basis is effectively zero, retain the current ratio rather than dividing by zero.

- [ ] **Step 4: Implement honest, localizable Ratio labels**

Return semantic data rather than hard-coded UI text:

```kotlin
sealed interface MultibandRatioLabel {
    data class Conventional(val ratio: Double) : MultibandRatioLabel
    data object Limit : MultibandRatioLabel
    data class Over(val percent: Int) : MultibandRatioLabel
}
```

Return `Conventional` below the limiter point, `Limit` at `100`, and `Over` above it. Keep the raw `0..200` value available to exact-value input. Localize the final string in the editor presentation layer.

- [ ] **Step 5: Run transfer tests and all unit tests**

Expected: PASS.

---

### Task 3: Rebuild Multiband Graph Models

**Files:**

- Modify: `app/src/main/java/com/llsl/viper4android/dsp/ResponseCurve.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/dsp/EffectGraphModel.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/dsp/GraphSampleRate.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/GraphMapping.kt`
- Modify: `app/src/test/java/com/llsl/viper4android/dsp/EffectGraphModelTest.kt`
- Modify: `app/src/test/java/com/llsl/viper4android/dsp/MultibandCrossoverResponseTest.kt`
- Modify: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/GraphMappingTest.kt`

**Interfaces:**

- `MultibandGraphModel.handles` contains exactly four crossover handles.
- `MultibandGraphModel` exposes five structural curves, one unity-sum curve, five band regions, stored gains, Auto Gain flags, and band-enable flags.
- Produces `safeMultibandCrossoverMax(sampleRate)`.
- Produces linear transfer-axis mapping helpers and band-region hit testing.

- [ ] **Step 1: Replace the old threshold-handle tests with failing four-handle tests**

Assert:

- Exactly four handles exist.
- Handle X positions match the four crossovers.
- Handle Y positions match Bands 1 through 4 stored Makeup Gain values.
- Auto Gain bands retain the stored handle position but do not shift the plotted structural curve.
- Manual Gain bands shift their structural curve by the exact dB value.
- Band 5 remains in the curve and region lists without a fifth handle.
- The pre-compressor unity-sum curve remains near `0 dB` away from numerical endpoints.

- [ ] **Step 2: Add failing safe-bound and region tests**

Test sample rates `32000`, `44100`, `48000`, `96000`, invalid sample rates, and band-region taps at each boundary.

```kotlin
fun safeMultibandCrossoverMax(sampleRate: Int): Int =
    minOf(MULTIBAND_MAX_FREQUENCY, floor(graphMaxFrequency(sampleRate)).toInt())
```

- [ ] **Step 3: Run targeted tests and confirm RED**

Expected: old model still exposes threshold handles and lacks gain/reference fields.

- [ ] **Step 4: Extend crossover sampling**

Add optional per-band dB offsets to `multibandCrossoverCurves`. The default remains five zeros so existing callers and tests retain current behavior.

Add a unity-sum curve that uses the complex crossover sum before compressor gain. Do not sum already normalized screen coordinates.

- [ ] **Step 5: Replace `multibandGraphModel`**

Use normalized multiband state. Build four handle models with IDs `crossover-0` through `crossover-3`. Use `0..24 dB` for Y mapping and `30..safeMax` for logarithmic X mapping.

Effective plotted offsets are:

```kotlin
val plottedGains = List(MULTIBAND_BAND_COUNT) { band ->
    if (mbc.gainAutos[band]) 0.0 else mbc.gains[band].toDouble()
}
```

Keep stored gains separately so an Auto-locked handle can still describe the configured manual value.

- [ ] **Step 6: Add transfer-axis helpers**

Add pure `linearValueToX`, `xToLinearValue`, `linearValueToY`, and `yToLinearValue` helpers. Reuse them for Threshold, Ratio endpoint, Knee width, and transfer graph semantics.

- [ ] **Step 7: Run targeted and all unit tests**

Expected: PASS.

---

### Task 4: Extend The RenderNode Graph Surface

**Files:**

- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/GraphDragReducer.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstResponseGraph.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/ResponseRenderNode.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/ResponseDisplayListCache.kt`
- Create: `app/src/test/java/com/llsl/viper4android/ui/components/viper/GraphDragReducerTest.kt`
- Modify: `app/src/test/java/com/llsl/viper4android/ui/components/viper/ResponseDisplayListCacheTest.kt`

**Interfaces:**

- `GraphHandle` gains `enabled`, `badge`, and a semantic description without breaking existing call sites.
- `VstResponseGraph` gains reference curves, per-band colors, selected region, region click, dashed main-curve mode, and a final settled drag callback.
- Drag delivery is coalesced to at most one callback per rendered frame and always emits the exact final point.

- [ ] **Step 1: Add failing reducer tests**

Test:

- Latest sample wins within one frame.
- Final drag sample is emitted even when no frame occurs before pointer-up.
- `HORIZONTAL` preserves the starting Y value.
- `VERTICAL` preserves the starting X value.
- `FREE` changes both coordinates.
- Disabled handles cannot be captured.
- Cancellation emits one settled sample and clears capture.
- A second pointer cannot capture another handle during an active drag.

- [ ] **Step 2: Add failing display-list identity tests**

Assert cache re-recording when any of these change:

- Reference curve content.
- Main curve dash mode.
- Per-band color list.
- Selected region changes do not invalidate the display-list cache because selection is a dynamic overlay.
- Graph size or existing style revision.

- [ ] **Step 3: Run targeted tests and confirm RED**

- [ ] **Step 4: Implement a pure latest-sample reducer**

```kotlin
internal data class GraphDragSample(
    val handleId: String,
    val x: Float,
    val y: Float,
)

internal class GraphDragReducer {
    private var activeHandleId: String? = null
    private var pending: GraphDragSample? = null

    fun begin(handleId: String): Boolean {
        if (activeHandleId != null) return false
        activeHandleId = handleId
        return true
    }

    fun offer(sample: GraphDragSample): Boolean {
        if (sample.handleId != activeHandleId) return false
        pending = sample
        return true
    }

    fun drain(): GraphDragSample? = pending.also { pending = null }

    fun finish(): GraphDragSample? {
        val finalSample = drain()
        activeHandleId = null
        return finalSample
    }
}
```

Add a pure `applyGraphDragAxis(axis, start, candidate)` helper beside the reducer. The composable schedules one `withFrameNanos` drain after the first pending sample. Pointer-up calls `finish()` synchronously before the settled callback.

- [ ] **Step 5: Extend `VstResponseGraph` without changing the rendering stack**

Add parameters with safe defaults:

```kotlin
referenceCurves: List<List<Offset>> = emptyList(),
bandCurveColors: List<Color> = emptyList(),
selectedBandRegionIndex: Int? = null,
onBandRegionSelected: ((Int) -> Unit)? = null,
curveDashed: Boolean = false,
onHandleDragSettled: (String, Float, Float) -> Unit = { _, _, _ -> },
```

Keep `onHandleDrag` for frame updates. Draw selected region and Auto badges as dynamic Compose overlays, not inside the cached static curve display list.

- [ ] **Step 6: Extend `ResponseRenderNode`**

Record grid, main curve, band curves, and dashed reference curves in one cached RenderNode. Use `DashPathEffect` only for reference or explicitly dashed curves. Preserve the current API 29 baseline with no fallback renderer.

- [ ] **Step 7: Update existing graph call sites for settled callbacks**

Keep FIR and Dynamic EQ behavior compiling with defaults. Do not redesign those editors in this task.

- [ ] **Step 8: Run component tests and all unit tests**

Expected: PASS and no new rendering backend.

---

### Task 5: Make `VstKnob` Support Disabled And Exact Raw Values

**Files:**

- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstKnob.kt`
- Create: `app/src/test/java/com/llsl/viper4android/ui/components/viper/VstKnobValueTest.kt`

**Interfaces:**

- Adds `enabled`, `disabledReason`, `onValueChangeStarted`, `inputValue`, and `parseInput`.
- Keeps existing call sites source-compatible through defaults.
- Makes exact-value input parse raw numbers rather than formatted labels with units.

- [ ] **Step 1: Add failing exact-input tests**

Test that display strings such as `12 ms`, `2:1`, and `+6 dB` are not reused as editable raw text. Test canonical parsing, range clamping, and invalid-input rejection.

- [ ] **Step 2: Run the test and confirm RED**

- [ ] **Step 3: Extend the component API**

```kotlin
enabled: Boolean = true,
disabledReason: String? = null,
onValueChangeStarted: () -> Unit = {},
inputValue: (Double) -> String = { value -> value.toString() },
parseInput: (String) -> Double? = { text -> text.trim().toDoubleOrNull() },
```

Call `onValueChangeStarted` once after drag slop is crossed and once before applying an exact dialog value. Keep `onValueChangeFinished` as the single settled callback.

When disabled:

- Do not install drag or click gestures.
- Render disabled opacity.
- Expose `disabledReason` through semantics.
- Keep the current value visible.

- [ ] **Step 4: Fix exact-value dialog initialization**

Initialize with `inputValue(value)`, not `formatValue(value)`. Parse with `parseInput`, clamp to the knob range, and leave the dialog open with an error when parsing fails.

- [ ] **Step 5: Run knob tests and all unit tests**

Expected: PASS; existing editors continue compiling unchanged.

---

### Task 6: Complete Multiband ViewModel Actions, History, And Reset

**Files:**

- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorViewModel.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/MultibandEditorAction.kt`
- Modify: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/EffectEditorViewModelDispatchTest.kt`
- Modify: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/EditorHistoryTest.kt`

**Interfaces:**

- Produces update actions for all original multiband fields.
- Produces one testable `MultibandEditorAction` sink between Compose and the ViewModel.
- Produces `updateMultibandCrossoverHandle(index, frequency, gain, last)`.
- Produces `performDiscreteEdit(edit: () -> Unit)` for switches and exact values.
- Produces safe sample-rate crossover clamping.
- Makes full reset one undoable atomic transaction.

- [ ] **Step 1: Add failing dispatch tests for every parameter family**

For one selected band, assert exact raw commands for:

- Band enable.
- Threshold.
- Ratio.
- Knee Auto, Knee, and Knee Multi.
- Gain Auto and Gain.
- Attack Auto, Attack, and Max Attack.
- Release Auto, Release, and Max Release.
- Crest.
- Adapt.
- No Clip.

Also simulate service reconnection and assert one full normalized multiband dispatch occurs before later incremental commands.

Assert each action updates state, dispatches one matching parameter index/value pair, and persists the matching key.

- [ ] **Step 2: Add failing two-axis gesture tests**

Start one gesture, update crossover frequency and Gain over multiple frames, settle once, then assert:

- Final state contains both values.
- Intermediate dispatches use `last = false`.
- The final changed command uses `last = true`.
- Undo restores both values together.
- Redo reapplies both values together.

- [ ] **Step 3: Add failing complete-reset tests**

Mutate every primary and advanced multiband field. Reset and assert:

- Global multiband enable is preserved.
- Every other field returns to the canonical default.
- One store transaction occurs.
- One full-state DSP dispatch occurs.
- One persistence batch occurs.
- One undo operation restores the pre-reset state.

- [ ] **Step 4: Run targeted tests and confirm RED**

- [ ] **Step 5: Add typed ViewModel actions**

Use `EffectStateStore.updateBandPref` for each per-band parameter. Every numeric action clamps to its `EffectGroups.kt` range before calling the store.

```kotlin
fun updateMultibandAutoGain(band: Int, enabled: Boolean, last: Boolean = true) =
    effectStateStore.updateBandPref(
        MultibandCompressorEffect.gainAutos,
        band,
        enabled,
        last = last,
    )
```

Implement analogous methods for all fields listed above.

Define a Compose-free action protocol so UI tests do not require a real Hilt ViewModel:

```kotlin
enum class MultibandIntControl {
    THRESHOLD,
    RATIO,
    GAIN,
    KNEE,
    KNEE_MULTI,
    ATTACK,
    MAX_ATTACK,
    RELEASE,
    MAX_RELEASE,
    CREST,
    ADAPT,
}

enum class MultibandBooleanControl {
    BAND_ENABLE,
    KNEE_AUTO,
    GAIN_AUTO,
    ATTACK_AUTO,
    RELEASE_AUTO,
    NO_CLIP,
}

sealed interface MultibandEditorAction {
    data object BeginGesture : MultibandEditorAction
    data object SettleGesture : MultibandEditorAction
    data object Flush : MultibandEditorAction
    data class SetInt(
        val control: MultibandIntControl,
        val band: Int,
        val value: Int,
        val last: Boolean,
    ) : MultibandEditorAction
    data class SetBoolean(
        val control: MultibandBooleanControl,
        val band: Int,
        val value: Boolean,
        val last: Boolean,
    ) : MultibandEditorAction
    data class SetCrossoverHandle(
        val crossover: Int,
        val frequency: Int,
        val gain: Int,
        val last: Boolean,
    ) : MultibandEditorAction
}
```

Add `handleMultibandEditorAction(action)` to the ViewModel and route every enum value to the typed update functions.

- [ ] **Step 6: Implement the two-axis action**

Constrain the frequency with the current `safeMultibandCrossoverMax(graphSampleRate.value)`. Update the crossover first and Gain second. Mark only the final emitted command as `last = true`.

If Auto Gain is on, ignore the supplied Y value and update only crossover frequency.

- [ ] **Step 7: Clamp crossovers when sample rate changes**

After refreshing the graph sample rate, normalize current crossovers against the new safe maximum. If values change, apply one store transaction so state, driver, and persistence remain aligned.

- [ ] **Step 8: Implement discrete history and full reset**

```kotlin
fun performDiscreteEdit(edit: () -> Unit) {
    beginGesture()
    edit()
    settleGesture()
}
```

Use a complete `EffectTransaction` for reset. Do not call 18 separate public update methods.

- [ ] **Step 9: Run targeted and all unit tests**

Expected: PASS.

---

### Task 7: Extract The Dedicated Multiband Editor And Build The Top Graph

**Files:**

- Create: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/MultibandCompressorEditor.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/MultibandEditorPresentation.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorScreen.kt`
- Create: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/MultibandEditorPresentationTest.kt`

**Interfaces:**

- `EffectEditorScreen` delegates the multiband branch to one extracted composable.
- `MultibandCompressorEditor` accepts `onAction: (MultibandEditorAction) -> Unit` instead of a ViewModel, allowing deterministic UI tests.
- Presentation model maps normalized state into five band items, five colors, four handles, lock badges, and selected-band semantics.
- The top graph supports region selection and free X/Y dragging with Auto Gain Y locking.

- [ ] **Step 1: Add failing presentation tests**

Assert:

- Five band items and four handles are produced.
- Handle `crossover-0` controls Band 1 Gain and handle `crossover-3` controls Band 4 Gain.
- Band 5 has no handle but remains selectable.
- Auto Gain changes a handle from `FREE` to `HORIZONTAL` and adds `AUTO` semantics.
- Five theme-derived colors remain stable by band index.
- Bypassed band compression changes presentation state without removing the band region.

- [ ] **Step 2: Run the test and confirm RED**

- [ ] **Step 3: Extract the current multiband composable unchanged**

Move the existing private `MultibandCompressorEditor` out of `EffectEditorScreen.kt` first. Replace its ViewModel parameter with `onAction: (MultibandEditorAction) -> Unit`, and adapt calls in `EffectEditorScreen` through `viewModel::handleMultibandEditorAction`. Compile remotely before redesigning it so extraction and behavior change are separate checkpoints.

- [ ] **Step 4: Implement the five-band presentation model**

Derive five colors using Compose `lerp(primary, secondary, fraction)` with fractions `0f`, `0.25f`, `0.5f`, `0.75f`, and `1f`. Keep selection shape/outline independent of color.

Build graph handles with:

```kotlin
dragAxis = if (state.gainAutos[band]) GraphDragAxis.HORIZONTAL else GraphDragAxis.FREE
badge = if (state.gainAutos[band]) "AUTO" else null
```

- [ ] **Step 5: Replace the top graph**

Render:

- Five structural band curves.
- One dashed unity-sum reference.
- Exactly four crossover handles.
- Selected region highlight.
- No threshold markers.

Region taps update `selectedBand`. Handle frame callbacks call `updateMultibandCrossoverHandle(index, frequency, gain, last = false)`. The settled callback replays the exact final index, frequency, and gain with `last = true`, then settles history and flushes persistence.

- [ ] **Step 6: Add band selector, compression-enable switch, and exact crossover controls**

Use `VstBandStrip` for the five-band selector and the existing MiuiX-backed `LabeledSwitch` for `Compression enabled for Band N`.

Provide four compact `VstKnob` crossover controls with the same safe max and neighbor validation as the graph.

- [ ] **Step 7: Run presentation tests, all unit tests, and `assembleDebug`**

Expected: top graph is functional and the old five-knob deck still compiles temporarily below it.

---

### Task 8: Add The Transfer Graph And Primary Controls

**Files:**

- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/MultibandCompressorEditor.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/MultibandEditorPresentation.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/MultibandEditorPresentationTest.kt`

**Interfaces:**

- Adds one transfer graph for the selected band.
- Adds Threshold, Auto Knee, Ratio, Knee, Auto Gain, Gain, Auto Attack, Attack, Auto Release, and Release controls.
- Auto dependencies disable ignored knobs and graph handles.

- [ ] **Step 1: Add failing transfer-handle presentation tests**

Assert manual mode produces:

- Threshold handle with horizontal drag.
- Ratio endpoint handle with vertical drag.
- Knee-width handle with horizontal drag.

Assert Auto Knee removes or disables Ratio and Knee handles while leaving Threshold active. Assert Auto Gain excludes manual Gain from the plotted curve and disables top-graph Y editing.

- [ ] **Step 2: Run the test and confirm RED**

- [ ] **Step 3: Build the transfer card**

Use `VstResponseGraph` with linear dB grid positions, the calculated transfer curve, and a dashed `1:1` reference. Use `-60..0 dB` input and `-60..24 dB` output.

Handle mapping:

- Threshold X maps directly to `-48..0 dB`.
- Ratio Y uses `ratioCoefficientForOutput` at `0 dB` input.
- Knee handle X maps to `threshold + knee / 2`, then converts back to `knee = 2 * abs(handleInput - threshold)` clamped to `0..12 dB`.

- [ ] **Step 4: Implement honest Auto-mode graph states**

- Auto Knee: dashed dynamic reference, Threshold only, message `Auto Knee: live curve unavailable`.
- Auto Gain: omit unknown vertical automatic gain, message `Auto Gain excluded from preview`.
- Auto Attack/Release: keep steady-state shape and state that timing is not shown.

Do not add animated fake gain reduction.

- [ ] **Step 5: Replace the temporary knob deck with primary controls**

Use `VstKnob` plus paired `LabeledSwitch` controls. Apply these enable rules:

- Auto Knee disables Ratio and Knee.
- Auto Gain disables Gain and top-graph Y editing.
- Auto Attack disables Attack.
- Auto Release disables Release.

Format `multibandRatioLabel` through localized resources and use raw integer text for exact Ratio input.

- [ ] **Step 6: Add localized strings**

Add English, Simplified Chinese, and Russian strings for:

- Crossover response title and description.
- Compressor transfer title and description.
- Compression enabled for selected band.
- Auto Knee live-curve limitation.
- Auto Gain preview limitation.
- Ratio `Limit` and `Over N%` labels.
- Disabled-control reasons.

- [ ] **Step 7: Run presentation tests, all unit tests, and `assembleDebug`**

Expected: both graphs and primary controls compile and dispatch through the ViewModel.

---

### Task 9: Restore Advanced Controls And Responsive Layout

**Files:**

- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstExpandableControlGroup.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/MultibandCompressorEditor.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/MultibandEditorPresentation.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/MultibandEditorPresentationTest.kt`

**Interfaces:**

- Restores Knee Multi, Max Attack, Max Release, Crest, Adapt, and No Clip.
- Adds portrait/narrow stacked layout and wide two-column layout.
- Advanced-panel expanded state remains editor-local and is not persisted as DSP state.

- [ ] **Step 1: Add failing control-availability tests**

Assert:

- Knee Multi enabled only when Auto Knee is on.
- Max Attack enabled only when Auto Attack is on.
- Max Release enabled only when Auto Release is on.
- No Clip enabled only when Auto Gain is on.
- Crest always enabled.
- Adapt enabled when Auto Knee or Auto Gain is on.
- Bypassed band settings remain editable but are marked as bypassed preview.

- [ ] **Step 2: Run the test and confirm RED**

- [ ] **Step 3: Implement `VstExpandableControlGroup`**

Use MiuiX theme values and Material Icons `ExpandMore`/`ExpandLess`. The entire visible header is a normal click target; no long press. Expose expanded state and content semantics.

- [ ] **Step 4: Add all advanced controls**

Use the canonical ranges from `MultibandCompressorEffect`. Pair Max Attack and Max Release with their owning Auto modes. Keep Crest enabled in every mode and explain Adapt/No Clip disabled states.

- [ ] **Step 5: Refactor the multiband screen layout**

In narrow constraints, render one vertically scrolling column in this order:

1. Crossover graph.
2. Transfer graph.
3. Band selector and compression-enable switch.
4. Primary controls.
5. Advanced controls.

At wide constraints, render a `Row`:

- Left weighted column: crossover graph above transfer graph.
- Right weighted column: independently scrolling selector and controls.

Keep graph heights stable and prevent pointer capture from scrolling the parent while a graph handle is active.

- [ ] **Step 6: Preserve the shared editor shell**

Keep offline status, undo, redo, reset confirmation, global effect enable, and predictive back in `EffectEditorScreen`. Do not duplicate top-bar logic inside the multiband composable.

- [ ] **Step 7: Run presentation tests, all unit tests, and `assembleDebug`**

Expected: all original controls are reachable in portrait and landscape code paths.

---

### Task 10: Fix The Main-Screen Preview And Remove Dead Legacy Controls

**Files:**

- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt`
- Modify: `app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionsMaterialPolicyTest.kt`
- Create: `app/src/test/java/com/llsl/viper4android/ui/screens/main/MultibandPreviewPolicyTest.kt`

**Interfaces:**

- `MultibandCompressorSection` accepts `showCurvePreview`.
- Preview uses the rebuilt four-handle model and never shows threshold points.
- No-graph mode shows `PreviewEditAction`.
- The unreachable original inline multiband controls after the current early `return` are deleted.

- [ ] **Step 1: Add failing source-policy tests**

Assert:

- `MainScreen` passes `showCurvePreviews` to the multiband section.
- The multiband preview has an explicit no-graph edit action.
- No threshold list is converted into multiband frequency-graph handles.
- The old unreachable multiband slider block is absent.
- No new Material3 imports are introduced.

- [ ] **Step 2: Run the policy tests and confirm RED**

- [ ] **Step 3: Update main-screen wiring**

Add `showCurvePreview: Boolean = true` to `MultibandCompressorSection`. Match the Equalizer card pattern:

```kotlin
if (showCurvePreview) {
    VstResponseGraph(
        handles = model.handles.toGraphHandles(handleColors),
        curve = model.curve,
        bandCurves = model.bandCurves,
        bandCurveColors = bandColors,
        referenceCurves = listOf(model.unitySumCurve),
        interactive = false,
        onClick = onOpenEditor,
        onHandleDrag = { _, _, _ -> },
    )
} else {
    PreviewEditAction(onClick = onOpenEditor)
}
```

- [ ] **Step 4: Delete dead inline controls**

Remove the unreachable code after the preview-only `return` in `MultibandCompressorSection`. Keep icons and live audio features; remove only code that can no longer execute.

- [ ] **Step 5: Run policy tests, all unit tests, and `assembleDebug`**

Expected: preview-only main card works in both graph modes.

---

### Task 11: Compose UI Coverage And Accessibility

**Files:**

- Modify: `app/build.gradle.kts`
- Create: `app/src/androidTest/java/com/llsl/viper4android/ui/screens/editor/MultibandCompressorEditorTest.kt`
- Create: `app/src/androidTest/java/com/llsl/viper4android/ui/screens/main/MultibandCompressorPreviewTest.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstResponseGraph.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/MultibandCompressorEditor.kt`

**Interfaces:**

- Adds Compose UI test dependencies using the existing Compose BOM.
- Gives every graph handle, band selector item, switch, and disabled control stable semantics.

- [ ] **Step 1: Add Compose UI test dependencies**

```kotlin
defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

androidTestImplementation("androidx.test.ext:junit:1.2.1")
androidTestImplementation("androidx.test:runner:1.6.2")
androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
debugImplementation("androidx.compose.ui:ui-test-manifest")
```

- [ ] **Step 2: Write failing editor UI tests**

Use a pure fake action sink and deterministic `MultibandCompressorState`. Assert:

- Exactly four nodes expose `Crossover 1` through `Crossover 4` semantics.
- No top-graph node exposes Threshold semantics.
- Selecting Band 5 updates the transfer card and parameter title.
- Auto Gain disables the selected manual Gain control and Y-edit semantics.
- Auto Knee disables Ratio and Knee.
- Advanced expansion reveals all six advanced parameter groups.
- Compression bypass leaves the band selector and settings present.
- Every graph-adjustable parameter has an explicit exact-value control.

- [ ] **Step 3: Write failing preview UI tests**

Assert graph mode opens the editor from the preview and no-graph mode exposes the explicit edit action.

- [ ] **Step 4: Add accessibility semantics**

Each crossover node description includes boundary number, current frequency, controlled band Gain, and Auto lock state. Transfer handles include selected band, parameter, and friendly value. Use at least 48dp semantic hit targets.

- [ ] **Step 5: Run instrumented tests when a device is available**

Use the raw `connectedDebugAndroidTest` command. If no device is available, record that limitation and still run unit tests plus manual installed-APK checks in Task 12.

---

### Task 12: Full Verification And Device Review

**Files:**

- Verify all files changed in Tasks 1 through 11.

- [ ] **Step 1: Inspect the complete diff**

Run `git status --short`, `git diff --check`, and review the complete diff. Confirm no unrelated user changes were altered and no Material Icons were removed.

- [ ] **Step 2: Run all unit tests with raw logs**

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:testDebugUnitTest --stacktrace --no-daemon 2>&1"
```

Expected: all tests pass with zero failures and zero errors.

- [ ] **Step 3: Run the full debug build with raw logs**

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 4: Install and inspect on device**

Verify portrait and landscape layouts with these states:

- Default state with all Auto modes on.
- All Auto modes off.
- Band 5 selected.
- One band compressor bypassed.
- Global multiband effect bypassed.
- DSP service offline and reconnecting.
- Main-screen curve previews on and off.
- Ratio below limiter, at limiter, and above limiter.
- Minimum and maximum crossover/gain values.

- [ ] **Step 5: Verify audio linkage manually**

Confirm:

- Horizontal crossover drag changes the intended crossover immediately.
- Vertical drag changes Band 1-4 Gain only when Auto Gain is off.
- Band 5 Gain knob dispatches normally.
- Ratio and Knee affect audio only with Auto Knee off.
- Manual Attack and Release affect audio only with their Auto modes off.
- Advanced Auto parameters affect their intended modes.
- Undo/redo groups one two-axis drag as one operation.
- Reset restores every parameter but preserves global effect enable.
- Closing and reopening preserves all values.

- [ ] **Step 6: Report evidence**

Report exact test totals, build result, APK path and size, instrumented-test status, and any residual device-only risks. Do not claim completion without the command outputs from Steps 2 and 3.

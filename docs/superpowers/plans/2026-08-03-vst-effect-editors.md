# VST Effect Editors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move FIR equalizer, dynamic equalizer, and multiband compressor editing into one independent, live-updating VST-style Activity while leaving preview-only controls on the main screen.

**Architecture:** Extract the existing `MainViewModel.applyPref` and `applyBandPref` mutation path into a singleton `EffectStateStore`. `MainViewModel` and a new `EffectEditorViewModel` observe and mutate that same store. A single `EffectEditorActivity` routes by `EditorKind` and hosts shared graph/knob/fader components plus three effect-specific editors.

**Tech Stack:** Kotlin, Jetpack Compose, MiuiX KMP 0.9.x, Hilt, StateFlow, Android bound service, JUnit, Compose Canvas and pointer input.

## Global Constraints

- Use MiuiX public APIs and project-local wrappers; do not edit MiuiX source.
- Do not add Material3 UI components.
- Keep Material Icons and existing audio features.
- FIR frequencies remain fixed; graph handles move vertically only.
- Dynamic EQ graph handles edit frequency horizontally and gain vertically.
- Multiband crossover boundaries cannot cross and keep one-semitone minimum spacing.
- Main cards retain header switch and preview only; no hidden long-press operations.
- Graph drags dispatch live at most once per frame and persist the settled value on gesture end.
- Functional tests must drive the same action/reducer APIs used by Compose and assert state, raw DSP dispatch, persistence, and reconnect order together.
- Do not treat source-text policy checks as functional coverage. Use them only for narrow migration policies such as banning new Material3 imports.
- Every linkage test must assert that one user action produces one unambiguous backend command sequence; duplicate or conflicting dispatches are failures.
- Do not create commits unless the user explicitly requests another checkpoint.

---

### Task 1: Shared effect state store

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/effect/EffectStateStore.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/MainViewModel.kt`
- Test: `app/src/test/java/com/llsl/viper4android/effect/EffectStateStoreTest.kt`

**Interfaces:**
- Produces: `EffectStateStore.state: StateFlow<EffectState>`
- Produces: `attachService(service: ViperService?)`
- Produces: `updatePref(pref: EffectPref<T>, value: T, last: Boolean = true)`
- Produces: `updateBandPref(pref: ListPref<E>, band: Int, value: E, count: Int = 5, last: Boolean = true)`
- Produces: `replaceEqBands(bands: List<Double>)`
- Produces: `updateState(transform: (EffectState) -> EffectState)` and `flush()`
- Produces: `isServiceConnected: StateFlow<Boolean>`

- [x] **Step 1: Write store behavior tests**

Drive store actions through a fake editor-action façade backed by real `EffectPref` objects. Assert the complete linkage for each action: resulting `EffectState`, raw DSP command, persisted key/value, and command ordering. Include list padding, disabled-effect dispatch suppression, reconnect full-state behavior, concurrent updates, and duplicate-command rejection.

- [x] **Step 2: Run the store tests and confirm RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.llsl.viper4android.effect.EffectStateStoreTest --no-daemon
```

Expected: compilation failure because `EffectStateStore` does not exist.

- [x] **Step 3: Implement the store using the existing mutation logic**

Move the behavior currently in `MainViewModel.applyPref`, `applyBandPref`, `replaceAt`, `shouldDispatch`, and `persistPref`. The store owns its `MutableStateFlow`, repository writes, service reference, and service state provider.

```kotlin
@Singleton
class EffectStateStore @Inject constructor(
    private val repository: ViperRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(EffectState())
    val state: StateFlow<EffectState> = mutableState.asStateFlow()
    private var viperService: ViperService? = null

    fun attachService(service: ViperService?) {
        viperService = service
        service?.setStateProvider { mutableState.value }
        if (service != null) scope.launch { service.dispatchFullState(mutableState.value, force = true) }
    }
}
```

- [x] **Step 4: Delegate MainViewModel effect state and mutation calls**

Inject the store, expose `uiState = effectStateStore.state`, attach/detach the bound service, and keep thin compatibility wrappers so existing effect sections continue compiling while later tasks migrate call sites.

- [x] **Step 5: Run store and existing unit tests**

Run the targeted test, then `:app:testDebugUnitTest`. Expected: PASS with no behavioral changes on the main screen.

### Task 2: Coordinate mapping and undo model

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EditorModels.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/GraphMapping.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EditorHistory.kt`
- Test: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/GraphMappingTest.kt`
- Test: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/EditorHistoryTest.kt`

**Interfaces:**
- Produces: `enum class EditorKind { FIR_EQUALIZER, DYNAMIC_EQUALIZER, MULTIBAND_COMPRESSOR }`
- Produces: `frequencyToX`, `xToFrequency`, `dbToY`, `yToDb`
- Produces: `constrainCrossovers(values, changedIndex, minFrequency, maxFrequency)`
- Produces: `EditorHistory<T>.beginGesture`, `settleGesture`, `undo`, and `redo`

- [x] **Step 1: Write mapping and constraint tests**

Cover logarithmic round trips at 20 Hz, 1 kHz, and 20 kHz; dB round trips; non-finite rejection; FIR nearest-band selection; dynamic EQ clamping; and one-semitone crossover spacing.

- [x] **Step 2: Run tests and confirm RED**

Expected: unresolved mapping and history symbols.

- [x] **Step 3: Implement pure mapping functions**

```kotlin
fun frequencyToX(frequency: Double, min: Double, max: Double): Float =
    ((ln(frequency.coerceIn(min, max)) - ln(min)) / (ln(max) - ln(min))).toFloat()

fun xToFrequency(x: Float, min: Double, max: Double): Double =
    exp(ln(min) + x.coerceIn(0f, 1f) * (ln(max) - ln(min)))
```

Use `2.0.pow(1.0 / 12.0)` for crossover spacing and clamp the changed boundary between its neighbors.

- [x] **Step 4: Implement gesture-level undo history**

Store at most 50 settled entries. `beginGesture` captures one before-state, updates do not append history, and `settleGesture` appends one operation when the final value differs.

- [x] **Step 5: Run mapping/history tests**

Expected: PASS.

### Task 3: Independent Activity and shared editor shell

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/EffectEditorActivity.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorViewModel.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Test: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/EffectEditorRoutingTest.kt`

**Interfaces:**
- Consumes: `EffectStateStore`, `EditorKind`, `EditorHistory`
- Produces: `EffectEditorActivity.createIntent(context, kind)`
- Produces: `EffectEditorScreen(kind, state, actions)`

- [ ] **Step 1: Write routing and manifest policy tests**

Assert all three enum values round-trip through Intent strings, unknown routes finish safely, the Activity is non-exported, and predictive back is enabled.

- [ ] **Step 2: Run routing tests and confirm RED**

- [ ] **Step 3: Implement Activity and Hilt ViewModel**

Use `@AndroidEntryPoint`, `enableEdgeToEdge`, `Viper4AndroidTheme`, and a non-exported manifest entry. The ViewModel observes `EffectStateStore` and exposes typed editor actions.

- [ ] **Step 4: Build the responsive shared shell**

Portrait layout uses graph, band selector, then a three-column parameter deck. Wide layout uses graph left and parameter deck right. Add icon buttons for back, undo, redo, reset, and bypass; use `ViperDialog` for reset confirmation.

- [ ] **Step 5: Run routing tests and compileDebugKotlin**

Expected: Activity starts for every route and compiles without Material3 imports.

### Task 4: Shared VST controls and graph gestures

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstResponseGraph.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstKnob.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstFaderBank.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstBandSelector.kt`
- Test: `app/src/test/java/com/llsl/viper4android/ui/components/viper/VstGestureReducerTest.kt`

**Interfaces:**
- Produces: `GraphHandle(id, x, y, color, label, dragAxis)`
- Produces: `VstResponseGraph(handles, curve, onGestureStart, onHandleDrag, onGestureEnd)`
- Produces: `VstKnob(value, range, steps, valueLabel, onValueChange, onValueChangeFinished)`
- Produces: `VstFaderBank(bands, selectedBand, onBandSelected, onBandChanged, onBandChangeFinished)`

- [ ] **Step 1: Write gesture reducer tests**

Test 48dp hit targets, nearest-handle selection, axis locking, frame coalescing, cancellation settling, and exact final values.

- [ ] **Step 2: Run tests and confirm RED**

- [ ] **Step 3: Implement Canvas graph and gesture reducer**

Draw a logarithmic frequency grid, dB grid, response path, selection outline, and handles. Keep gesture math pure and call UI callbacks from one frame-coalesced reducer.

- [ ] **Step 4: Implement knob, fader, and band selector**

Knobs use vertical drag, visible values, and clickable exact input. FIR faders are horizontally scrollable and stable-width. Add/delete are explicit icon actions with content descriptions.

- [ ] **Step 5: Run component tests and compile**

Expected: PASS; no new Material3 UI imports.

### Task 5: FIR equalizer editor and preview-only card

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/FirEqualizerEditor.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/EqGraphView.kt`
- Test: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/FirEqualizerEditorTest.kt`

**Interfaces:**
- Consumes: `EffectState.eq`, `EffectStateStore.replaceEqBands`, `VstResponseGraph`, `VstFaderBank`
- Produces: fixed-X, vertical-only FIR editing for 10/15/25/31 bands

- [ ] **Step 1: Write FIR reducer tests**

Assert horizontal motion selects the nearest fixed band but never changes its frequency; vertical motion clamps gain; band-count changes restore per-count arrays.

- [ ] **Step 2: Run tests and confirm RED**

- [ ] **Step 3: Implement FIR editor**

Add band-count selection, preset selection, read/write fader bank, graph dragging, reset, and exact value input. All paths call one `setBandGain(index, value)` reducer.

- [ ] **Step 4: Convert the main FIR card**

Keep header switch. Remove inline preset/count/sliders and the old dialog launch. Show `EqCurveGraph` only; tapping it opens `EffectEditorActivity` with `FIR_EQUALIZER`. When graph previews are disabled, show one compact edit icon action.

- [ ] **Step 5: Run FIR tests and main-screen policy tests**

Expected: PASS.

### Task 6: Dynamic EQ editor and preview-only card

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/DynamicEqualizerEditor.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/DynamicEqGraph.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt`
- Test: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/DynamicEqualizerEditorTest.kt`

**Interfaces:**
- Consumes: `EffectState.dynamicEq`, band-pref store updates, shared graph and knobs
- Produces: two-axis frequency/gain graph editing with stable band identity

- [ ] **Step 1: Write dynamic EQ reducer tests**

Cover frequency/gain clamping, selected-band identity after frequency sorting, add/delete bounds, and all scalar parameter updates.

- [ ] **Step 2: Run tests and confirm RED**

- [ ] **Step 3: Implement dynamic response preview and editor**

Render an approximate filter response from type/frequency/Q/gain. Graph handles edit frequency and gain. The dense parameter deck edits filter type, Q, threshold, attack, and release. Add/delete remain explicit.

- [ ] **Step 4: Convert the main dynamic EQ card**

Remove tabs and sliders; show only `DynamicEqGraph` and open `DYNAMIC_EQUALIZER` on tap.

- [ ] **Step 5: Run dynamic EQ and policy tests**

Expected: PASS.

### Task 7: Multiband compressor editor and preview-only card

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/MultibandCompressorEditor.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/MultibandCompressorGraph.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt`
- Test: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/MultibandCompressorEditorTest.kt`

**Interfaces:**
- Consumes: `EffectState.multibandCompressor`, crossover constraint mapping, shared graph and knobs
- Produces: four draggable crossovers, threshold handles, five-band parameter deck

- [ ] **Step 1: Write compressor reducer tests**

Cover crossover ordering/minimum spacing, threshold clamping, band selection, advanced parameters, and one-operation drag history.

- [ ] **Step 2: Run tests and confirm RED**

- [ ] **Step 3: Implement compressor graph and editor**

Draw five colored regions and four crossover lines. Horizontal crossover drags update `crossoverFreqs`; vertical band-handle drags update threshold. The selected deck exposes threshold, ratio, knee, gain, attack, release, and an advanced panel with all existing parameters.

- [ ] **Step 4: Convert the main compressor card**

Remove tabs and inline controls; show only the compressor preview and launch `MULTIBAND_COMPRESSOR` on tap.

- [ ] **Step 5: Run compressor and policy tests**

Expected: PASS.

### Task 8: Integration, offline behavior, and visual verification

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorScreen.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/effect/EffectStateStore.kt`
- Modify: `app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionsMaterialPolicyTest.kt`

**Interfaces:**
- Consumes all previous tasks.
- Produces reconnect re-dispatch, offline indicator, final preview synchronization, and verified APK.

- [ ] **Step 1: Add offline and reconnect tests**

Edit while service is null, assert persistence succeeds, attach a fake service, and assert one full-state dispatch occurs before subsequent incremental dispatches.

- [ ] **Step 2: Run tests and confirm RED**

- [ ] **Step 3: Implement offline state and flush points**

Display a compact DSP-offline status, flush on gesture end, Activity stop, and back, and retain editing while disconnected.

- [ ] **Step 4: Run complete verification**

Run remotely:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Install and visually inspect**

Install `app-debug.apk`. Capture portrait and landscape screenshots for all three editors. Verify dragging, exact input, add/delete, reset, undo/redo, predictive back, main-preview synchronization, and no clipped controls.

- [ ] **Step 6: Review checkpoint**

Inspect `git status`, `git diff`, and test output. Leave changes uncommitted unless the user explicitly requests a checkpoint commit.

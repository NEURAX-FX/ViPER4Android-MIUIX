# LSP-Style Spectrum Overlay Implementation Plan

> **For Codex:** REQUIRED SUB-SKILL: Use `test-driven-development` while implementing each task, then use `verification-before-completion` before reporting success.

**Goal:** Replace the multiband editor's separate flat spectrum graph with a time-smoothed, peak-hold analyzer rendered behind the existing interactive crossover response.

**Architecture:** Keep telemetry interpretation and ballistics in pure Kotlin. A small Compose bridge advances that state on display frames and emits normalized graph points. `VstResponseGraph` accepts the resulting optional analyzer layer and draws it below its existing grid, cached response curves, and handles. The driver protocol and DSP stay unchanged.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, MiuiX theme, JUnit 4, Compose UI instrumentation tests, Gradle Android plugin.

**Source spec:** `docs/superpowers/specs/2026-08-07-spectrum-visualization-design.md`

**Repository constraint:** Do not edit MiuiX or ViPERFX_RE sources. Do not add Material3 UI. Commit commands are intentionally omitted because this workspace requires explicit user authorization before committing.

---

## Task 1: Build The Time-Based Spectrum Presentation Model

**Files:**

- Modify: `app/src/main/java/com/llsl/viper4android/dsp/SpectrumPresentation.kt`
- Modify: `app/src/test/java/com/llsl/viper4android/dsp/SpectrumPresentationTest.kt`

### Step 1: Replace coefficient-oriented tests with behavior tests

Add a local telemetry factory to `SpectrumPresentationTest` and cover these contracts:

```kotlin
private fun telemetry(
    sequence: Int,
    sampleRate: Int = 48_000,
    spectrumDb: List<Float> = List(DriverTelemetry.SPECTRUM_COUNT) { -24f },
) = DriverTelemetry(
    sequence = sequence,
    sampleRate = sampleRate,
    fftSize = 2_048,
    validMask = DriverTelemetry.SPECTRUM_VALID,
    overrunCount = 0,
    spectrumDb = spectrumDb,
    meterDb = List(DriverTelemetry.METER_COUNT) { 0f },
)
```

Tests must verify:

- the 64 band-center frequencies equal the geometric centers of the driver's logarithmic bands at 48 kHz;
- low sample rates use `min(20_000, sampleRate / 2)` before graph-axis clamping;
- one 15 ms attack time constant advances by `1 - exp(-1)` of the remaining distance;
- one 300 ms release time constant advances by `1 - exp(-1)` of the remaining distance;
- a repeated sequence does not reset the 150 ms stale timer;
- stale data releases to `-96 dBFS` rather than freezing;
- peaks hold for 500 ms, then fall at 20 dB/s without dropping below the envelope;
- a sample-rate change resets target, envelope, peaks, and hold times before remapping;
- wrong-sized input targets the floor and non-finite bins sanitize to the floor;
- monotone fitting remains bounded by adjacent values and output points remain normalized.

Use explicit nanosecond timestamps in every timing test. Avoid sleeps and coroutines in this unit-test layer.

### Step 2: Run the focused tests and confirm the expected failure

After syncing the two changed files to the remote project, run:

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:testDebugUnitTest --tests 'com.llsl.viper4android.dsp.SpectrumPresentationTest' --no-daemon"
```

Expected: failures because the current implementation uses fixed attack/release coefficients, no peak state, no stale timeout, and index-based x coordinates.

### Step 3: Implement an immutable ballistic state transition

In `SpectrumPresentation.kt`, retain `fitSpectrumDb()` and replace `smoothSpectrum()` / `interpolateSpectrum()` with a state model shaped around these APIs:

```kotlin
internal const val SPECTRUM_FLOOR_DB = -96f
internal const val SPECTRUM_DISPLAY_MIN_DB = -72.0
internal const val SPECTRUM_DISPLAY_MAX_DB = 24.0

internal class SpectrumBallisticsState(
    val sampleRate: Int = 0,
    val sequence: Int? = null,
    val targetDb: FloatArray = FloatArray(DriverTelemetry.SPECTRUM_COUNT) { SPECTRUM_FLOOR_DB },
    val envelopeDb: FloatArray = FloatArray(DriverTelemetry.SPECTRUM_COUNT) { SPECTRUM_FLOOR_DB },
    val peakDb: FloatArray = FloatArray(DriverTelemetry.SPECTRUM_COUNT) { SPECTRUM_FLOOR_DB },
    val peakHoldUntilNanos: LongArray = LongArray(DriverTelemetry.SPECTRUM_COUNT),
    val lastInputNanos: Long? = null,
    val lastFrameNanos: Long? = null,
    val hasInput: Boolean = false,
)

internal fun advanceSpectrumBallistics(
    previous: SpectrumBallisticsState,
    telemetry: DriverTelemetry?,
    frameTimeNanos: Long,
): SpectrumBallisticsState
```

Implementation rules:

- accept a new target only when `hasSpectrum`, sample rate, size, and sequence are valid;
- update `lastInputNanos` only for a new sequence;
- preserve the last target during the 150 ms grace period, then target the floor;
- use `alpha = 1 - exp(-dt / tau)` with 15 ms attack and 300 ms release;
- apply the 500 ms hold and 20 dB/s peak decay per band;
- never mutate arrays owned by the previous state;
- return the unchanged state when no analyzer has ever been received, avoiding needless Compose invalidation;
- snap envelope values within 0.05 dB of the floor to the exact floor so a settled state stops invalidating Compose;
- reset all arrays when a valid frame changes sample rate.

### Step 4: Implement driver-faithful frequency mapping

Add pure helpers equivalent to:

```kotlin
internal fun spectrumBandCenterFrequency(
    index: Int,
    count: Int,
    sampleRate: Int,
): Double

internal fun spectrumCurvePoints(
    valuesDb: List<Float>,
    sampleRate: Int,
    minDb: Double = SPECTRUM_DISPLAY_MIN_DB,
    maxDb: Double = SPECTRUM_DISPLAY_MAX_DB,
): List<Offset>
```

Use geometric band centers, `graphFrequencyToX()`, and `graphDbToY()`. Keep bounded monotone Hermite fitting, but interpolate x and y from the mapped band-center points rather than assuming the first and last samples are exactly `0f` and `1f`.

Provide overloads that consume `FloatArray` for the frame loop so it does not box 64 values on every display frame.

### Step 5: Re-run the focused tests

Run the command from Step 2.

Expected: all `SpectrumPresentationTest` cases pass.

---

## Task 2: Put The Multiband Response On The Shared LSP Axis

**Files:**

- Modify: `app/src/main/java/com/llsl/viper4android/dsp/EffectGraphModel.kt`
- Modify: `app/src/test/java/com/llsl/viper4android/dsp/EffectGraphModelTest.kt`

### Step 1: Add a failing axis contract test

Extend `multibandModelExposesFourCrossoverGainHandlesAndSharedCurves()` with:

```kotlin
assertEquals(-72.0, model.minDb, 0.0)
assertEquals(24.0, model.maxDb, 0.0)
```

Keep the existing handle round-trip and crossover curve assertions. They ensure changing the lower bound does not change stored gain values.

### Step 2: Run the model test and confirm it fails at `-48 dB`

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:testDebugUnitTest --tests 'com.llsl.viper4android.dsp.EffectGraphModelTest' --no-daemon"
```

### Step 3: Change only the multiband graph floor

In `EffectGraphModel.kt`, change `MBC_MIN_DB` from `-48.0` to `-72.0`. Leave other effect graphs and the compressor transfer graph unchanged.

This regenerates band curves, unity response, and crossover-handle y positions on one `-72..+24 dB` scale shared with the analyzer.

### Step 4: Re-run the model test

Expected: all graph-model tests pass, including gain round trips and low-sample-rate bounds.

---

## Task 3: Add The Animated Analyzer Layer To The Shared Graph

**Files:**

- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstSpectrumGraph.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstResponseGraph.kt`

### Step 1: Replace the standalone graph with a Compose presentation bridge

Remove `Animatable`, fixed 80 ms tweening, and the nested `VstResponseGraph` call from `VstSpectrumGraph.kt`. Define the optional renderer payload and a state producer:

```kotlin
@Immutable
data class SpectrumGraphLayer(
    val envelope: List<Offset>,
    val peaks: List<Offset>,
)

@Composable
fun rememberSpectrumGraphLayer(
    telemetry: DriverTelemetry?,
): SpectrumGraphLayer?
```

Use `rememberUpdatedState(telemetry)` and one `LaunchedEffect(Unit)` display-frame loop. On each `withFrameNanos` callback:

1. call `advanceSpectrumBallistics()` with the latest telemetry;
2. store the returned state only when it changed;
3. map envelope and peak arrays through `spectrumCurvePoints()`;
4. return `null` before the first valid spectrum and once stale envelope and peaks are both at or below the visible `-72 dB` graph floor.

Keep the loop scoped to composition so leaving the editor cancels it automatically.

### Step 2: Compile the bridge before changing the renderer

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:compileDebugKotlin --no-daemon"
```

Expected: the new producer compiles; it is not yet rendered.

### Step 3: Extend `VstResponseGraph` with an optional layer

Add a defaulted parameter without changing existing callers:

```kotlin
spectrumLayer: SpectrumGraphLayer? = null,
```

Resolve analyzer colors from `MiuixTheme.colorScheme.primary`. Add a small private draw helper that:

- scales normalized offsets to canvas pixels;
- closes the envelope path to the graph floor at its own first and last x positions;
- fills it with `Brush.verticalGradient(primary 45% -> transparent)`;
- draws a subtle solid envelope stroke;
- draws the peak path with `PathEffect.dashPathEffect()`;
- skips empty, singleton, and non-finite paths safely.

Draw this helper after the surface and band regions but before vertical/horizontal grid lines. Keep `ResponseRenderNode` after grid labels so static reference and crossover curves remain cached and visually foregrounded. Keep handles last.

### Step 4: Run all JVM tests and compile the app

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --no-daemon"
```

Expected: existing `VstResponseGraph` callers compile unchanged and all JVM tests pass.

---

## Task 4: Merge Spectrum And Crossover UI In The Multiband Editor

**Files:**

- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/MultibandCompressorEditor.kt`
- Modify: `app/src/androidTest/java/com/llsl/viper4android/ui/screens/editor/MultibandCompressorEditorTest.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

### Step 1: Add a failing combined-graph instrumentation test

Create a valid 64-band `DriverTelemetry` fixture and render the editor with it. Assert:

- one node tagged `multiband-frequency-graph` exists;
- crossover handles `crossover-0` through `crossover-3` still exist;
- the old standalone `editor_graph_live_spectrum_title` text does not exist;
- the combined graph content description exists.

Keep the existing band-five and advanced-control tests unchanged.

### Step 2: Run the instrumentation test when a test device is available

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:connectedDebugAndroidTest --no-daemon"
```

Expected before implementation: the combined graph tag/structure assertion fails. If the remote Android environment cannot expose an instrumentation target, record that limitation and continue with unit/build plus installed-device verification in Task 5.

### Step 3: Integrate the analyzer unconditionally at composition level

In `MultibandCompressorEditor.kt`:

- call `rememberSpectrumGraphLayer(telemetry)` before `VstGraphWorkspace`;
- delete the conditional standalone spectrum title and `VstSpectrumGraph` block;
- pass `spectrumLayer` into the existing crossover `VstResponseGraph`;
- tag that graph with `Modifier.testTag("multiband-frequency-graph")`;
- derive its dB grid from `presentation.graph.minDb` / `maxDb` with a 24 dB step;
- convert dragged y values with the same model min/max instead of hard-coded `-48..24`;
- leave the input/output transfer graph untouched.

Calling the producer even when the newest telemetry value is `null` is required so a previously visible spectrum can release to the floor rather than disappear abruptly.

### Step 4: Update visible and accessibility labels

Update the existing crossover title and multiband graph description in all three locale files to describe the combined spectrum/crossover graph. Do not add instructional copy and do not remove the now-unused live-spectrum strings in this focused change.

Suggested source strings:

```xml
<string name="editor_graph_multiband">Live spectrum and multiband compressor response graph</string>
<string name="editor_graph_multiband_crossover_title">Spectrum and crossover response</string>
```

Use equivalent concise Chinese and Russian translations.

### Step 5: Re-run unit tests, compile, and instrumentation where available

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --no-daemon"
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:connectedDebugAndroidTest --no-daemon"
```

Expected: JVM suite passes; instrumentation confirms one combined frequency graph and preserved handle semantics.

---

## Task 5: Build, Install, And Verify Real Telemetry

**Files:**

- Verify only; fix only files already listed above if a check exposes a defect.

### Step 1: Review the final diff before remote sync

```bash
git diff --check
git diff -- app/src/main app/src/test app/src/androidTest docs/superpowers
git status --short
```

Confirm there are no unrelated edits, Material3 additions, MiuiX source changes, or ViPERFX_RE changes.

### Step 2: Sync the changed app files and run the complete build

Sync only the changed `app/` paths into `~/ViPER4Android`, preserving their repository-relative paths. Then run the documented command:

```bash
tar -cf - \
  app/src/main/java/com/llsl/viper4android/dsp/SpectrumPresentation.kt \
  app/src/main/java/com/llsl/viper4android/dsp/EffectGraphModel.kt \
  app/src/main/java/com/llsl/viper4android/ui/components/viper/VstSpectrumGraph.kt \
  app/src/main/java/com/llsl/viper4android/ui/components/viper/VstResponseGraph.kt \
  app/src/main/java/com/llsl/viper4android/ui/screens/editor/MultibandCompressorEditor.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-zh-rCN/strings.xml \
  app/src/main/res/values-ru/strings.xml \
  app/src/test/java/com/llsl/viper4android/dsp/SpectrumPresentationTest.kt \
  app/src/test/java/com/llsl/viper4android/dsp/EffectGraphModelTest.kt \
  app/src/androidTest/java/com/llsl/viper4android/ui/screens/editor/MultibandCompressorEditorTest.kt \
  | ssh -p 8022 10645@localhost "cd ~/ViPER4Android && tar -xf -"
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"
```

Expected: `BUILD SUCCESSFUL` and a new `app-debug.apk`.

### Step 3: Install the APK on the Android device

On the remote Android shell:

```bash
ssh -p 8022 10645@localhost 'APK="$HOME/ViPER4Android/app/build/outputs/apk/debug/app-debug.apk"; su -c "cp \"$APK\" /data/local/tmp/v4a-spectrum-debug.apk && chmod 644 /data/local/tmp/v4a-spectrum-debug.apk && pm install -r /data/local/tmp/v4a-spectrum-debug.apk"'
```

Expected: package manager prints `Success`.

### Step 4: Verify live behavior in the multiband editor

With audio playing and multiband compression enabled, check:

1. One graph contains both the translucent moving analyzer and foreground crossover responses.
2. Energy distribution is visibly non-flat and follows playback without 20 Hz stepping.
3. Fast rises appear promptly; falls are slower; dashed peaks hold briefly and then decay.
4. Stopping playback clears the analyzer naturally after the 150 ms grace period instead of freezing.
5. Crossover handles remain legible, selectable, and draggable above the analyzer.
6. Switching bands and opening advanced controls still works.
7. Dedicated editor graphs remain visible when main-screen curve previews are disabled.
8. Invalid or unsupported telemetry leaves the crossover graph usable with no error UI.

### Step 5: Check narrow/wide and light/dark layouts

Capture device screenshots in portrait and landscape under light and dark themes. Verify:

- frequency and dB labels remain readable over the gradient;
- no text, handles, or graph titles overlap;
- the gradient is restrained and MiuiX-themed rather than a dominant single-color panel;
- the peak stroke, response curves, and selected handles are distinguishable;
- the compressor transfer graph below remains unchanged.

### Step 6: Run final evidence commands

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:testDebugUnitTest assembleDebug --no-daemon"
git diff --check
git status --short
```

Report exact test/build results, whether instrumentation ran, and any remaining visual/device limitation. Do not claim completion from compile success alone; real telemetry and stale decay must be observed on device.

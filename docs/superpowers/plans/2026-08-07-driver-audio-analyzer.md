# Driver Audio Analyzer Implementation Plan

> **For Codex:** Execute this plan task-by-task with test-driven development and verify each repository independently before device deployment.

**Goal:** Add a reusable post-DSP spectrum and effect-meter analyzer to the ViPER driver, transport fixed-version telemetry through `GET_PARAM`, and render a smooth live spectrum plus multiband gain reduction in the Android editor.

**Architecture:** `ViperContext` owns an `AudioAnalyzer`. The real-time callback only downmixes and writes into a lock-free SPSC ring plus atomic meter slots. `GET_PARAM` drains the ring, performs one cached PFFFT transform, and returns a fixed little-endian wire structure. The App parses that payload, polls the active service effect at 20 Hz, applies attack/release smoothing and monotone interpolation, and animates between snapshots in Compose.

**Tech Stack:** C++17, PFFFT, Android AudioEffect command protocol, Kotlin, coroutines/StateFlow, Jetpack Compose, JUnit 4.

---

## Task 1: Driver analyzer core

**Files:**
- Create: `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/AudioAnalyzer.h`
- Create: `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/AudioAnalyzer.cpp`
- Create: `/root/AndroidIDEProjects/ViPERFX_RE/tests/AudioAnalyzerTest.cpp`
- Modify: `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/CMakeLists.txt`
- Modify: `/root/AndroidIDEProjects/ViPERFX_RE/CMakeLists.txt`

1. Add a host test for silence, a 1 kHz sine peak, disabled capture, reset, and ring overrun.
2. Run the test target and confirm it fails because `AudioAnalyzer` does not exist.
3. Implement a preallocated 8192-sample SPSC ring, 2048-point Hann/PFFFT analysis, 64 log bands from 20 Hz to min(20 kHz, Nyquist), a -96 dB floor, and six atomic meter slots.
4. Run `cmake -S . -B build-host -DBUILD_ANALYZER_TESTS=ON && cmake --build build-host --target audio_analyzer_test && ctest --test-dir build-host --output-on-failure`.

## Task 2: Driver telemetry protocol and integration

**Files:**
- Create: `/root/AndroidIDEProjects/ViPERFX_RE/src/TelemetryProtocol.h`
- Modify: `/root/AndroidIDEProjects/ViPERFX_RE/src/ViperContext.h`
- Modify: `/root/AndroidIDEProjects/ViPERFX_RE/src/ViperContext.cpp`
- Modify: `/root/AndroidIDEProjects/ViPERFX_RE/CMakeLists.txt`

1. Add compile-time layout assertions for a versioned 320-byte wire payload containing header fields, 64 spectrum floats, and 8 meter floats.
2. Add `kParamGetTelemetry = 9`, reply-capacity validation, and snapshot serialization.
3. Configure/reset the analyzer with the effect configuration, push POST-DSP samples after `ViPER::Process`, and auto-arm capture for two seconds on telemetry reads.
4. Build the Android arm64 library and confirm no DSP path behavior changes while telemetry is not polled.

## Task 3: Compressor meter sources

**Files:**
- Modify: `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/FETCompressor.h`
- Modify: `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/effects/MultibandCompressor.h`
- Modify: `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/ViPER.h`
- Modify: `/root/AndroidIDEProjects/ViPERFX_RE/ViPERDSP/viper/ViPER.cpp`
- Modify: `/root/AndroidIDEProjects/ViPERFX_RE/src/ViperContext.cpp`

1. Add read-only current gain-reduction accessors without changing compressor state.
2. Expose five multiband values and one FET value through `ViPER`.
3. Publish those values to analyzer meter slots after each processed block.
4. Extend host tests to verify meter validity, clamping, and wire ordering.

## Task 4: App telemetry parser and effect transport

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/viper/DriverTelemetry.kt`
- Create: `app/src/test/java/com/llsl/viper4android/viper/DriverTelemetryTest.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/viper/ViperParams.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/viper/ViperEffect.kt`

1. Add failing parser tests for valid payloads, wrong size/version/counts, non-finite values, and valid-mask behavior.
2. Implement explicit little-endian parsing of the fixed payload.
3. Add `ViperEffect.getTelemetry()` and keep old-driver behavior as a normal `null` result.
4. Run the focused JUnit tests.

## Task 5: Service and editor polling

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/service/ViperService.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/effect/EffectStateStore.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorViewModel.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorScreen.kt`
- Modify relevant existing tests under `app/src/test/java/...`

1. Add tests for telemetry delegation and stale/duplicate sequence handling.
2. Track the active global or first session effect in the service and expose telemetry through the dispatch target.
3. Poll only while an editor is visible, at 50 ms intervals, off the main dispatcher.
4. Clear telemetry when disconnected, unsupported, or stale.

## Task 6: Spectrum fitting and animation

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/dsp/SpectrumPresentation.kt`
- Create: `app/src/test/java/com/llsl/viper4android/dsp/SpectrumPresentationTest.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstSpectrumGraph.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/MultibandCompressorEditor.kt`
- Modify: `app/src/main/res/values/strings.xml`

1. Add tests for attack/release smoothing, bounded monotone interpolation, and log-frequency point mapping.
2. Implement smoothing and monotone cubic fitting without peak overshoot.
3. Render a stable noninteractive spectrum graph and animate snapshot transitions at display frame rate.
4. Show five multiband GR values next to their corresponding band controls; do not conflate spectrum level with compressor transfer curves.

## Task 7: End-to-end verification and deployment

1. Run driver host tests and arm64 Android build.
2. Run App unit tests and `assembleDebug` using the documented remote build command.
3. Copy the identifiable driver library to the existing mount-test path, bind-mount it from PID 1's namespace, restart `vendor.audio-hal`, and verify target hash, mapped hash, version string, and stable PID.
4. Install the debug APK, launch the editor, verify telemetry sequence advances, spectrum is nonblank during playback, GR values return to zero without compression, and the old driver degrades to no graph rather than a crash.
5. Record any device-only limitations explicitly.

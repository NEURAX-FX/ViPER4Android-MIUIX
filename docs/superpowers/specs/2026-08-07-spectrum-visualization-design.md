# Spectrum Visualization & Telemetry Redesign (LSP-Style Dual Layer VST Graph)

## Overview
Redesign ViPER4Android's spectrum graph component (`VstSpectrumGraph.kt`) and telemetry presentation engine (`SpectrumPresentation.kt`) from a basic flat single line into a professional VST/LSP-style Real-Time Analyzer (RTA) with dual-layer overlay rendering and ballistic energy decay.

## Key Objectives
1. **Ballistic Telemetry Processing**: Upgrade `SpectrumPresentation.kt` with fast attack (~15ms) and exponential decay (~300ms) to eliminate sudden drop-offs and flatlines during audio playback.
2. **Log-Frequency Mapping**: Map 64 FFT driver telemetry bins to a logarithmic scale spanning 20 Hz to 20 kHz.
3. **Dual-Layer Canvas Graphics**: Render background live RTA spectrum with translucent gradient fill and peak-hold lines underneath crisp foreground DSP transfer function curves.
4. **Theme & Setting Integration**: Harmonize with MiuiX theme colors and respect the global `no-graph` setting toggle while preserving full spectrum view in dedicated editor screens.

---

## 1. Telemetry & DSP Processing Architecture (`SpectrumPresentation.kt`)

### 1.1 Data Feed & Normalization
* Bins: 64 floating-point magnitude values received from `DriverTelemetry`.
* Range: -96 dB (floor) to +12 dB (ceiling).
* Frequency distribution: Logarithmic interpolation mapping bin indices $[0 \dots 63]$ to $f(i) = 20 \times (20000 / 20)^{i / 63} \text{ Hz}$.

### 1.2 Ballistic Envelope Smoothing
For each band $i$ at frame time $t$ with raw value $v_i(t)$ and smoothed value $s_i(t-1)$:
$$\Delta = v_i(t) - s_i(t-1)$$
$$s_i(t) = \begin{cases} s_i(t-1) + \alpha_{\text{attack}} \cdot \Delta & \text{if } \Delta > 0 \quad (\text{Attack: } \alpha_{\text{attack}} \approx 0.85) \\ s_i(t-1) + \alpha_{\text{decay}} \cdot \Delta & \text{if } \Delta \le 0 \quad (\text{Decay: } \alpha_{\text{decay}} \approx 0.12) \end{cases}$$

### 1.3 Peak Hold Logic
* $p_i(t) = \max(p_i(t-1), s_i(t))$.
* Hold timer: 500 ms before peak value starts decaying linearly at 20 dB/sec.

---

## 2. Canvas Graphics & Layering (`VstSpectrumGraph.kt`)

### 2.1 Layer 1: Background Log Grid & Labels
* Frequency grid lines: 100 Hz, 1 kHz, 10 kHz.
* Gain grid lines: -24 dB, -12 dB, 0 dB, +12 dB.
* Labels rendered with muted secondary text color from MiuiX theme palette.

### 2.2 Layer 2: Live RTA Spectrum
* **Spline Interpolation**: Catmull-Rom or cubic Bezier curve interpolation through smoothed bin values to produce a smooth, natural wave shape instead of jagged step lines.
* **Gradient Area Fill**: Linear gradient brush from Primary Accent (opacity 0.50) at curve top down to 0.05 opacity at canvas bottom (-96 dB baseline).
* **Peak Hold Stroke**: Dotted accent line drawn through $p_i(t)$ points.

### 2.3 Layer 3: Foreground Filter Response
* High-contrast crisp stroke line (width 2.5dp) representing target DSP curve (Parametric EQ or Multiband Compressor response).
* Control points/handles rendered at key parametric frequencies.

---

## 3. Compatibility & Settings
* Theme responsiveness: Colors dynamically resolve from MiuiX `MiuixTheme` palette.
* Preference control: Honors the `no-graph` preference flag on main screens.

---

## 4. Verification Plan
1. **Unit Tests**: Add tests in `SpectrumPresentationTest.kt` verifying ballistic attack/decay rate calculation and logarithmic frequency bin distribution.
2. **Build Verification**: Run `./gradlew :app:testDebugUnitTest` and assemble debug build to confirm zero compile/type errors.

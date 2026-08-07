# LSP-Style Spectrum Overlay Design

## Goal

Turn the existing standalone live-spectrum line in the multiband compressor editor
into an LSP-style analyzer layer behind the existing crossover response. The graph
must remain truthful to driver telemetry, decay naturally when updates stop, and
keep the foreground response curves and controls readable.

This slice changes presentation only. It does not change the analyzer wire protocol,
driver FFT, audio processing, or the meaning of compressor meters.

## Verified Inputs

The current driver and app establish the following contract:

- `DriverTelemetry` carries 64 post-DSP spectrum values, the output sample rate,
  FFT size, sequence, validity flags, and effect meters.
- The driver already aggregates FFT bins into 64 logarithmic bands from 20 Hz to
  `min(20 kHz, Nyquist)` and clamps their magnitudes to `-96..0 dBFS`.
- The app polls at approximately 20 Hz and discards duplicate sequence values.
- `VstSpectrumGraph` currently applies fixed-coefficient attack/release smoothing,
  interpolates each snapshot over 80 ms, and renders the spectrum as a separate
  graph above the multiband crossover graph.
- `VstResponseGraph` already owns the MiuiX surface, log-frequency grid, foreground
  response curves, band regions, handles, labels, gestures, and accessibility.

The app must not reinterpret the 64 values as linear FFT bins. It reconstructs each
band center from the same logarithmic band formula used by the driver and maps that
frequency through the shared graph axis helpers.

## Presentation Model

### Frequency Mapping

For band `i` out of `N = 64`, use the driver's analyzed maximum:

```text
analyzerMax = min(20_000, sampleRate / 2)
low(i)      = 20 * (analyzerMax / 20)^(i / N)
high(i)     = 20 * (analyzerMax / 20)^((i + 1) / N)
center(i)   = sqrt(low(i) * high(i))
```

Map `center(i)` with the existing `graphFrequencyToX()` helper so the analyzer and
response curves share one log-frequency coordinate system. Clamp the final x value
only where the graph's existing Nyquist safety margin makes the last center slightly
exceed its response axis.

### Time-Based Ballistics

Use elapsed monotonic frame time rather than fixed per-poll coefficients. For a time
constant `tau` and elapsed time `dt`:

```text
alpha = 1 - exp(-dt / tau)
next  = previous + alpha * (target - previous)
```

Initial constants:

- attack time constant: 15 ms;
- release time constant: 300 ms;
- peak hold: 500 ms;
- peak decay after hold: 20 dB per second;
- stale-input grace period: 150 ms after the last new telemetry sequence.

When telemetry becomes stale, the live target changes to `-96 dBFS`; the normal
release and peak rules then return the graph to silence instead of leaving a frozen
shape. Invalid, non-finite, or incorrectly sized input also targets the floor. A new
valid sequence resumes from the current presented state without a jump.

Keep this state transition in pure Kotlin so timing, sanitization, peak hold, stale
decay, and sequence changes can be tested without Compose.

### Spatial Fitting

Retain the existing bounded monotone cubic Hermite fitting. It smooths the low-point
64-band mesh without creating peaks between adjacent telemetry bands. Do not use an
unbounded Catmull-Rom fit.

The presentation output contains two normalized curves:

- current spectrum envelope;
- per-band held/decaying peak envelope.

Use a shared `-72..+24 dB` display axis for the combined graph, matching the LSP
multiband graph. The ballistic state still retains the driver's full `-96..0 dBFS`
range; values below `-72 dBFS` clamp to the graph floor only while producing plot
points. This avoids assigning different meanings to the same horizontal grid while
leaving headroom for the existing positive crossover-band gain handles.

## Rendering

Extend `VstResponseGraph` with an optional analyzer-layer model. Draw in this order:

1. graph surface and existing band-region tint;
2. live spectrum area with a vertical MiuiX-primary gradient, approximately 45%
   opacity at the envelope and fading to transparent at the floor;
3. a subtle spectrum edge stroke;
4. a dashed peak-hold stroke;
5. existing frequency and dB grid plus labels, kept above the fill for readability;
6. existing cached reference, band, and main response curves;
7. existing graph handles.

The analyzer curves use normalized `Offset` points and are rendered in the Compose
canvas. Existing static response curves remain in `ResponseRenderNode`, so adding a
dynamic analyzer does not invalidate their cached display list each frame.

The spectrum fill closes to the visible `-72 dB` graph floor at the first and last
plotted x positions. Values beneath that display floor remain preserved in the
ballistic state but are visually clipped. The fill does not imply measured energy
outside the analyzer's frequency range.

## Editor Integration

In `MultibandCompressorEditor`, replace the standalone live-spectrum graph and the
separate crossover graph with one combined graph:

- live post-DSP spectrum and peak hold in the background;
- existing multiband crossover curves, unity reference, regions, and draggable
  crossover handles in the foreground;
- existing frequency grid shared by both layers;
- one `-72..+24 dB` y axis shared by analyzer and response layers.

The compressor input/output transfer graph remains separate because its axes are
input and output level, not frequency.

The main-screen `showCurvePreviews` preference continues to control only lightweight
main-screen response previews. Dedicated editor graphs remain available, as required
by the project's no-graph policy. This task does not add a main-screen live analyzer.

## Lifecycle And Failure Behavior

- Run the display-frame animation only while the combined graph is composed.
- A duplicate telemetry sequence does not reset stale timing or peak hold.
- A changed sample rate remaps x coordinates and resets the temporal state to avoid
  carrying bands across incompatible frequency ranges.
- Missing telemetry leaves the crossover editor fully usable and simply omits the
  analyzer layer.
- Old drivers that do not support telemetry continue returning `null` without an
  error UI.
- Keep the existing content description and handle semantics; the rapidly changing
  analyzer values are visual context and are not announced every frame.

## Verification

### Unit Tests

Add focused tests for:

- frequency-band centers at 48 kHz and a low sample rate;
- attack and release using elapsed time rather than poll count;
- peak hold, peak decay, and stale-input release to the floor;
- invalid data sanitization and sample-rate reset;
- bounded fitted curves and normalized x/y coordinates.

### Build And Device Checks

1. Run `:app:testDebugUnitTest`.
2. Run the documented remote `assembleDebug` build after syncing the changed app
   files.
3. Install the debug APK and inspect the multiband editor at narrow and wide widths.
4. During playback, verify that the analyzer is non-flat, the crossover curves and
   handles remain legible and interactive, and the envelope returns to the floor
   after playback stops.
5. Capture device screenshots for light and dark themes and check that labels,
   gradient, peak line, and handles do not overlap incoherently.

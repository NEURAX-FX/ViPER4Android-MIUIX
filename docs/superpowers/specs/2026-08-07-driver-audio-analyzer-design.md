# Driver Audio Analyzer Design

## Goal

Add a reusable audio-analysis component to the ViPER driver so the Android UI can
render a real-time spectrum and effect meters without doing audio capture in the
application process.

The component must be safe on the real-time audio path, independent of any one
effect, and usable by future visualizations beyond the multiband-compressor editor.

## Findings

The driver already has the required integration points:

- `ViperContext::Process` owns the PCM processing callback and is the correct place
  to add optional input/output taps.
- The effect command path already supports `GET_PARAM` replies with a caller-
  supplied size, so telemetry can be exposed without a new Binder command family.
- `pffft` is already present in the driver tree and can provide the real FFT.
- The application already has a `ViperEffect.getParameter` path that can poll a
  fixed-size binary payload.

The analyzer should not use `Visualizer`. That API observes an Android audio
session rather than this effect instance, may require permissions, and cannot
provide reliable pre/post-DSP or per-band compressor data.

## Architecture

Create a small `AudioAnalyzer` component under `ViPERDSP/viper/effects`, alongside
the existing DSP effect implementations. It is a reusable processing component,
not an Android effect UUID and not a separate repository or CMake library.
It has three responsibilities:

1. Accept optional mono samples from the audio callback.
2. Produce a spectrum snapshot on demand.
3. Aggregate named meter values supplied by effects into the same telemetry
   snapshot.

The first implementation uses a fixed-size single-producer/single-consumer ring:

```text
audio callback -> mono downmix -> fixed ring buffer
                                      |
                                      v
GET_PARAM query -> snapshot/FFT -> fixed telemetry reply
```

The audio callback only performs an enabled check, stereo downmix, and bounded
ring writes. It must not allocate, lock, wait, or run FFT code. The query path may
run one FFT when the cached sample window is newer than the last result.

This avoids one background thread per `ViperContext`, which is undesirable in
`audioserver` because contexts can be short-lived and numerous.

## Analyzer API

Use an API shaped like the following; exact naming should follow the existing C++
style in the driver:

```cpp
class AudioAnalyzer {
public:
    void Configure(uint32_t sample_rate, uint32_t channels);
    void Enable(bool enabled);
    void Push(const float* interleaved, uint32_t frames);
    void SetSpectrumTap(SpectrumTap tap);
    void SetMeter(uint32_t id, float value_db);
    bool ReadTelemetry(TelemetrySnapshot* out);
};
```

Implementation constraints:

- FFT size: 2048 samples initially.
- Window: Hann window, precomputed during configuration.
- Output: 64 logarithmically spaced bands, normalized to dB with a bounded floor.
- Input: stereo is downmixed to mono; other channel counts use an explicit
  documented policy rather than indexing assumptions.
- Storage: all buffers are allocated during `Configure` or construction.
- Disabled mode: `Push` returns after one cheap enabled check.
- Reset: configuration changes and effect reset invalidate the current snapshot;
  stale data must not be presented as live audio.

The analyzer should support two spectrum taps:

- `PRE_DSP`: samples before `viper_.Process`, useful for input visualization.
- `POST_DSP`: samples after processing, useful for showing the audible result.

The first UI should use `POST_DSP` by default. Both taps can share the same ring
implementation only when explicitly enabled, so the common case does not pay for
two copies of the samples.

## Effect telemetry

Spectrum data alone cannot explain compressor behavior. Effects should publish
their own measurements through the analyzer:

- Multiband compressor: one gain-reduction dB value per band.
- FET compressor: current gain-reduction dB value.
- Future effects: meters identified by stable numeric IDs.

The analyzer owns the snapshot and transport format, but it does not inspect
effect internals. Each effect remains responsible for defining when and how its
meter value is calculated.

Meter values should be instantaneous driver values. Temporal smoothing belongs in
the UI or a reusable presentation layer so different visualizations can choose
their own attack/release behavior.

## Telemetry protocol

Add a new read-only parameter identifier, for example `kParamGetTelemetry`.
The reply is a fixed little-endian binary structure with a version and valid-mask:

```text
uint32 version
uint32 sequence
uint32 sample_rate
uint32 fft_size
uint32 spectrum_band_count
uint32 meter_count
uint32 valid_mask
float  spectrum_db[64]
float  meter_db[MAX_METERS]
```

The actual C++ wire struct must use fixed-width fields and a compile-time size
assertion. Do not expose pointers, `size_t`, compiler-dependent `bool`, or STL
objects. The driver must reject replies whose requested capacity is smaller than
the required structure and report the required size through `reply_size`.

`sequence` changes only when a new telemetry snapshot is published. The client
can therefore discard duplicate polls and detect reset or missed snapshots.
Unknown telemetry versions must be rejected by the app rather than guessed.

Separate parameter IDs may be added later for lightweight meter-only reads, but
the first version should keep one bounded payload and one polling path.

## UI consumption

The Android layer polls telemetry at approximately 20-30 Hz. Rendering remains at
the display frame rate by interpolating the last two snapshots and applying a
small configurable EMA. The app should parse with an explicit `ByteBuffer` byte
order and validate:

- payload size,
- protocol version,
- band and meter counts,
- finite float values,
- sequence continuity.

The spectrum should be drawn as a log-frequency polyline or bars. Do not use a
high-degree curve fit: log-frequency interpolation plus temporal smoothing is
more stable and does not invent peaks between FFT bands. A compressor editor can
overlay the per-band gain-reduction values separately from the spectrum.

## Integration points

At context setup:

- configure the analyzer when sample rate/channel count are known;
- keep it disabled unless a client has requested telemetry;
- clear it on reset and release.

In processing:

- push the selected pre-DSP samples before `viper_.Process`;
- push post-DSP samples after processing when that tap is enabled;
- publish effect meters after the effect has updated its current state.

In `GET_PARAM` handling:

- recognize the telemetry parameter;
- obtain the current cached/updated snapshot;
- copy only the fixed wire struct into `reply_data`;
- never block waiting for a worker or audio callback.

## Failure and performance rules

- If no complete FFT window exists, return a valid snapshot with the spectrum
  validity bit cleared; do not return old data as current.
- If the ring overruns, drop the oldest samples and increment an overrun counter;
  never block the audio callback.
- Clamp non-finite and out-of-range values before transport.
- Keep FFT work bounded to one transform per new window/query interval.
- Telemetry is diagnostic/UI data and must never alter DSP output or effect state.

## Implementation sequence

1. Add `AudioAnalyzer` ring/window/FFT code with unit tests for silence, a single
   tone, channel downmixing, disabled mode, reset, and ring overrun.
2. Add the fixed telemetry wire format and `GET_PARAM` driver handling.
3. Integrate POST-DSP spectrum into `ViperContext` behind the enable flag.
4. Add multiband and FET compressor meter taps.
5. Add the Kotlin parser and polling state flow.
6. Add the spectrum/GR rendering and verify desktop-sized and narrow layouts.

The driver and app changes should land in small buildable slices. The first slice
should expose a silence-safe telemetry payload before any UI animation is added.

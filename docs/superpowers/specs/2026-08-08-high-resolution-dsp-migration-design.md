# High-Resolution DSP Migration Design

## Status

Approved design baseline. This document defines the migration from the current
fixed-format DSP path to a runtime-configurable stereo `Float32` graph supporting
PCM integer input/output up to 32-bit and sample rates up to 384 kHz.

The first implementation target is native PCM plus an internal `Float32` DSP path.
The system does not force all content to 384 kHz. It processes at the negotiated
runtime rate and rebuilds the graph when the rate changes. Dynamic changes use a
buffer-boundary graph swap with a short crossfade.

## Current Inventory

The driver currently invokes 24 audio effect modules in `ViPER.cpp`:

1. `Convolver`
2. `VHE`
3. `ViPERDDC`
4. `SpectrumExtend`
5. `IIRFilter` / FIR equalizer path
6. `DynamicEQ`
7. `ColorfulMusic`
8. `StereoImager`
9. `DiffSurround`
10. `PlaybackGain`
11. `MultibandCompressor`
12. `FETCompressor`
13. `DynamicSystem`
14. `TubeSimulator`
15. `PsychoacousticBass`
16. `ViPERBass`
17. `ViPERBassMono`
18. `ViPERClarity`
19. `Cure`
20. `AnalogX`
21. `Reverberation`
22. `SpeakerCorrection`
23. `LUFSTargeting`
24. software limiter, currently represented by two `SoftwareLimiter` instances

`AudioAnalyzer` is telemetry infrastructure, not an effect, and is excluded from
the count. `ViperContext`, PCM conversion, graph publication, and shared DSP
primitives are separate infrastructure work.

## Migration Classification

All 24 modules must be touched for lifecycle, format, validation, or real-time
safety. They are split by algorithmic scope:

### Algorithm-Level Rewrites: 18

`VHE`, `ViPERDDC`, `SpectrumExtend`, `IIRFilter`/FIR EQ, `DynamicEQ`,
`ColorfulMusic`, `StereoImager`, `PlaybackGain`, `DynamicSystem`, `TubeSimulator`,
`PsychoacousticBass`, `ViPERBass`, `ViPERBassMono`, `ViPERClarity`, `Cure`,
`AnalogX`, `Reverberation`, and `SoftwareLimiter`.

Reasons include fixed 44.1/48 kHz coefficient tables, fixed-rate time constants,
missing anti-aliasing, runtime allocation, unclear transfer characteristics, or
insufficiently testable legacy behavior. `TubeSimulator` is currently a per-sample
averaging operation rather than a physical tube model; that behavior must not be
silently presented as high-resolution tube saturation.

### Core-Preserving Refactors: 6

`Convolver`, `DiffSurround`, `MultibandCompressor`, `FETCompressor`,
`SpeakerCorrection`, and `LUFSTargeting` retain their useful conceptual/core
algorithms but require native-rate preparation, preallocation, validation, and
real-time-safe processing. `Convolver` retains partitioned convolution; the other
modules retain their core dynamics, delay, filter topology, or loudness model while
being adapted to the new graph contract.

This classification is a work estimate and ownership boundary, not permission to
leave a module untested. A core-preserving refactor can still replace unsafe
implementation details.

## Audio Contract

The public graph configuration is versioned and contains:

- `sample_rate`: integer in `8_000..384_000` Hz;
- `pcm_encoding`: supported signed integer PCM widths through 32-bit and
  `Float32` at the boundary;
- `channels`: exactly 2 for this migration;
- `max_block_frames`: validated against the driver limit;
- `graph_generation`: monotonically increasing publication identifier;
- capability flags describing accepted rate and format combinations.

The boundary converts supported PCM integer input to interleaved normalized
`Float32`, processes stereo audio, and converts back to the negotiated output
encoding with explicit clipping, rounding, and non-finite sanitization. DSP state,
coefficients, and audio buffers use `Float32` unless a specific design calculation
requires `double` precision.

No implicit upsampling to 384 kHz is performed. A 44.1 kHz stream remains 44.1 kHz;
a 192 kHz stream is processed at 192 kHz. Rate-dependent time constants, delay
lengths, lookahead, FFT sizes, and coefficient designs are derived from the active
rate rather than persisted as sample counts.

## Graph Lifecycle

### Control Path

`SET_CONFIG` validates the complete format and effect configuration, then builds a
new immutable-ready `DspGraph` off the audio thread. Construction includes all
coefficient design, FFT/convolution planning, effect preparation, buffer sizing,
parameter snapshot creation, and warm-up. A configuration is acknowledged only
after the graph is complete.

The graph stores the negotiated format, maximum block size, all effect instances,
preallocated work buffers, and an immutable parameter snapshot. `Process()` must
not allocate, lock, log, perform file I/O, throw, or design coefficients.

### Atomic Publication

The completed graph is published with an atomic pointer and generation. At each
audio callback, the audio thread reads the current pointer once and may exchange it
only at a buffer boundary. The old graph is retained until no callback can reference
it, then destroyed by the control thread. Temporary double graph memory is allowed
during a swap.

### Crossfade

For a same-rate replacement, old and new graphs may run in parallel for roughly
5 ms and combine with an equal-power crossfade. This prevents parameter/state
discontinuities while preserving old graph output during preparation.

When the sample rate changes, the old graph is never fed samples at the new rate.
Its filters and delays are mathematically invalid for that input. The new graph
starts at the buffer boundary and crossfades from dry input to new wet output for
roughly 5 ms. This avoids both invalid old-rate processing and a hard state-reset
click.

If graph construction fails, the active graph remains unchanged. The control plane
reports a specific rejection or build reason; it never publishes a partially
initialized graph.

## Shared DSP Foundation

Before broad effect migration, introduce reusable, unit-tested primitives:

- `DspModule` lifecycle and `DspGraph` ownership;
- PCM integer/Float32 conversion and clipping policy;
- stereo interleaved block view with validated frame count;
- biquad/SOS design and processing;
- complementary Linkwitz-Riley crossover;
- FIR design, FFT plan, overlap-save/overlap-add and partitioned convolution;
- anti-aliased oversampling/downsampling where nonlinear effects require it;
- sample-rate-derived parameter smoothing and envelope detection;
- delay line with maximum-delay preallocation;
- true-peak detector and BS.1770 loudness accumulation;
- parameter snapshot, generation, and graph-swap crossfade utilities;
- debug-only allocation and non-finite guards for verification builds.

LSP code may be used as an algorithm reference or through a compatible embeddable
library after license review. Plugin UI, port automation, and plugin thread
ownership must not be copied into the Android driver.

## Native Implementation Style

New DSP code follows a C-with-classes style:

- concrete `final` classes own effect state and preallocated resources;
- public lifecycle stays small and explicit: `Prepare`, `Reset`, parameter update,
  and `Process`;
- the graph composes concrete modules instead of storing a polymorphic hierarchy;
- processing code uses plain loops, POD parameter snapshots, explicit buffers, and
  predictable control flow;
- constructors do not perform expensive planning; `Prepare` performs all work that
  may allocate or design coefficients;
- `Process` is `noexcept` and performs no allocation, locking, logging, file I/O,
  coefficient design, or container resizing;
- exceptions and RTTI remain disabled; templates are limited to small compile-time
  kernels and format conversion rather than framework metaprogramming;
- RAII is used for ownership and cleanup, not to hide audio-thread work;
- the Android effect boundary remains a C-compatible API around the concrete C++
  graph.

Typical module shape:

```cpp
class StereoLimiter final {
public:
    bool Prepare(const DspConfig &config, DspArena &arena);
    void Reset() noexcept;
    void SetParams(const LimiterParams &params) noexcept;
    void Process(float *interleaved, size_t frames) noexcept;

private:
    LimiterParams params_{};
    // Prepared state and non-owning arena-backed buffers.
};
```

## Effect Migration Order

### Phase 0: Runtime Foundation

Implement format negotiation, PCM conversion, `DspGraph`, atomic publication,
buffer-boundary swap, crossfade, preallocation, generation telemetry, failure
reporting, and real-time allocation tests. No capability claim changes yet.

### Phase 1: Frequency-Critical Path

Migrate FIR/IIR EQ, `ViPERDDC`, `Convolver`, and `SoftwareLimiter`. Validate
frequency response, Nyquist behavior, impulse response, latency, and 384 kHz
resource/CPU budgets first.

### Phase 2: Dynamics And Loudness

Migrate `DynamicEQ`, `PlaybackGain`, `DynamicSystem`, `MultibandCompressor`,
`FETCompressor`, and `LUFSTargeting`. Validate envelope timing in seconds, detector
linking, true peak, gain staging, and no zipper noise.

### Phase 3: Tone And Nonlinear Effects

Migrate `SpectrumExtend`, `PsychoacousticBass`, `ViPERBass`, `ViPERBassMono`,
`ViPERClarity`, `TubeSimulator`, `AnalogX`, and `Cure`. Use explicit anti-aliasing
and oversampling policies for nonlinear processing.

### Phase 4: Spatial, Legacy, And Room Effects

Migrate `VHE`, `StereoImager`, `DiffSurround`, `ColorfulMusic`, `Reverberation`,
and `SpeakerCorrection`. `VHE` must replace its fixed 44.1/48 kHz assets with
rate-aware resources or a documented design/resampling path. `ViPERDDC` receives
the equivalent resource migration in Phase 1.

During migration, 44.1/48 kHz old/new comparisons may exist internally for golden
tests. They are not a permanent compatibility branch. The 384 kHz capability is
enabled only after every phase and the complete matrix pass.

## Parameter Compatibility

Existing app parameters remain versioned and are normalized once on the control
path into `ParameterSnapshot`:

- frequencies remain Hz;
- attack, release, delay, and lookahead remain time units, never sample counts;
- FIR/IR parameters retain source resources and design metadata, not stale runtime
  coefficients;
- legacy EQ/DDC/VHE values retain original values and migration version;
- exact conversion is preferred; an approximation must be deterministic and emit a
  warning visible to diagnostics.

The audio thread reads only the immutable snapshot associated with its graph. It
does not access mutable application preference state.

## Failure Handling

Reject configuration when the sample rate is outside `8 kHz..384 kHz`, channels are
not exactly 2, PCM encoding is unsupported, block size exceeds the driver limit, or
any effect fails preparation. Report the failing field/module and keep the last
known-good graph active.

If runtime protection detects non-finite audio or an internal invariant failure,
the active graph enters a controlled dry/silent fallback according to the failure
policy and increments a diagnostic counter. The audio thread does not throw or log.
The control plane exposes graph generation, switch state, failing module, disable
reason, and failure count.

## Verification And Acceptance

Every migrated module must demonstrate:

- bypass transparency within the defined floating-point tolerance;
- no NaN or Inf propagation;
- no audio-thread allocation, lock, logging, file I/O, or exception;
- sample-rate transition without an audible click under the crossfade budget;
- parameter changes without zipper artifacts;
- stable operation at every claimed rate from 8 kHz through 384 kHz;
- stereo channel isolation and deterministic reset behavior.

Required test layers:

1. primitive unit tests for coefficient design, conversions, timing, delay, FFT/FIR,
   true peak, and crossfade math;
2. per-effect golden tests using impulse, sweep, white/pink noise, silence, and
   parameter automation;
3. graph tests for rejected config, atomic publication, generation ordering,
   same-rate replacement, cross-rate dry-to-wet transition, and old-graph lifetime;
4. debug builds with allocation and non-finite instrumentation;
5. device tests at 44.1, 48, 96, 192, and 384 kHz where hardware exposes them.

Initial quantitative targets:

- 44.1/48 kHz old/new frequency-response error no greater than `0.25 dB`, with
  effect-specific transient and gain tolerances documented in each test;
- no invalid filter coefficients at or near Nyquist;
- 384 kHz stereo Float32 CPU and memory usage within the device budget, with no
  callback heap allocation;
- the approximately 5 ms equal-power transition peak no greater than the larger
  pre/post level by `1 dB`;
- unsupported format/rate errors are visible and truthful rather than silently
  enabling a partial effect chain.

## Scope Boundaries

This design covers the DSP driver and its app-facing configuration/diagnostics. It
does not redesign MiuiX source, add multichannel processing, force 384 kHz
resampling, or remove existing audio features. UI work is limited to exposing
negotiated format, capability, switch state, and failure reason clearly enough for
diagnosis.

# Multiband Compressor Editor Redesign

**Status:** Approved for implementation
**Date:** 2026-08-06
**Scope:** Multiband compressor main card, dedicated editor, graph interaction, parameter accessibility, dispatch, persistence, and reset behavior

## 1. Relationship To Existing Design

This document replaces the multiband-compressor-specific UI and interaction rules in `2026-08-03-vst-effect-editors-design.md`.

The shared editor architecture remains unchanged:

- Keep one independent `EffectEditorActivity`.
- Keep `EffectStateStore` as the single mutable owner of effect state.
- Keep live DSP dispatch and persistent storage behind the store.
- Keep the main-screen effect card preview-only.
- Keep undo, redo, reset, exact-value input, predictive back, and offline editing.
- Keep all new UI on MiuiX public APIs and project-local wrappers.

This redesign is required because the current editor exposes only a small subset of the original multiband compressor controls. The omitted Auto controls are enabled by default, causing several visible manual knobs to update stored values while the driver ignores those values.

## 2. Goals

- Restore explicit access to every multiband compressor parameter supported by the existing app and driver.
- Replace the current mixed frequency/threshold graph with two semantically correct graphs.
- Reduce the top frequency graph to exactly four crossover handles.
- Let each crossover handle edit crossover frequency horizontally and Makeup Gain vertically.
- Provide a separate input/output compressor transfer graph for Threshold, Ratio, and Knee.
- Make Auto mode dependencies visible and prevent users from manipulating inactive controls.
- Preserve the fixed five-band driver model and all existing parameter ranges.
- Keep graph drawing honest when the compressor behavior depends on audio history that the app cannot observe.
- Ensure every graph-editable value also has an explicit knob or numeric-input path.

## 3. Non-Goals

- Do not change the native DSP parameter IDs or payload format.
- Do not add, remove, merge, or reorder compressor bands.
- Do not edit the MiuiX library source.
- Do not introduce Material3 UI components.
- Do not claim to display live gain reduction without driver telemetry.
- Do not draw a live compressed frequency response from static settings.
- Do not add a spectrum analyzer or audio meter.
- Do not make important operations depend on long press.
- Do not silently discard Ratio values above the conventional limiter point.

## 4. Canonical DSP Model

The app configures a fixed five-band compressor with four crossover frequencies.

### 4.1 Crossover stage

Each crossover uses two identical second-order Butterworth low-pass or high-pass sections in series, with `Q = 0.70710678`. This produces the driver's fourth-order crossover slope.

- Band 1: low-pass at crossover 1.
- Bands 2 through 4: high-pass at the lower crossover, then low-pass at the upper crossover.
- Band 5: high-pass at crossover 4.

The app can calculate this static structural response exactly. It cannot calculate the instantaneous compressed response because the compressor uses audio-dependent state.

### 4.2 Compressor stage

Each band owns one stateful FET compressor with:

- Band compressor enable.
- Threshold.
- Ratio coefficient.
- Auto Knee and manual Knee.
- Knee Multi.
- Auto Gain and manual Makeup Gain.
- Auto Attack and manual Attack.
- Max Attack.
- Auto Release and manual Release.
- Max Release.
- Crest.
- Adapt.
- No Clip.

The driver behavior creates these UI dependencies:

- `Auto Knee = on`: manual Ratio and Knee are inactive; Knee Multi remains active.
- `Auto Gain = on`: manual Makeup Gain is inactive; No Clip remains active.
- `Auto Attack = on`: manual Attack is inactive; Max Attack remains active.
- `Auto Release = on`: manual Release is inactive; Max Release remains active.
- `Auto Knee = off`: Knee Multi is inactive.
- `Auto Gain = off`: No Clip is inactive.
- Crest remains active in both manual and automatic timing modes because it participates in the sidechain peak detector.
- `Auto Knee = off` and `Auto Gain = off`: Adapt has no effective output role.

The UI must reflect these dependencies instead of presenting ignored controls as active.

## 5. Parameter Contract

The redesign must use the existing application ranges and defaults. It must not invent a new storage format.

| Parameter | Stored range | Default | Unit or display |
|---|---:|---:|---|
| Band compressor enable | Boolean | `true` | On/Off |
| Crossover 1-4 | `30..16000` | `120, 500, 4000, 8000` | Hz |
| Threshold | `-48..0` | `-18` | dB |
| Ratio coefficient | `0..200` | `50` | Converted label, see below |
| Manual Makeup Gain | `0..24` | `0` | dB |
| Manual Knee | `0..12` | `0` | dB |
| Knee Multi | `0..100` | `0` | Percent-like coefficient |
| Manual Attack | `1..100` | `1` | ms |
| Max Attack | `1..100` | `44` | ms |
| Manual Release | `5..500` | `100` | ms |
| Max Release | `5..500` | `200` | ms |
| Crest | `5..300` | `100` | Driver time control |
| Adapt | `0..200` | `50` | Driver coefficient |
| Auto Knee | Boolean | `true` | On/Off |
| Auto Gain | Boolean | `true` | On/Off |
| Auto Attack | Boolean | `true` | On/Off |
| Auto Release | Boolean | `true` | On/Off |
| No Clip | Boolean | `true` | On/Off |

### 5.1 Makeup Gain correction

The approved concept described a signed `-12..+12 dB` graph axis. The implementation must instead preserve the existing `0..24 dB` state and preset contract.

- Graph vertical editing writes `0..24 dB` directly.
- `0 dB` is unity manual makeup.
- The redesign does not add negative manual gain.
- Existing presets must round-trip without conversion.

### 5.2 Ratio display

The stored Ratio value is a driver reduction coefficient, not a conventional `N:1` ratio.

For stored values below `100`, the steady-state hard-knee output slope is:

```text
outputSlope = 1 - storedRatio / 100
conventionalRatio = 1 / outputSlope
```

Required labels include:

| Stored value | Primary label |
|---:|---|
| `0` | `1:1` |
| `50` | `2:1` |
| `75` | `4:1` |
| `90` | `10:1` |
| `95` | `20:1` |
| `100` | `Limit` |
| `101..200` | `Over 1%..100%` |

Values above `100` produce a negative output slope and are a real existing driver capability. They must remain editable. The exact-value dialog may show both the friendly label and the raw coefficient.

Do not label the raw value as a percentage without explaining its behavior.

## 6. Overall Editor Layout

The editor contains three primary regions.

### 6.1 Portrait and narrow windows

The scroll order is:

1. Crossover response card.
2. Compressor transfer card.
3. Band selector and band-compressor enable row.
4. Primary parameter cards.
5. Expandable advanced parameter card.

Both graphs remain visible above the parameter deck. Parameter cards may scroll, but graph gestures must not unintentionally scroll the page while a handle is captured.

### 6.2 Landscape and wide windows

Use two columns:

- Left column: crossover graph above transfer graph.
- Right column: band selector, primary controls, and advanced controls.

The right parameter column scrolls independently when required. The graph column keeps a stable minimum width and does not collapse into unreadable charts.

### 6.3 Shared editor shell

Keep the existing editor top bar with:

- Back.
- Effect title.
- Global effect bypass or enable state.
- DSP offline indication.
- Undo.
- Redo.
- Reset.

Reset requires confirmation and resets the entire multiband compressor state, not only the currently visible knobs.

## 7. Top Crossover Response Card

### 7.1 Purpose

This card displays only the static crossover structure and manual per-band Makeup Gain preview. It must not be described as the live compressed response.

The card contains:

- Logarithmic frequency axis.
- Linear dB axis.
- Five color-coded band curves.
- One unity-sum reference curve.
- Five selectable band regions.
- Exactly four interactive crossover handles.

### 7.2 Four-handle model

The handles are identified as:

- `crossover-0`: Band 1 / Band 2 boundary.
- `crossover-1`: Band 2 / Band 3 boundary.
- `crossover-2`: Band 3 / Band 4 boundary.
- `crossover-3`: Band 4 / Band 5 boundary.

No threshold handles are rendered on this graph.

### 7.3 Horizontal drag

Horizontal movement edits the corresponding crossover frequency.

- X uses logarithmic frequency mapping.
- Minimum editable frequency is `30 Hz`.
- Maximum editable frequency is `min(16000 Hz, safeNyquist)`.
- `safeNyquist` must remain strictly below the current sample-rate Nyquist limit.
- If the sample rate is unavailable, use the existing sanitized graph fallback sample rate for preview and editing bounds.

Crossovers must remain ordered with at least one semitone of separation:

```text
spacingRatio = 2^(1/12)
f[i] >= f[i - 1] * spacingRatio
f[i] <= f[i + 1] / spacingRatio
```

Dragging one handle never reorders persistent crossover identities.

### 7.4 Vertical drag

Vertical movement edits manual Makeup Gain.

- Handle `crossover-i` controls the manual Makeup Gain of Band `i + 1`.
- The Y range is `0..24 dB`.
- Up increases gain; down decreases gain.
- Band 5 has no dedicated crossover handle, so its Makeup Gain remains editable through the Band 5 knob and exact-value input.
- Selecting the Band 5 region must make its Gain control immediately visible.

Vertical editing is locked when the controlled band has `Auto Gain = on`. The handle remains horizontally draggable and shows an `AUTO` state. The UI must not silently turn Auto Gain off when the user attempts a vertical drag.

### 7.5 Two-axis gesture capture

The handle supports free two-dimensional dragging, but updates are resolved independently:

- X delta maps only to crossover frequency.
- Y delta maps only to manual Makeup Gain.
- A movement slop is applied before editing begins.
- Once captured, the graph consumes the gesture until end or cancellation.
- Multi-touch does not modify a second handle during an active drag.
- One drag creates one undo operation containing both changed values.

### 7.6 Band curve rendering

The five structural curves use the exact fourth-order crossover response already modeled by `MultibandCrossoverResponse`.

- When manual Gain is active, shift that band's plotted curve by the manual Gain value.
- When Auto Gain is active, do not invent an automatic gain offset. Draw the unshifted structural curve and show an `Auto Gain` indicator.
- When a band compressor is bypassed, the audio band still exists. Do not hide its crossover region. Desaturate or dash its compressor-related overlay instead.
- The unity-sum line represents the crossover network before stateful compression and automatic gain.

### 7.7 Region selection

Tapping a band region selects that band without changing parameters.

Selection is synchronized across:

- Top graph region.
- Bottom band selector.
- Transfer graph.
- Parameter cards.

The selected band is distinguished by outline or shape as well as color.

## 8. Middle Compressor Transfer Card

### 8.1 Purpose

This card displays the selected band's input/output transfer behavior. It is separate from the frequency graph because Threshold, Ratio, and Knee operate on level, not frequency.

Axes:

- X: input level, `-60..0 dB`.
- Y: output level, `-60..24 dB` so manual Makeup Gain remains visible.
- A dashed `1:1` line provides the uncompressed reference.

The graph title and accessibility description include the selected band number and whether automatic modes prevent an exact static curve.

### 8.2 Manual static transfer

When `Auto Knee = off` and `Auto Gain = off`, draw the exact steady-state target curve represented by the driver, excluding attack/release history.

Define:

```text
T = threshold dB
G = manual makeup gain dB
K = knee width dB
R = stored ratio / 100
D = input dB - T
```

The gain-reduction term is:

```text
if D <= -K / 2: reduction = 0
if D >=  K / 2: reduction = R * D
otherwise:       reduction = R * (D + K / 2)^2 / (2 * K)
```

The output is:

```text
output dB = input dB + G - reduction
```

For `K = 0`, use the hard-knee form without division by zero.

Attack and Release affect the transition over time, not this steady-state target curve. The card must not imply temporal behavior is shown.

### 8.3 Graph handles

Use one primary breakpoint handle and two smaller auxiliary handles:

- Threshold handle: horizontal drag edits Threshold.
- Ratio handle: vertical drag at the high-input end edits Ratio.
- Knee handle: horizontal drag around the knee boundary edits Knee width.

Each handle has a minimum 48dp semantic hit target even if its visible marker is smaller.

All three values remain editable through knobs and exact-value input. Graph gestures are never the only access path.

### 8.4 Automatic-mode rendering

The driver does not expose the live adaptive state required to draw every automatic mode exactly.

Required behavior:

- `Auto Knee = on`: disable Ratio and manual Knee handles, draw a dashed dynamic-knee reference, and show `Auto Knee: live curve unavailable`.
- `Auto Gain = on`: omit unknown automatic vertical makeup from the curve and show `Auto Gain excluded from preview`.
- `Auto Attack` or `Auto Release`: keep the steady-state curve but show that timing is automatic and not represented by the graph.
- Never animate fake gain reduction from settings alone.

Threshold remains editable in automatic modes because it remains an active driver parameter.

## 9. Band Selector And Enable Semantics

Use a five-item segmented band selector:

- Band 1.
- Band 2.
- Band 3.
- Band 4.
- Band 5.

The adjacent switch must be labeled as compressor bypass semantics, for example `Compression enabled for Band 3`.

Turning it off bypasses the compressor stage for that band. It does not mute the frequency band and must not remove the band from the crossover graph.

Parameter values remain editable while a band compressor is bypassed so users can prepare settings before re-enabling it. The UI shows those settings as a bypassed preview rather than claiming they are currently audible.

## 10. Parameter Deck

### 10.1 Primary dynamics card

The selected band exposes these primary controls without expanding an advanced panel:

- Threshold.
- Auto Knee.
- Ratio.
- Knee.
- Auto Gain.
- Makeup Gain.
- Auto Attack.
- Attack.
- Auto Release.
- Release.

Each Auto switch is visually paired with the control it governs.

Inactive controls remain visible but disabled, with a short reason available through supporting text or semantics:

- Auto Knee disables Ratio and Knee.
- Auto Gain disables Makeup Gain and top-graph vertical gain editing.
- Auto Attack disables Attack.
- Auto Release disables Release.

### 10.2 Advanced card

The expandable advanced card contains:

- Knee Multi.
- Max Attack.
- Max Release.
- Crest.
- Adapt.
- No Clip.

Dependency rules:

- Knee Multi is enabled only while Auto Knee is on.
- Max Attack is enabled only while Auto Attack is on.
- Max Release is enabled only while Auto Release is on.
- No Clip is enabled only while Auto Gain is on.
- Crest remains enabled in every mode.
- Adapt is disabled when both Auto Knee and Auto Gain are off.

The card remembers only its expanded/collapsed UI state. This UI state is not part of DSP persistence.

### 10.3 Crossover exact controls

Provide four explicit crossover value controls below the top graph or inside a compact `Crossovers` card.

- Each value opens exact numeric input.
- Input uses Hz.
- Validation applies the same sample-rate and one-semitone neighbor constraints as graph dragging.
- An invalid value keeps the dialog open and explains the legal range for that specific crossover.

## 11. Main-Screen Card

The main-screen multiband compressor card remains a scanning surface.

- Keep effect icon, title, status summary, and global enable switch.
- Keep a read-only crossover preview when curve previews are enabled.
- Do not put band tabs or full compressor controls back on the main card.
- Tapping the preview opens the dedicated multiband editor.
- When main-screen graph previews are disabled, show an explicit compact edit action.
- The preview must not display threshold dots on a frequency-response axis.
- The preview may show crossover regions, current crossover frequencies, selected preset summary if one exists, and Auto/Manual status counts.

## 12. State, Dispatch, And Persistence

### 12.1 State normalization

Normalize persisted multiband lists at the `EffectStateStore` boundary, not only inside composables.

- Exactly five entries for every per-band list.
- Exactly four crossover entries.
- Missing entries use canonical defaults.
- Extra entries are discarded only after preserving the first required entries.
- Values are clamped to their canonical ranges.
- Non-finite numeric input is rejected before entering state.

Malformed legacy data must not crash graph selection, crossover constraints, reset, or dispatch.

### 12.2 Per-index updates

Every band or crossover edit must use a per-index update path that produces the matching live DSP command.

- Crossover drag dispatches the changed crossover index.
- Makeup Gain Y drag dispatches the controlled band index.
- Transfer-graph edits dispatch Threshold, Ratio, or Knee for the selected band.
- Auto switches dispatch immediately and update control availability in the same state transaction.

Do not use a list-only update path that persists values without live driver dispatch.

### 12.3 Gesture coalescing

- Coalesce pointer updates to at most once per rendered frame.
- Keep UI state responsive during drag.
- One complete gesture creates one undo item.
- A two-axis crossover drag stores frequency and gain in the same undo item.
- Flush persistence when the gesture ends or is cancelled.
- Flush pending values on Activity stop and explicit back.

### 12.4 Reset

Reset is one atomic multiband transaction and restores:

- Preserve the current global effect enable state.
- Five band-enable defaults.
- Four crossover defaults.
- Thresholds.
- Ratios.
- Gains.
- Knees.
- Knee Multi values.
- Attacks and Max Attacks.
- Releases and Max Releases.
- Crests.
- Adapts.
- All four Auto lists.
- No Clip values.

After reset:

- Update state once.
- Dispatch the complete multiband compressor state once.
- Persist the complete normalized state once.
- Add one undo entry.

Reset must not leave old advanced values behind.

## 13. Rendering Architecture

Reuse the project-local Android `RenderNode` path on the API 29 baseline.

### 13.1 Cached display lists

Cache stable graph content separately for each card:

- Grid lines and labels.
- Static crossover band paths.
- Unity-sum reference.
- Static transfer path for the selected manual-mode parameter set.

Cache identity includes:

- Graph size.
- Sample rate.
- Crossover list.
- Relevant Gain and Auto states.
- Selected band transfer parameters.
- Theme and graph colors.
- Axis ranges.

### 13.2 Dynamic overlays

Draw these outside the cached display list:

- Handles.
- Selection outlines.
- Drag highlights.
- Auto-mode badges.
- Bypass indicators.
- Pointer interaction feedback.

Do not introduce a custom Android `View`, raw Skiko/Skia embedding, or a second rendering stack.

## 14. Visual And MiuiX Rules

- Use `MiuixTheme` colors and project-local Viper wrappers.
- Keep existing Material Icons where appropriate.
- Do not add `androidx.compose.material3.*` UI components.
- Use cards and spacing consistent with the current editor shell.
- Keep values stable in width so dragging does not shift the layout.
- Use color consistently for each band across both graphs, selector, and parameter title.
- Distinguish selection and bypass by outline, opacity, or dash pattern in addition to color.
- Avoid hidden gestures. Every drag action has an explicit control equivalent.
- Respect MiuiX scaffold insets and overlay hosting.

## 15. Accessibility

Every crossover handle exposes:

- Boundary number.
- Current frequency.
- Controlled band Makeup Gain.
- Auto Gain lock state when applicable.

Every transfer handle exposes:

- Band number.
- Parameter name.
- Friendly value.
- Raw Ratio coefficient when Ratio is above the limiter point.

Additional requirements:

- Minimum 48dp touch targets.
- Exact numeric input for all graph-adjustable values.
- Keyboard or step adjustment where supported by shared controls.
- No meaning conveyed by color alone.
- Disabled controls explain which Auto mode owns the parameter.

## 16. Error And Offline Behavior

- Controls remain editable while the DSP service is offline.
- The editor shows a compact offline indicator without blocking interaction.
- On reconnect, dispatch the full normalized multiband state before incremental updates resume.
- If the sample rate changes, recompute graph bounds and response paths.
- If a persisted crossover exceeds the new safe Nyquist bound, clamp it through one normalized state transaction and maintain crossover ordering.
- Invalid exact input remains visible with a specific validation message.

## 17. Testing Requirements

### 17.1 Unit tests

- Five-band and four-crossover list normalization.
- Malformed short and long persisted lists.
- Log frequency mapping round trips.
- Gain Y mapping round trips over `0..24 dB`.
- One-semitone crossover spacing at every boundary.
- Safe Nyquist clamping after sample-rate changes.
- Exact fourth-order crossover response and unity sum.
- Manual hard-knee transfer curve.
- Manual soft-knee transfer curve.
- `K = 0` transfer behavior without division by zero.
- Ratio labels for `0`, `50`, `75`, `90`, `95`, `100`, `101`, and `200`.
- Auto mode enable/disable dependencies.
- Per-index live dispatch for crossover, gain, threshold, ratio, and knee.
- One two-axis drag producing one undo operation.
- Atomic complete reset and one full-state dispatch.
- Reconnect full-state dispatch.

### 17.2 UI tests

- The top graph contains exactly four crossover handles and no threshold handles.
- Every crossover handle supports horizontal frequency editing.
- Handles 1 through 4 support vertical Gain editing only when their controlled band's Auto Gain is off.
- Band 5 Gain remains explicitly editable without a fifth crossover handle.
- Region tap, band selector, transfer graph, and parameter deck keep one selected band.
- Auto Knee disables Ratio and Knee graph/manual controls.
- Auto Gain disables manual Gain and top-graph Y editing.
- Auto Attack and Auto Release disable their manual timing controls.
- All advanced original parameters remain reachable.
- Band compressor bypass does not hide the audio band.
- Main-screen no-graph mode retains explicit editor access.
- Portrait and landscape layouts do not clip graphs, values, or controls.
- No new Material3 UI imports appear in the redesigned surface.

### 17.3 Manual audio verification

- Crossover dragging changes the running DSP immediately.
- Manual Gain changes audio only when Auto Gain is off and the band compressor is enabled.
- Ratio and Knee changes affect audio only when Auto Knee is off.
- Manual Attack and Release changes affect audio only when their Auto modes are off.
- Advanced Auto controls audibly affect their corresponding modes.
- Reset changes the running DSP immediately and restores all defaults.
- Closing and reopening the editor preserves every primary and advanced value.

## 18. Acceptance Criteria

- The dedicated editor contains two separate graphs: crossover response and compressor input/output transfer.
- The top graph has exactly four draggable crossover handles.
- X drag edits crossover frequency; Y drag edits the associated Band 1-4 manual Makeup Gain.
- Band 5 Makeup Gain remains available through explicit controls.
- The transfer graph edits Threshold, Ratio, and Knee without placing level controls on a frequency-response axis.
- Automatic behavior is labeled honestly; the UI never presents an inactive knob as active.
- All original multiband compressor parameters remain explicitly reachable.
- The main card remains preview-only and retains editor access in no-graph mode.
- All edits dispatch live, persist, undo correctly, and survive service reconnect.
- Reset restores every multiband parameter atomically.
- Graphs do not claim to show live state that is unavailable without driver telemetry.
- Unit tests, UI tests, remote `assembleDebug`, device installation, and portrait/landscape visual checks pass.

# VST Effect Editors Design

**Status:** Approved for implementation
**Date:** 2026-08-03
**Scope:** FIR equalizer, dynamic equalizer, and multiband compressor editing

## Goal

Replace the long inline parameter lists on the main effects screen with professional, dense VST-style editors hosted by one independent Activity. The main screen remains a scanning surface: each affected effect card shows its enable switch and a read-only response preview; tapping the preview opens the editor.

Editing must update the running DSP in real time, persist reliably, survive Activity recreation, and remain visible in the main-screen preview without copying state between Activities.

## Non-Goals

- No new DSP parameters or changes to the native driver contract.
- No arbitrary horizontal movement for FIR bands whose frequencies are fixed by the DSP.
- No live spectrum analyzer until a real metering data source exists.
- No separate Activity per effect.
- No Material3 UI migration. New UI uses MiuiX public APIs and project-local components.
- No Cancel/Apply transaction model. Changes are live and remain applied when the editor closes.

## Architecture

### Shared `EffectStateStore`

Introduce one application-scoped `EffectStateStore` as the only mutable owner of `EffectState`.

Responsibilities:

- Expose `StateFlow<EffectState>`.
- Apply typed `EffectPref` and `BandPref` updates.
- Dispatch live parameter changes through `ViperService`.
- Persist settled values through `ViperRepository`.
- Re-dispatch the full current state after service or AIDL reconnect.
- Serialize updates so MainActivity and the editor Activity cannot overwrite each other.

`MainViewModel` keeps screen-specific concerns such as dialogs, imports, driver status, and device management, but delegates all effect mutations and effect state observation to the store. This keeps `ViperService.stateProvider` attached to one continuously current state source.

The store boundary is intentionally small:

- `state: StateFlow<EffectState>` for the current effect snapshot.
- `updatePref(effect, pref, value)` for scalar parameters.
- `updateBandPref(effect, bandId, pref, value)` for band parameters.
- `replaceEqBands(bands)` for FIR band arrays.
- `setEffectEnabled(effect, enabled)` for card/editor enable switches.
- `flush()` for Activity stop, back, and completed gestures.

The store owns serialization, validation, service dispatch, and persistence. Callers do not write repository keys or call `ViperService` directly.

### Independent editor Activity

Add one `EffectEditorActivity`, registered with predictive-back support and wrapped in the existing MiuiX theme.

The launch contract contains only an `EditorKind`:

- `FIR_EQUALIZER`
- `DYNAMIC_EQUALIZER`
- `MULTIBAND_COMPRESSOR`

The Activity does not receive an effect-state snapshot. Its `EffectEditorViewModel` observes `EffectStateStore`, so edits, service dispatch, persistence, and the main-screen preview share the same state immediately.

### Editor component boundaries

- `EffectEditorScreen`: route-level shell, top bar, connectivity state, undo/redo, reset, and bypass.
- `VstResponseGraph`: logarithmic frequency grid, response rendering, hit testing, selected handles, and drag gestures.
- `VstKnob`: rotary parameter control with vertical drag, step adjustment, and clickable exact-value input.
- `VstBandSelector`: explicit selected-band navigation and add/delete actions where supported.
- `VstFaderBank`: fixed-frequency vertical faders for FIR EQ.
- Effect-specific editors own parameter layout and coordinate mapping; they do not duplicate Activity or persistence logic.

## Shared editor shell

The selected visual direction is the dense desktop-VST layout adapted responsively:

- Portrait: response graph on top, selected-band strip below it, dense three-column parameter deck beneath.
- Landscape or wide window: response graph on the left and parameter deck on the right.
- Top bar: back, effect name, connectivity/bypass state, undo, redo, and reset.
- Values are always visible beside or below controls.
- Tapping a value opens exact numeric input using project-local MiuiX dialogs.
- Controls use stable dimensions; changing labels or values must not shift the graph or parameter grid.

The graph is an editor in this Activity, but remains read-only on the main screen.

## Main-screen cards

For FIR EQ, dynamic EQ, and multiband compressor:

- Keep the effect title, icon, and enable switch in the card header.
- Remove inline sliders, tabs, band controls, and add/delete controls from the card body.
- Show only the current read-only response preview in the body.
- Make the preview an explicit, accessible entry to `EffectEditorActivity`.
- The card body click launches the Activity with its `EditorKind`; the header switch remains an independent enable action and does not launch the editor.
- Dedicated editor access remains available when the global “show curve previews” preference hides main-screen graphs; in that mode the card shows one compact edit action instead of silently removing access.

## Interaction model

### Shared graph behavior

- X coordinates use logarithmic frequency mapping over the parameter’s supported range.
- Y coordinates map linearly to the relevant dB range.
- The selected handle has a larger hit target than its visible marker.
- Dragging is clamped continuously to legal parameter ranges.
- Live DSP dispatch is coalesced to at most once per rendered frame.
- One drag gesture creates one undo entry, not one entry per pointer event.
- The final value is persisted when the gesture ends or is cancelled.
- Exact value input remains available for all graph-adjustable values.

### FIR equalizer

- Frequencies remain fixed by the selected 10/15/25/31-band DSP mode.
- Graph handles move vertically only and edit gain.
- Horizontal pointer movement selects the nearest fixed-frequency band but does not change its frequency.
- A horizontally scrollable vertical-fader bank mirrors the graph values.
- Graph drag, fader drag, step controls, and exact input all update the same band value.
- Existing presets, band-count selection, reset, and curve preview remain supported.

### Dynamic equalizer

- Each band is shown as a selectable graph handle.
- Horizontal drag edits center frequency on a logarithmic scale.
- Vertical drag edits gain.
- The selected band exposes filter type, frequency, Q, gain, threshold, attack, and release in the parameter deck.
- Add and delete are explicit commands. Delete is unavailable when it would violate the engine’s minimum band count.
- Band ordering is derived from frequency for display; persistent band identity does not change during sorting.

### Multiband compressor

- Show five color-coded compressor regions and four crossover boundaries.
- Dragging a crossover boundary horizontally edits its frequency.
- Neighboring crossover boundaries cannot cross. A minimum one-semitone spacing is enforced.
- Selecting a region exposes threshold, ratio, knee, makeup gain, attack, and release.
- A selected band’s vertical graph handle edits threshold; makeup gain remains a knob/value control to avoid ambiguous two-value vertical dragging.
- Advanced parameters remain available in a compact expandable deck and are not removed.

## Undo, reset, and persistence

- Keep an editor-local undo/redo stack with at most 50 settled operations.
- Coalesce a complete pointer drag into one operation.
- Reset affects the current effect only and requires confirmation.
- Back navigation does not revert edits; it closes the editor after pending persistence completes.
- Preference writes are debounced during continuous control changes and flushed on gesture end, Activity stop, and explicit back.
- Process recreation restores the latest state from `EffectStateStore` and repository persistence, not from an Intent snapshot.

## Service and error handling

- If the driver service is connected, updates are dispatched immediately.
- If disconnected, controls remain editable and values remain persisted.
- The editor shows a compact “DSP offline” state instead of blocking the UI.
- On reconnect, the store dispatches the full current effect state before resuming incremental updates.
- Invalid numeric input keeps the dialog open and displays the legal range.
- Non-finite values are rejected before entering the store.
- FIR band arrays are normalized to the active band count.
- Dynamic EQ frequencies and multiband crossovers are clamped and sorted according to their constraints.

## Accessibility and input

- Every graph handle has a semantic label containing band, frequency, and value.
- Every parameter can be edited without graph dragging.
- Touch targets remain at least 48dp even when visible knobs or handles are smaller.
- Selected bands are distinguished by shape/outline as well as color.
- No important operation depends on long press.
- Predictive back closes the Activity without losing settled edits.

## Testing

### Unit tests

- Logarithmic frequency-to-X and X-to-frequency round trips.
- dB-to-Y and Y-to-dB round trips.
- FIR nearest-band selection and vertical-only gain updates.
- Dynamic EQ two-axis drag clamping and stable band identity.
- Multiband crossover ordering and minimum spacing.
- Drag coalescing into one undo operation.
- Store update serialization, debounced persistence, and reconnect full-state dispatch.

### UI and policy tests

- Each main card contains preview-only editor content and retains explicit editor access.
- `EffectEditorActivity` routes all three editor kinds.
- Add/delete, undo/redo, reset confirmation, exact input, and offline state remain reachable.
- No new Material3 UI imports are introduced in the editor surface.

### Verification

- Run all unit tests and `assembleDebug` remotely.
- Install the APK on the connected device.
- Capture portrait and landscape screenshots for all three editors.
- Verify graph drag, knob/fader drag, exact input, undo/redo, predictive back, main-preview synchronization, and service reconnect behavior.

## Delivery order

1. Extract and verify `EffectStateStore` without changing visible behavior.
2. Add Activity routing and the shared editor shell.
3. Add coordinate mapping, graph primitives, knobs, faders, and undo support.
4. Implement FIR editor and convert its main card to preview-only.
5. Implement dynamic EQ editor and convert its main card.
6. Implement multiband compressor editor and convert its main card.
7. Complete responsive visual verification, regression tests, and APK installation.

## Acceptance criteria

- All three effects open in one independent Activity through typed routes.
- Main cards contain no inline parameter lists for these effects.
- Main previews update while the editor is open.
- FIR graph editing cannot alter fixed frequencies.
- Dynamic EQ supports direct frequency/gain graph dragging.
- Multiband crossovers cannot cross and maintain minimum spacing.
- Edits affect live audio when connected and persist when offline.
- Undo/redo treats each drag as one operation.
- Existing presets, imports, advanced parameters, and audio features remain available.
- Unit tests and debug APK build pass, and device screenshots show no overlap or clipped controls in portrait or landscape.

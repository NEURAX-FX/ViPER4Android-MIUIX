# LSP-Inspired Editor UI Alignment Design

## Status

Proposed design. The direction has been approved: adopt LSP's information architecture and interaction model while keeping a responsive MiuiX Android presentation.

## Context

The current editor implementation in `EffectEditorScreen.kt` is a single vertically scrolling column. Each effect places a `VstResponseGraph` and its controls inside an `EditorCard`; FIR exposes every band knob at once, while Dynamic EQ and Multiband Compressor use a selected-band control group.

The LSP reference layouts use a graph-first workspace. The graph occupies the dominant area, with an explicit zoom rail, input/output meters, grouped signal and analysis controls, and a dense band/filter control area. The reference implementation also treats each effect's graph as a different visualization rather than sharing one generic curve.

Reference sources inspected during this design:

- `/tmp/opencode/lsp-plugins-graph-equalizer/res/main/ui/plugins/equalizer/graphic/mono.xml`
- `/tmp/opencode/lsp-plugins-para-equalizer/res/main/ui/plugins/equalizer/parametric/mono.xml`
- `/tmp/opencode/lsp-plugins-mb-compressor/res/main/ui/plugins/dynamics/compressor/multiband/mono.xml`
- `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorScreen.kt`
- `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstResponseGraph.kt`
- `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstKnob.kt`

## Goals

- Make the editor graph-first instead of card-first.
- Reproduce the useful LSP structure: graph, grouped controls, band strip, explicit side utilities, and effect-specific visualization.
- Keep the existing MiuiX theme and project-local Viper wrappers.
- Keep the layout usable in portrait and landscape orientations.
- Preserve the current `EffectStateStore` and `EffectEditorViewModel` data flow.
- Make graph selection and drag behavior predictable on touch screens.
- Avoid drawing values that do not exist in the current state layer.

## Non-Goals

- Do not clone the LSP desktop layout or its fixed minimum dimensions.
- Do not copy LSP colors, XML structure, C++ DSP code, or hidden desktop interactions.
- Do not add a meter backend or invent fake input/output meter values.
- Do not change audio processing, parameter ranges, persistence formats, or effect behavior.
- Do not edit MiuiX library source.

## Design Decision

Use LSP's information architecture with a responsive Android adaptation:

```text
EditorScreen
  GraphWorkspace
    ResponseGraph
    OptionalZoomRail
    OptionalMeterRail
  SignalGroup
  OptionalAnalysisOrModeGroup
  BandStrip
  SelectedBandControls
```

The graph remains the primary visual surface. Controls are grouped beneath or beside it, rather than placing the graph and every control in one undifferentiated card.

### Responsive Layout

Landscape uses a two-region workspace:

```text
[                 graph                 ][zoom][input][output]
[ signal / analysis groups ][ selected band controls ][     ]
[                    band strip / mode row                 ]
```

Portrait uses the same semantic order without forcing a desktop-sized row:

```text
[ graph ]
[ compact zoom/meter row, only when data exists ]
[ signal or analysis group ]
[ band strip ]
[ selected band controls ]
```

The implementation should use `BoxWithConstraints` or an equivalent existing responsive pattern. Exact breakpoints must be chosen from available width, not from device names.

## Visual System

### Surface Hierarchy

- The graph gets one dedicated graph surface using the active MiuiX surface colors.
- Avoid a rounded graph inside another rounded `Card`; the graph workspace should read as one instrument panel.
- Signal, analysis, and selected-band controls remain separate groups with clear headings.
- Side utilities use narrow, flat rails similar to LSP's `bg_schema` columns.

### Grid and Labels

The graph should keep the existing logarithmic frequency mapping and dB mapping, but expose LSP-like grid layers:

- Alt grid: minor decade subdivisions such as 20, 30, 50, 70, 100, and their higher-frequency equivalents.
- Secondary grid: 100, 1k, and 10k plus useful dB reference lines.
- Primary grid: the zero dB line, boundaries, and major labels.
- Frequency labels should include the useful LSP markers instead of labeling only decade starts.
- dB labels should be aligned to the vertical axis area and should not overlap the curve.

Grid density may be reduced in portrait mode, but the mapping must remain logarithmic.

### Knobs and Values

`VstKnob` is directionally correct and should remain the shared control primitive:

- retain the arc, center marker, exact-value dialog, drag gesture, and accessibility progress semantics;
- keep labels and values visually subordinate to the graph;
- use compact grouped layouts rather than rendering all FIR bands as a large knob wall;
- do not introduce fake 3D shading or decorative gradients.

## Shared Components

The implementation plan should introduce or evolve project-local components with narrow responsibilities:

### `VstGraphWorkspace`

Owns the graph surface and optional utility rails. It decides responsive placement but does not calculate effect responses.

### `VstResponseGraph`

Continues to own grid drawing, curve drawing, handles, selection, and drag dispatch. It must support:

- tap-to-select without requiring a drag;
- drag-to-edit with `FREE`, `HORIZONTAL`, and `VERTICAL` constraints;
- selected and active visual states;
- external grid labels and optional band-region overlays;
- content descriptions that identify the graph and its editable handles.

### `VstBandStrip`

Provides an explicit, touch-friendly horizontal band/filter selector. It replaces the FIR editor's current strategy of showing every knob simultaneously. It must expose selected state and not depend on long press.

### `VstControlGroup`

Provides the LSP-like group boundary and heading without duplicating MiuiX internals. It is used for signal, analysis/mode, and selected-band controls.

### `VstMeterRail`

Is optional and data-driven. It is rendered only when real meter values are exposed by the state layer. Until then, the layout must not reserve a misleading empty meter column.

### `VstZoomRail`

Is optional and data-driven. It is rendered only when the editor has a meaningful zoom value and update callback. A decorative, non-functional fader is not acceptable.

## Effect-Specific Behavior

### FIR Equalizer

- Keep fixed band frequencies and vertical gain editing.
- Show the response graph first.
- Show a horizontal frequency band strip below the graph.
- Show controls for the selected band instead of a simultaneous 15/25/31-knob wall.
- Preserve direct gain editing and exact values.
- The curve must be derived from the fixed-band response helper, not from the average of all values.

### Dynamic Equalizer

- Keep the selected-band model because it fits the LSP parametric layout and mobile touch constraints.
- Keep frequency, Q, gain, threshold, attack, and release in the selected-band group.
- Keep selected handles and parametric response calculation synchronized with state.
- Use the band strip for explicit band selection in addition to graph selection.
- Keep frequency movement on a logarithmic axis.

### Multiband Compressor

- Show crossover handles as vertical frequency boundaries.
- Color each band region using the graph marker cycle or theme-derived semantic colors.
- Keep threshold, ratio, gain, attack, and release in the selected-band control group.
- Do not use threshold values as a frequency-response curve. The existing `bandedStepCurvePoints` visualization is not the correct semantic model for this graph and must not remain the primary MBC graph.
- If no real spectrum or gain-reduction data is available, show structural band regions and crossover boundaries only. Do not fabricate a mesh.
- Crossover updates must retain the existing one-semitone spacing constraint.

## Interaction Rules

- Tapping a handle selects it.
- Dragging a handle begins one undoable gesture and settles it on drag end.
- A vertical-only handle cannot change frequency; a horizontal-only handle cannot change level.
- Selected band state is shared between graph handles and the band strip.
- Exact values remain available through the existing visible value interaction.
- Important operations such as reset, enable/bypass, band selection, and deletion must remain visible actions; no long-press-only behavior.
- All interactive handles and selectors must expose useful accessibility labels.

## Data Flow

```text
EffectStateStore
  -> EffectEditorViewModel
  -> EffectEditorScreen
  -> derived graph model + selected-band model
  -> VstGraphWorkspace / VstResponseGraph / VstBandStrip / VstKnob
```

The screen derives graph geometry from the collected `EffectState`. User gestures call the existing ViewModel update methods. No graph component writes directly to persistence or the audio service.

Meter and zoom utilities are conditional on state support. Adding those state fields is a separate, explicitly scoped task and is not part of the initial visual alignment pass.

## Verification

### Unit Tests

- logarithmic frequency and dB mapping boundaries;
- grid marker ordering and labels;
- fixed-band response behavior;
- parametric response peak, symmetry, and Q behavior;
- crossover ordering and minimum spacing;
- graph selection/drag mapping where the logic is extracted from Compose.

### Build Verification

Use the documented remote build environment:

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"
```

The implementation is not complete until the relevant unit tests and `assembleDebug` both pass.

### Manual UI Checks

- FIR with 15, 25, and 31 bands in portrait and landscape;
- Dynamic EQ with selected-band changes from both graph and band strip;
- Multiband Compressor with malformed/short persisted lists and crossover dragging;
- graph tap selection without dragging;
- exact-value editing and undo/redo after graph gestures;
- disconnected/offline state without fake meters;
- dark/light MiuiX themes and narrow screens.

## Rollout Order

1. Extract the graph workspace and remove the nested graph-card hierarchy.
2. Add tap selection and LSP-like grid/label layering.
3. Add the responsive band strip and selected-band control layout.
4. Align FIR and Dynamic EQ visual models.
5. Replace the MBC threshold stair graph with crossover regions and markers.
6. Add real zoom or meter rails only if corresponding state is available.

## Acceptance Criteria

- The editor visibly reads as a graph-first instrument panel rather than a vertical card list.
- Portrait and landscape layouts preserve the same semantic order and remain touchable.
- FIR no longer renders every band control as an undifferentiated knob wall.
- Dynamic EQ graph, handles, band strip, and selected controls stay synchronized.
- MBC graph no longer presents thresholds as a fake frequency response.
- Tap selection works independently of drag.
- No audio feature, persistence behavior, or parameter range is removed.
- Unit tests and `assembleDebug` pass.

# LSP UI Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the effect editor into a responsive, graph-first MiuiX instrument panel inspired by LSP while preserving all existing effect state, parameter ranges, and audio behavior.

**Architecture:** Keep `EffectEditorScreen` as the state-driven screen and keep response calculations in pure Kotlin files. Add small project-local visual primitives for the graph workspace, band selector, and control groups. The graph receives derived geometry and dispatches gestures to the existing `EffectEditorViewModel`; it never writes to persistence or the audio service.

**Tech Stack:** Kotlin, Jetpack Compose Foundation/Runtime/UI, existing MiuiX theme and project-local Viper components, JUnit 4 unit tests, Gradle remote build.

## Global Constraints

- Keep icons. Do not remove `androidx.compose.material.icons.*` just because Material3 UI is being removed.
- Do not edit MiuiX library source. Use public MiuiX APIs and project-local wrappers.
- Do not drop existing audio features during UI migration.
- Do not rely on hidden long-press actions for important operations.
- Keep migration incremental and buildable after each meaningful step.
- Do not change audio processing, parameter ranges, persistence formats, or effect behavior.
- Do not add fake input/output meter values or decorative non-functional zoom controls.
- The graph must remain logarithmic in frequency and dB-aware in vertical mapping.

---

## File Map

### Existing files to modify

- `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstResponseGraph.kt`: graph surface, grid layers, region overlays, tap selection, drag handling, and semantics.
- `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstKnob.kt`: only if the grouped control layout needs a narrowly scoped accessibility or sizing adjustment.
- `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorScreen.kt`: responsive workspace, band strip integration, selected-band controls, and effect-specific graph models.
- `app/src/main/java/com/llsl/viper4android/ui/screens/editor/GraphMapping.kt`: reusable graph hit testing and grid/mapping helpers.
- `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EqResponse.kt`: response samples and MBC structural region helpers.
- `app/src/test/java/com/llsl/viper4android/ui/screens/editor/GraphMappingTest.kt`: mapping, grid, and hit-test coverage.
- `app/src/test/java/com/llsl/viper4android/ui/screens/editor/EqResponseTest.kt`: effect response and region coverage.

### New files to create

- `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstBandStrip.kt`: explicit horizontal band/filter selector.
- `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstControlGroup.kt`: flat LSP-like group boundary and heading.
- `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstGraphWorkspace.kt`: responsive graph-first placement and optional data-driven rails.

---

### Task 1: Stabilize Graph Models and Hit Testing

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/GraphMapping.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EqResponse.kt`
- Test: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/GraphMappingTest.kt`
- Test: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/EqResponseTest.kt`

**Interfaces:**
- `GraphMapping.kt` continues to expose `frequencyToX`, `xToFrequency`, `dbToY`, `yToDb`, `frequencyGridLines`, `decibelGridLines`, and crossover constraints.
- Add `data class GraphHandleModel(val id: String, val x: Float, val y: Float)` to `GraphMapping.kt` as the pure, Compose-free handle geometry model.
- Add a pure `nearestGraphHandle(handles: List<GraphHandleModel>, x: Float, y: Float, hitRadius: Float): Int?` helper using normalized coordinates. It returns the index of the closest handle within `hitRadius` (Euclidean distance in normalized space), or `null` when nothing is within range.
- Add `data class GraphBandRegion(val startX: Float, val endX: Float, val label: String? = null)` to `GraphMapping.kt` as the pure geometry model consumed by the graph component.
- Add `mbcBandRegions(crossovers: List<Double>, minFrequency: Double, maxFrequency: Double): List<GraphBandRegion>` to `EqResponse.kt`.
- The existing Compose `GraphHandle` in `VstResponseGraph.kt` keeps its color/label fields; the graph converts it to `GraphHandleModel` before calling `nearestGraphHandle`.

- [x] **Step 1: Write failing tests for handle selection and MBC regions**

```kotlin
@Test
fun nearestGraphHandleReturnsNullOutsideTouchRadius() {
    val handles = listOf(GraphHandleModel("a", 0.2f, 0.4f))

    assertNull(nearestGraphHandle(handles, 0.8f, 0.4f, hitRadius = 0.08f))
}

@Test
fun nearestGraphHandlePicksTheClosestHandleInRange() {
    val handles = listOf(
        GraphHandleModel("a", 0.2f, 0.4f),
        GraphHandleModel("b", 0.3f, 0.4f),
    )

    assertEquals(1, nearestGraphHandle(handles, 0.28f, 0.42f, hitRadius = 0.1f))
}

@Test
fun mbcRegionsUseLogFrequencyBoundaries() {
    val regions = mbcBandRegions(listOf(200.0, 2000.0), 20.0, 20000.0)

    assertEquals(3, regions.size)
    assertEquals(0f, regions.first().startX, 0.0001f)
    assertEquals(1f, regions.last().endX, 0.0001f)
    assertTrue(regions.zipWithNext().all { (a, b) -> a.endX == b.startX })
}
```

- [x] **Step 2: Run the focused tests and verify they fail for missing helpers**

Run: `ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:testDebugUnitTest --tests '*GraphMappingTest*' --tests '*EqResponseTest*' --no-daemon"`

Expected: compilation failure naming the new helper/model symbols.

- [x] **Step 3: Implement the smallest pure helpers**

Use normalized logarithmic positions for MBC regions:

```kotlin
fun mbcBandRegions(
    crossovers: List<Double>,
    minFrequency: Double,
    maxFrequency: Double,
): List<GraphBandRegion> {
    val boundaries = listOf(minFrequency) + crossovers + listOf(maxFrequency)
    return boundaries.zipWithNext { start, end ->
        GraphBandRegion(
            startX = frequencyToX(start, minFrequency, maxFrequency),
            endX = frequencyToX(end, minFrequency, maxFrequency),
            label = null,
        )
    }
}
```

Reject invalid crossovers by reusing the existing `constrainCrossovers` path before calling the region helper. Do not add a second persistence policy.

- [x] **Step 4: Run focused tests and confirm they pass**

Run the same focused Gradle command. Expected: all existing and new mapping/response tests pass.

---

### Task 2: Add Shared Graph-First Visual Primitives

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstBandStrip.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstControlGroup.kt`
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstGraphWorkspace.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstResponseGraph.kt`

**Interfaces:**
- `VstBandStrip(items: List<VstBandItem>, selectedIndex: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier)`.
- `VstBandItem(id: String, title: String, value: String, color: Color? = null, enabled: Boolean = true)`.
- `VstControlGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`.
- `VstGraphWorkspace(modifier: Modifier = Modifier, graph: @Composable BoxScope.() -> Unit, utilityRail: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit)`.
- Extend `VstResponseGraph` with `bandRegions: List<GraphBandRegion> = emptyList()` and tap selection that calls the existing `onHandleSelected` without requiring drag.

- [x] **Step 1: Add the band strip with explicit click semantics**

Implement a horizontally scrollable row of compact MiuiX-colored segments. Each segment must have a minimum 48dp touch target, selected styling, a visible title/value, and `Modifier.semantics { contentDescription = ... }`. Do not use long press.

- [x] **Step 2: Add the flat control group wrapper**

Use `MiuixTheme.colorScheme.surfaceContainerHigh` and existing typography. The wrapper should provide one heading and a `ColumnScope` content slot, without importing Material3 `Card`.

- [x] **Step 3: Add responsive graph workspace placement**

Use `BoxWithConstraints` to place the graph and optional rail side by side only when width is sufficient. When no rail is supplied, the graph must consume the full available width. Do not reserve empty columns for unavailable meter/zoom state.

- [x] **Step 4: Add graph region overlays and tap selection**

Draw translucent regions before grid lines and curve. Use an `awaitEachGesture`/tap-or-drag pointer flow or an equivalent Compose Foundation implementation so a pointer-up without movement selects the nearest handle. Preserve `GraphDragAxis` constraints during drag. Keep touch radius in dp and convert once with `LocalDensity`.

- [x] **Step 5: Run compile and existing graph tests**

Run: `ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:testDebugUnitTest --tests '*GraphMappingTest*' :app:compileDebugKotlin --no-daemon --max-workers=1"`

Expected: the new components compile and graph mapping tests remain green.

---

### Task 3: Refactor the Editor Layout Around the Shared Workspace

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorScreen.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EditorModels.kt` only if an editor-specific band item model is needed.

**Interfaces:**
- Keep `EffectEditorScreen`'s public entry point and `EffectEditorViewModel` callbacks unchanged.
- Each editor supplies `VstGraphWorkspace`, one `VstBandStrip`, and one `VstControlGroup` for selected-band controls.
- Graph selection and strip selection both update the existing local `selectedBand` state.

- [x] **Step 1: Extract the common editor body shape without changing effect behavior**

Replace the current repeated `EditorCard { VstResponseGraph(...) }` plus control sequence with a common graph-first body. Keep the existing top bar, bypass, reset, undo, redo, and dialogs intact.

- [x] **Step 2: Refactor FIR to selected-band controls**

Use `selectedBand` for FIR. Build strip items from the existing frequency list and gain values. Render one selected band's gain knob beneath the strip. Keep direct handle dragging and `viewModel.updateFirBand`/existing flush behavior. Remove only the simultaneous knob wall; do not remove any band data or controls.

- [x] **Step 3: Refactor Dynamic EQ to shared strip and group**

Build strip items from `dynamic.freqs` and `dynamic.gains`. Keep the existing six selected-band knobs and update callbacks. Pass `selectedHandleId` to the graph and ensure selecting a graph handle updates the same `selectedBand` index used by the strip.

- [x] **Step 4: Refactor MBC to shared strip and structural regions**

Use sanitized fixed-size threshold/crossover lists already used for malformed persisted data. Keep threshold/ratio/gain/attack/release controls. Replace `bandedStepCurvePoints` as the primary graph curve with `mbcBandRegions` and crossover handles. The graph must show band boundaries and region colors, not threshold values mapped as a fake frequency response.

- [x] **Step 5: Preserve portrait and landscape usability**

Verify the common body uses width constraints, does not require horizontal scrolling for the graph, and keeps each selected control at a minimum touch size. Do not add hard-coded system-bar padding; rely on the existing `ViperScaffold`/MiuiX inset behavior.

---

### Task 4: Align Graph Rendering and Accessibility

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/VstResponseGraph.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

**Interfaces:**
- Keep `GraphHandle.valueDescription` as the source for handle semantics.
- Add localized graph descriptions only where the screen currently uses a string resource.

- [x] **Step 1: Add layered LSP-like grid labels**

Expose minor and major grid lines using the existing `frequencyGridLines`/`decibelGridLines` data. Keep labels outside the curve path and clamp them inside the graph bounds. Reduce label density only through the existing `showGridLabels`/line lists, not by changing frequency mapping.

- [x] **Step 2: Add handle selection semantics**

When a handle is selected, expose its label and current value through `contentDescription`. The graph description must identify its effect type. A tap on empty graph space may invoke the existing graph click callback but must not mutate state.

- [x] **Step 3: Verify localized resources and accessibility compilation**

Run `:app:compileDebugKotlin` and inspect that all three resource locales contain the same graph resource names.

---

### Task 5: Verification and Regression Pass

**Files:**
- Test: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/GraphMappingTest.kt`
- Test: `app/src/test/java/com/llsl/viper4android/ui/screens/editor/EqResponseTest.kt`
- Test: existing editor routing/history tests.

- [x] **Step 1: Run all editor unit tests**

Run:

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew :app:testDebugUnitTest --tests '*ui.screens.editor*' --no-daemon --max-workers=1"
```

Expected: all editor tests pass with zero failures and zero errors.

- [x] **Step 2: Run the full debug build**

Run:

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"
```

Expected: `BUILD SUCCESSFUL` and a debug APK under `app/build/outputs/apk/debug/`.

- [ ] **Step 3: Perform the manual matrix**

Check FIR with 15/25/31 bands, Dynamic EQ selection from graph and strip, MBC with short persisted lists, crossover drag spacing, exact-value editing, undo/redo, offline state, dark/light themes, portrait, and landscape. Record any issue before claiming completion.

- [x] **Step 4: Run `git diff --check` and inspect the final diff**

Run: `git diff --check` and `git diff --stat`.

Expected: no whitespace errors, only files listed in this plan changed for this feature, and no unrelated user changes reverted.

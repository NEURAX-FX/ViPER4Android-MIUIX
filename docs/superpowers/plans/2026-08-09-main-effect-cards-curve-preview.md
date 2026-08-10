# Main Effect Cards & Curve Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the main effect list with compact MiuiX cards that show live summaries, JDSP-inspired 85dp curve previews, and explicit links to the existing full editors.

**Architecture:** Move the reusable card shell, compact curve renderer, and editor action row into project-local `viper` components. Keep DSP graph generation and all `MainViewModel` callbacks unchanged; the main screen consumes existing graph models in a non-interactive preview while dedicated editor screens retain `VstResponseGraph`. Summary formatting stays in pure Kotlin helpers so value conversion and resource-name handling are unit-testable.

**Tech Stack:** Kotlin, Jetpack Compose, MiuiX KMP 0.9.x, AndroidX Material icons, JUnit 4, remote Gradle build on Android over SSH.

## Global Constraints

- Do not change DSP algorithms, parameter IDs, persistence keys, resource upload behavior, or editor graph models.
- Do not edit MiuiX source.
- Keep `androidx.compose.material.icons.*` imports and existing icon choices.
- Do not introduce new Material3 `Card`, `Switch`, `Slider`, `Tab`, `TextButton`, or dialog usage.
- Main-screen curve previews are 85dp high, non-interactive, omit handles and labels, and respect `showCurvePreviews`.
- Equalizer, Multiband Compressor, and Dynamic EQ always expose an explicit editor row, even when previews are hidden.
- Existing full editors continue using `VstResponseGraph`; only the main list uses `ViperCurvePreview`.
- All cards use a title plus one ellipsized summary line and a 58dp minimum header height.
- Switch taps only toggle enabled state; header taps only toggle expansion.
- Build and tests run only in the remote `~/ViPER4Android` environment via SSH port 8022.
- Preserve unrelated dirty worktree changes and do not commit unless the user explicitly requests it.

---

### Task 1: Pure Summary Formatting

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSectionSummaries.kt`
- Create: `app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionSummaryTest.kt`

**Interfaces:**
- Produces: `basenameOrNone(path: String, noneLabel: String): String`
- Produces: `formatConvolverSummary(kernelFile: String, wet: Int, crossDelay100Ns: Int, noneLabel: String, wetLabel: String): String`
- Produces: `formatOutputSummary(volume: Int, channelPan: Int, limiter: Int, limiterLabel: String): String`
- Produces: `formatMultiplier(rawHundredths: Int): String`
- Produces: `joinEffectSummary(vararg values: String): String`

- [ ] **Step 1: Write failing formatter tests**

```kotlin
package com.llsl.viper4android.ui.screens.main

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectSectionSummaryTest {
    @Test
    fun convolverUsesBasenameAndExactDelayPrecision() {
        assertEquals(
            "Neurax08.wav · Wet 100% · 0.3125 ms",
            formatConvolverSummary(
                kernelFile = "/storage/emulated/0/Kernel/Neurax08.wav",
                wet = 100,
                crossDelay100Ns = 3125,
                noneLabel = "None",
                wetLabel = "Wet",
            ),
        )
    }

    @Test
    fun convolverUsesNoneForMissingKernel() {
        assertEquals(
            "None · Wet 65% · 10.0000 ms",
            formatConvolverSummary("", 65, 100000, "None", "Wet"),
        )
    }

    @Test
    fun outputConvertsRawValuesAndPan() {
        assertEquals(
            "0.0 dB · 50:50 · Limiter 0.0 dB",
            formatOutputSummary(100, 0, 100, "Limiter"),
        )
    }

    @Test
    fun multiplierUsesOneDecimalPlace() {
        assertEquals("2.5x", formatMultiplier(250))
    }
}
```

- [ ] **Step 2: Sync the test and verify RED remotely**

Run:

```bash
rsync -azR -e 'ssh -p 8022' \
  "app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionSummaryTest.kt" \
  10645@localhost:~/ViPER4Android/
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --tests "*EffectSectionSummaryTest" --no-daemon'
```

Expected: Kotlin test compilation fails because the formatter functions do not exist.

- [ ] **Step 3: Implement the pure formatters**

```kotlin
package com.llsl.viper4android.ui.screens.main

import java.io.File
import java.util.Locale
import kotlin.math.log10

internal fun basenameOrNone(path: String, noneLabel: String): String =
    path.takeIf(String::isNotBlank)?.let { File(it).name }.orEmpty().ifBlank { noneLabel }

internal fun formatConvolverSummary(
    kernelFile: String,
    wet: Int,
    crossDelay100Ns: Int,
    noneLabel: String,
    wetLabel: String,
): String =
    String.format(
        Locale.US,
        "%s · %s %d%% · %.4f ms",
        basenameOrNone(kernelFile, noneLabel),
        wetLabel,
        wet,
        crossDelay100Ns / 10000.0,
    )

internal fun formatOutputSummary(
    volume: Int,
    channelPan: Int,
    limiter: Int,
    limiterLabel: String,
): String {
    fun rawToDb(raw: Int): Double = if (raw > 0) 20.0 * log10(raw / 100.0) else -99.9
    val left = 50 - channelPan / 2
    val right = 50 + channelPan / 2
    return String.format(
        Locale.US,
        "%.1f dB · %d:%d · %s %.1f dB",
        rawToDb(volume),
        left,
        right,
        limiterLabel,
        rawToDb(limiter),
    )
}

internal fun formatMultiplier(rawHundredths: Int): String =
    String.format(Locale.US, "%.1fx", rawHundredths / 100.0)

internal fun joinEffectSummary(vararg values: String): String =
    values.filter(String::isNotBlank).joinToString(" · ")
```

- [ ] **Step 4: Sync implementation and verify GREEN remotely**

```bash
rsync -azR -e 'ssh -p 8022' \
  "app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSectionSummaries.kt" \
  10645@localhost:~/ViPER4Android/
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --tests "*EffectSectionSummaryTest" --no-daemon'
```

Expected: `EffectSectionSummaryTest` passes.

### Task 2: Reusable Compact MiuiX Effect Card

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEffectCard.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt:139-227`
- Modify: `app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionsMaterialPolicyTest.kt`

**Interfaces:**
- Produces: `ViperEffectCard(title, summary, enabled, onEnabledChange, icon, hasEnableSwitch, toggleOnly, initiallyExpanded, content)`
- Consumes later: every main effect section uses this wrapper.

- [ ] **Step 1: Add a failing source-policy test for the wrapper**

```kotlin
@Test
fun compactEffectCardUsesMiuixAndSeparatesExpansionFromPower() {
    val card = readSource(
        "app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEffectCard.kt",
    )
    assertTrue("Card must expose summary", "summary: String?" in card)
    assertTrue("Summary must ellipsize", "TextOverflow.Ellipsis" in card && "maxLines = 1" in card)
    assertTrue("Header must own expansion", "expanded = !expanded" in card)
    assertFalse("Power switch must not mutate expansion", "expanded = checked" in card)
    assertTrue("Card must use MiuiX Card", "Card as MiuixCard" in card)
    assertTrue("Card must use MiuiX Switch", "Switch as MiuixSwitch" in card)
    assertFalse("Wrapper must not import Material3", "androidx.compose.material3" in card)
}
```

- [ ] **Step 2: Sync the test and verify RED remotely**

```bash
rsync -azR -e 'ssh -p 8022' \
  "app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionsMaterialPolicyTest.kt" \
  10645@localhost:~/ViPER4Android/
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --tests "*EffectSectionsMaterialPolicyTest" --no-daemon'
```

Expected: failure because `ViperEffectCard.kt` is missing.

- [ ] **Step 3: Implement `ViperEffectCard`**

Use this exact public signature:

```kotlin
@Composable
fun ViperEffectCard(
    title: String,
    summary: String? = null,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    hasEnableSwitch: Boolean = true,
    toggleOnly: Boolean = false,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
)
```

Implementation requirements:

- Outer `MiuixCard`: `fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)`, 12dp corner radius, zero inside margin.
- Header: `heightIn(min = 58.dp)`, 12dp horizontal padding, 8dp vertical padding.
- Icon chip: 32dp square, 10dp corner radius, primary alpha `0.16f` enabled and `0.08f` disabled.
- Title: `MiuixTheme.textStyles.body1`.
- Summary: `body2`, `onSurfaceVariantSummary`, `maxLines = 1`, `TextOverflow.Ellipsis`.
- Expand icon: `Icons.Default.ExpandLess` or `Icons.Default.ExpandMore` when not `toggleOnly`.
- Header click toggles only `expanded`; switch callback calls only `onEnabledChange`.
- Body retains `AnimatedVisibility`, `expandVertically`, `shrinkVertically`, and current 10dp/6dp padding.

- [ ] **Step 4: Replace the local card implementation**

Remove the local `EffectSection` function from `EffectSections.kt`, import `ViperEffectCard`, and replace every `EffectSection(` call with `ViperEffectCard(`. Do not change section parameter callbacks in this step.

- [ ] **Step 5: Sync changed files and verify GREEN remotely**

```bash
rsync -azR -e 'ssh -p 8022' \
  "app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEffectCard.kt" \
  "app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt" \
  10645@localhost:~/ViPER4Android/
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --tests "*EffectSectionsMaterialPolicyTest" :app:compileDebugKotlin --no-daemon'
```

Expected: policy tests pass and Kotlin compilation succeeds.

### Task 3: JDSP-Inspired 85dp Curve Preview

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperCurvePreview.kt`
- Create: `app/src/test/java/com/llsl/viper4android/ui/components/viper/ViperCurvePreviewTest.kt`

**Interfaces:**
- Produces: `previewAreaPolygon(points: List<Offset>): List<Offset>`
- Produces: `ViperCurvePreview(curve, bandCurves, referenceCurves, contentDescription, onClick, modifier)`

- [ ] **Step 1: Write failing geometry tests**

```kotlin
package com.llsl.viper4android.ui.components.viper

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViperCurvePreviewTest {
    @Test
    fun areaPolygonClosesResponseToBottomEdge() {
        val result = previewAreaPolygon(listOf(Offset(0.2f, 0.3f), Offset(0.8f, 0.6f)))
        assertEquals(
            listOf(Offset(0.2f, 1f), Offset(0.2f, 0.3f), Offset(0.8f, 0.6f), Offset(0.8f, 1f)),
            result,
        )
    }

    @Test
    fun invalidOrSinglePointCurvesAreRejected() {
        assertTrue(previewAreaPolygon(listOf(Offset.Zero)).isEmpty())
        assertTrue(previewAreaPolygon(listOf(Offset(Float.NaN, 0f), Offset(1f, 1f))).isEmpty())
    }
}
```

- [ ] **Step 2: Sync the test and verify RED remotely**

```bash
rsync -azR -e 'ssh -p 8022' \
  "app/src/test/java/com/llsl/viper4android/ui/components/viper/ViperCurvePreviewTest.kt" \
  10645@localhost:~/ViPER4Android/
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --tests "*ViperCurvePreviewTest" --no-daemon'
```

Expected: unresolved `previewAreaPolygon`.

- [ ] **Step 3: Implement geometry normalization**

```kotlin
internal fun previewAreaPolygon(points: List<Offset>): List<Offset> {
    if (points.size < 2 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return emptyList()
    val normalized = points.map { Offset(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }
    return buildList(normalized.size + 2) {
        add(Offset(normalized.first().x, 1f))
        addAll(normalized)
        add(Offset(normalized.last().x, 1f))
    }
}
```

- [ ] **Step 4: Implement `ViperCurvePreview`**

Use this signature:

```kotlin
@Composable
fun ViperCurvePreview(
    curve: List<Offset>,
    bandCurves: List<List<Offset>> = emptyList(),
    referenceCurves: List<List<Offset>> = emptyList(),
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

The composable must:

- use a `Canvas` with `fillMaxWidth().height(85.dp).clickable(onClick)`;
- draw a 12dp rounded `surfaceContainerHigh` background at 34% alpha;
- draw three horizontal and four vertical grid lines using outline alpha 0.08, with the center horizontal line at alpha 0.12;
- draw reference curves first at outline alpha 0.45;
- draw band curves next using alternating primary/secondary colors at alpha 0.55;
- draw the primary curve with a 2dp stroke and a vertical primary gradient fill from alpha 0.35 to zero;
- draw no handles, labels, drag targets, or spectrum layer;
- provide a localized content description through semantics.

- [ ] **Step 5: Sync implementation and verify GREEN remotely**

```bash
rsync -azR -e 'ssh -p 8022' \
  "app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperCurvePreview.kt" \
  10645@localhost:~/ViPER4Android/
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --tests "*ViperCurvePreviewTest" :app:compileDebugKotlin --no-daemon'
```

Expected: both pass.

### Task 4: Explicit Editor Action Row And Localization

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEditorRow.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionsMaterialPolicyTest.kt`

**Interfaces:**
- Produces: `ViperEditorRow(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: Add a failing policy test**

```kotlin
@Test
fun editorRowIsExplicitAndMaterial3Free() {
    val row = readSource(
        "app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEditorRow.kt",
    )
    assertTrue("Editor row must be clickable", ".clickable(" in row)
    assertTrue("Editor row must show a chevron", "KeyboardArrowRight" in row)
    assertFalse("Editor row must not use Material3", "androidx.compose.material3" in row)
}
```

- [ ] **Step 2: Sync the test and verify RED remotely**

```bash
rsync -azR -e 'ssh -p 8022' \
  "app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionsMaterialPolicyTest.kt" \
  10645@localhost:~/ViPER4Android/
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --tests "*EffectSectionsMaterialPolicyTest" --no-daemon'
```

Expected: missing `ViperEditorRow.kt` source-file failure.

- [ ] **Step 3: Implement `ViperEditorRow`**

Use MiuiX `Icon` and `Text`, an explicit full-row click target, 12dp rounded primary-alpha background, 12dp horizontal/10dp vertical padding, passed leading icon, weighted title, and `Icons.Default.KeyboardArrowRight` on the right.

- [ ] **Step 4: Add exact localized strings**

Add these keys to all three locale files:

```xml
<string name="action_open_eq_editor">Open Equalizer Editor</string>
<string name="action_open_mbc_editor">Open Multiband Compressor Editor</string>
<string name="action_open_dynamic_eq_editor">Open Dynamic EQ Editor</string>
<string name="summary_active_bands">%1$d active bands</string>
```

Chinese values: `打开均衡器编辑器`, `打开多段压缩编辑器`, `打开动态均衡编辑器`, `%1$d 个启用频段`.

Russian values: `Открыть редактор эквалайзера`, `Открыть редактор мультиполосного компрессора`, `Открыть редактор динамического EQ`, `Активных полос: %1$d`.

- [ ] **Step 5: Sync and verify GREEN remotely**

```bash
rsync -azR -e 'ssh -p 8022' \
  "app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEditorRow.kt" \
  "app/src/main/res/values/strings.xml" \
  "app/src/main/res/values-zh-rCN/strings.xml" \
  "app/src/main/res/values-ru/strings.xml" \
  10645@localhost:~/ViPER4Android/
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --tests "*EffectSectionsMaterialPolicyTest" :app:compileDebugKotlin --no-daemon'
```

Expected: both pass.

### Task 5: Integrate Compact Previews Into Existing Graph Cards

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt:653-900`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt:337-360`
- Modify: `app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionsMaterialPolicyTest.kt`

**Interfaces:**
- Consumes: `ViperCurvePreview` from Task 3.
- Consumes: `ViperEditorRow` from Task 4.
- Preserves: `onOpenEditor: () -> Unit` callbacks and existing graph model functions.

- [ ] **Step 1: Add a failing integration policy test**

```kotlin
@Test
fun mainGraphCardsUseCompactPreviewAndExplicitEditors() {
    val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt")
    assertTrue("Main cards must use compact preview", "ViperCurvePreview(" in source)
    assertTrue("Main cards must expose editor rows", "ViperEditorRow(" in source)
    assertFalse("Main cards must not render full editor graph", "VstResponseGraph(" in source)
}
```

- [ ] **Step 2: Sync the test and verify RED remotely**

```bash
rsync -azR -e 'ssh -p 8022' \
  "app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionsMaterialPolicyTest.kt" \
  10645@localhost:~/ViPER4Android/
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --tests "*EffectSectionsMaterialPolicyTest" --no-daemon'
```

Expected: failure because `VstResponseGraph` remains in main sections.

- [ ] **Step 3: Migrate Multiband Compressor**

Replace the current preview body with:

```kotlin
if (showCurvePreview) {
    ViperCurvePreview(
        curve = model.unitySumCurve,
        bandCurves = model.bandCurves,
        contentDescription = stringResource(R.string.section_multiband_compressor),
        onClick = onOpenEditor,
    )
}
ViperEditorRow(
    title = stringResource(R.string.action_open_mbc_editor),
    icon = Icons.Default.Insights,
    onClick = onOpenEditor,
)
```

Remove `initiallyExpanded = true`.

- [ ] **Step 4: Migrate Equalizer**

Use `curve = model.curve`, `contentDescription = stringResource(R.string.section_equalizer)`, retain `firGraphModel(state, sampleRate)`, append the equalizer `ViperEditorRow`, and remove `initiallyExpanded = true`. Do not restore the unreachable legacy `EqCurveGraph` block after the existing `return`.

- [ ] **Step 5: Migrate Dynamic EQ and pass the setting**

Add `showCurvePreview: Boolean = true` to `DynamicEqSection`, use `ViperCurvePreview(curve = model.curve, contentDescription = stringResource(R.string.section_dynamic_eq), onClick = onOpenEditor)`, append its editor row, remove `initiallyExpanded = true`, and pass `showCurvePreviews` from `MainScreen.EffectList`.

- [ ] **Step 6: Remove obsolete main-screen graph adapters**

Delete `previewColors`, `toGraphHandles`, `GraphHandle`, `GraphHandlePoint`, `GraphDragAxis`, `VstResponseGraph`, and `mbcBandRegions` imports/usages from `EffectSections.kt` only when no live code references them. Do not modify dedicated editor files.

- [ ] **Step 7: Sync and verify GREEN remotely**

```bash
rsync -azR -e 'ssh -p 8022' \
  "app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt" \
  "app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt" \
  10645@localhost:~/ViPER4Android/
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --tests "*EffectSectionsMaterialPolicyTest" :app:compileDebugKotlin --no-daemon'
```

Expected: no full `VstResponseGraph` call remains in `EffectSections.kt` and compilation succeeds.

### Task 6: Add Live Summaries To Primary And Resource Cards

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt:229-1243`
- Test: `app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionSummaryTest.kt`

**Interfaces:**
- Consumes: Task 1 summary functions.
- Consumes: `ViperEffectCard(summary = ...)` from Task 2.

- [ ] **Step 1: Add summaries to primary cards**

Pass these exact summaries to `ViperEffectCard`:

| Card | Summary expression |
|---|---|
| Output | `formatOutputSummary(outputVolume, channelPan, limiter, stringResource(R.string.label_output_limiter))` |
| Playback Gain | `joinEffectSummary(formatMultiplier(strength), "${stringResource(R.string.label_max_gain)} ${formatMultiplier(maxGain)}", String.format(Locale.US, "%.1f dB", threshDb))` |
| LUFS | `joinEffectSummary(String.format(Locale.US, "%.1f LUFS", target / -10f), "${stringResource(R.string.label_max_gain)} ${String.format(Locale.US, "%.1f dB", maxGain / 10f)}", speedNames.getOrElse(speed) { speedNames[1] })` |
| FET Compressor | `joinEffectSummary("$threshold dB", String.format(Locale.US, "%.1f:1", ratio / 100.0), if (gainAuto) stringResource(R.string.label_fet_auto_gain) else "$gain dB")` |
| Multiband Compressor | localized active-band count from `bandEnables.count { it }` |
| DDC | `basenameOrNone(device, ddcNoneLabel)` |
| Spectrum Extension | `joinEffectSummary("$strength Hz", "$exciter%")` |
| Equalizer | localized `bandCount` plus resolved preset name or localized Custom |
| Dynamic EQ | localized `bandCount` plus existing localized `label_custom` |
| Convolver | `formatConvolverSummary(kernel, wet, crossDelay100Ns, kernelNoneLabel, stringResource(R.string.label_convolver_wet))` |

Use existing localized labels where available. Add only missing short summary labels to all three string files; do not hardcode English words inside localized summaries.

Remove `initiallyExpanded = true` from the Output card so every main effect card now starts collapsed.

- [ ] **Step 2: Add a source coverage assertion**

Extend `EffectSectionsMaterialPolicyTest` to count `summary =` occurrences and assert at least ten, preventing accidental omission of the high-traffic cards.

- [ ] **Step 3: Sync and verify remotely**

```bash
rsync -azR -e 'ssh -p 8022' \
  "app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt" \
  "app/src/main/res/values/strings.xml" \
  "app/src/main/res/values-zh-rCN/strings.xml" \
  "app/src/main/res/values-ru/strings.xml" \
  "app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionsMaterialPolicyTest.kt" \
  10645@localhost:~/ViPER4Android/
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --tests "*EffectSectionSummaryTest" --tests "*EffectSectionsMaterialPolicyTest" :app:compileDebugKotlin --no-daemon'
```

Expected: all pass.

### Task 7: Add Live Summaries To Spatial And Enhancement Cards

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt:1245-2204`
- Modify: locale string files only if a mode label is not already available.
- Test: `app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionsMaterialPolicyTest.kt`

**Interfaces:**
- Consumes: `joinEffectSummary`, `formatMultiplier`, and `ViperEffectCard(summary = ...)`.

- [ ] **Step 1: Add spatial summaries**

Use these values:

| Card | Summary values |
|---|---|
| Field Surround | widening, mid image, depth |
| Differential Surround | delay ms, wet %, reverse state |
| Stereo Imager | low/mid/high width percentages |
| Headphone Surround | quality level |
| Reverb | room size, dampening, wet % |
| Dynamic System | resolved preset name, strength % |

- [ ] **Step 2: Add enhancement summaries**

Use these values:

| Card | Summary values |
|---|---|
| Tube Simulator | localized enabled/disabled state |
| Psychoacoustic Bass | cutoff Hz, intensity %, harmonic name |
| ViPER Bass | resolved mode, displayed frequency (`frequency + 15`) when applicable, gain multiplier |
| Mono Bass | resolved mode, displayed frequency when applicable, gain multiplier |
| Clarity | resolved mode, gain multiplier |
| Auditory Protection | resolved strength name |
| AnalogX | resolved mode name |
| Speaker Optimization | localized enabled/disabled state |

All summaries use one line; no section gains a second nested card or hidden interaction.

- [ ] **Step 3: Raise summary coverage assertion**

After every `ViperEffectCard` call supplies a summary, assert at least 24 `summary =` occurrences in `EffectSectionsMaterialPolicyTest`.

- [ ] **Step 4: Sync and verify remotely**

```bash
rsync -azR -e 'ssh -p 8022' \
  "app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt" \
  "app/src/main/res/values/strings.xml" \
  "app/src/main/res/values-zh-rCN/strings.xml" \
  "app/src/main/res/values-ru/strings.xml" \
  "app/src/test/java/com/llsl/viper4android/ui/screens/main/EffectSectionsMaterialPolicyTest.kt" \
  10645@localhost:~/ViPER4Android/
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --tests "*EffectSectionsMaterialPolicyTest" :app:compileDebugKotlin --no-daemon'
```

Expected: all sections compile with localized summary values.

### Task 8: Full Remote Verification And APK Installation

**Files:**
- Verify all files changed in Tasks 1-7.

**Interfaces:**
- Consumes the complete feature.

- [ ] **Step 1: Sync every intended changed file**

Use `rsync -azR -e 'ssh -p 8022'` with the exact source and test files from Tasks 1-7 plus the three locale `strings.xml` files. Do not sync unrelated dirty files.

- [ ] **Step 2: Run the complete remote unit suite**

```bash
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew :app:testDebugUnitTest --stacktrace --no-daemon'
```

Expected: `BUILD SUCCESSFUL` and no failed tests.

- [ ] **Step 3: Build the remote debug APK**

```bash
ssh -p 8022 10645@localhost \
  'cd "$HOME/ViPER4Android" && bash ./gradlew assembleDebug --stacktrace --no-daemon'
```

Expected: `~/ViPER4Android/app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 4: Install only the verified APK**

```bash
ssh -p 8022 10645@localhost '
  su -c "cp \"$HOME/ViPER4Android/app/build/outputs/apk/debug/app-debug.apk\" /data/local/tmp/viper4android-card-preview-debug.apk; \
  chown shell:shell /data/local/tmp/viper4android-card-preview-debug.apk; \
  chmod 0644 /data/local/tmp/viper4android-card-preview-debug.apk; \
  pm install -r /data/local/tmp/viper4android-card-preview-debug.apk; \
  rm -f /data/local/tmp/viper4android-card-preview-debug.apk"
'
```

Expected: `Success`.

- [ ] **Step 5: Inspect final source and installed package evidence**

Run locally:

```bash
git diff --check
git status --short
```

Run remotely:

```bash
ssh -p 8022 10645@localhost \
  'su -c "dumpsys package com.llsl.viper4android | toybox grep -E \"versionCode=|versionName=|lastUpdateTime=\""'
```

Expected: no whitespace errors, unrelated changes remain untouched, and package update time reflects the new install.

## Self-Review

- Spec coverage: compact card header, one-line summaries, 85dp curves, explicit editor rows, preview setting, three locales, remote tests, APK build, and install each have a task.
- Deferred-work scan: no unspecified implementation markers remain.
- Type consistency: `ViperEffectCard`, `ViperCurvePreview`, `ViperEditorRow`, and summary helper names are identical across producer and consumer tasks.
- Scope: DSP state, persistence, native driver, full editor behavior, and MiuiX library source remain unchanged.

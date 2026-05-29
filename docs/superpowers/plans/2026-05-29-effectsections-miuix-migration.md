# EffectSections MiuiX Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the remaining Material3 tab, confirmation dialog, text-field dialog, and inline button usage from `EffectSections.kt` without changing audio behavior.

**Architecture:** Keep this as an incremental UI migration slice. Add a small project-local tab wrapper for MiuiX tabs, reuse existing `ViperDialog` and `ViperTextFieldDialog`, and keep all effect-state callbacks wired to the existing `MainViewModel` methods.

**Tech Stack:** Kotlin, Jetpack Compose, MiuiX KMP `0.9.0`, existing Viper wrapper components, remote Gradle verification over SSH.

---

## File Structure

- Create `app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperTabs.kt`: project-local wrapper around MiuiX `TabRowWithContour` for fixed and scrollable tab lists.
- Modify `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt`: replace Material3 `PrimaryTabRow`, `PrimaryScrollableTabRow`, `Tab`, `AlertDialog`, `OutlinedTextField`, and Material3 `TextButton` usage in the MBC, Dynamic EQ, and Dynamic System sections.
- Verify `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt`: targeted `rg` checks must show no Material3 UI imports or component names for this slice.

## Task 1: Add Viper Tabs Wrapper

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperTabs.kt`

- [ ] **Step 1: Write the wrapper**

Create `ViperTabs.kt` with this content:

```kotlin
package com.llsl.viper4android.ui.components.viper

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.TabRowWithContour

@Composable
fun ViperTabs(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return

    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex.coerceIn(tabs.indices),
        onTabSelected = onTabSelected,
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Verify wrapper compiles by API shape later**

No local unit test exists for Compose wrappers in this project. This task is verified by Task 4 remote `assembleDebug`.

## Task 2: Migrate MBC and Dynamic EQ Tabs

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt`

- [ ] **Step 1: Record the red check**

Run:

```bash
rg "PrimaryTabRow|PrimaryScrollableTabRow|\bTab\(" "app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt"
```

Expected: matches in MBC and Dynamic EQ sections before implementation.

- [ ] **Step 2: Replace MBC tab row**

Replace the `PrimaryTabRow` block in `MbcSection` with:

```kotlin
ViperTabs(
    tabs = tabNames,
    selectedTabIndex = selectedTab,
    onTabSelected = { selectedTab = it },
)
```

- [ ] **Step 3: Replace Dynamic EQ scrollable tab row**

Build `dynamicEqTabNames` from current band frequencies and use `ViperTabs`:

```kotlin
val dynamicEqTabNames = List(bandCount) { i -> formatFreq(freqs.getOrElse(i) { 1000 }) }
ViperTabs(
    tabs = dynamicEqTabNames,
    selectedTabIndex = safeTab,
    onTabSelected = { selectedTab = it },
)
```

Keep the existing explicit add control below or beside the tabs using a MiuiX `TextButton`, and add an explicit delete control for the selected band when `bandCount > 1`.

## Task 3: Migrate Dynamic EQ Delete and Dynamic System Save Actions

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt`

- [ ] **Step 1: Replace Dynamic EQ delete dialog**

Use `ViperDialog` for `deleteBandIndex >= 0`, preserving `removeDynamicEqBand(i)` and selected tab correction:

```kotlin
ViperDialog(
    show = true,
    onDismissRequest = { deleteBandIndex = -1 },
    title = stringResource(R.string.dialog_delete_band),
    confirmText = stringResource(R.string.action_delete),
    onConfirm = {
        val i = deleteBandIndex
        deleteBandIndex = -1
        viewModel.removeDynamicEqBand(i)
        if (selectedTab >= bandCount - 1) selectedTab = maxOf(0, bandCount - 2)
    },
    dismissText = stringResource(R.string.action_cancel),
    onDismiss = { deleteBandIndex = -1 },
) {
    Text("Remove ${formatFreq(freqs.getOrElse(deleteBandIndex) { 1000 })} band?")
}
```

- [ ] **Step 2: Replace Dynamic System inline Material3 buttons**

Replace Material3 `TextButton` calls in the Dynamic System save/delete/reset row with MiuiX `TextButton(text = ..., onClick = ...)`. Keep icons out of these buttons for this slice to reduce API risk; labels remain explicit.

- [ ] **Step 3: Replace Dynamic System save text field dialog**

Use `ViperTextFieldDialog` with `TextFieldValue` state. Preserve trimming and `onPresetAdd` behavior.

## Task 4: Verification

**Files:**
- Verify: changed files and full remote debug build.

- [ ] **Step 1: Run targeted Material3 check**

Run:

```bash
rg "import androidx\.compose\.material3|PrimaryTabRow|PrimaryScrollableTabRow|\bTab\(|AlertDialog|OutlinedTextField|MaterialTheme" "app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt"
```

Expected: no matches in `EffectSections.kt`.

- [ ] **Step 2: Sync changed files to remote**

Run:

```bash
ssh -p 8022 10645@localhost "mkdir -p ~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/viper ~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/screens/main" && scp -P 8022 "/root/AndroidIDEProjects/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperTabs.kt" "10645@localhost:~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperTabs.kt" && scp -P 8022 "/root/AndroidIDEProjects/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt" "10645@localhost:~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt"
```

Expected: command completes without transfer errors.

- [ ] **Step 3: Run remote build**

Run:

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Report remaining hotspots**

Run:

```bash
rg "import androidx\.compose\.material3|androidx\.compose\.material3|\bAlertDialog\b|\bOutlinedTextField\b|\bMaterialTheme\b|PrimaryTabRow|PrimaryScrollableTabRow|\bTab\(" "app/src/main/java"
```

Expected: `EffectSections.kt` is no longer listed for these slice-specific patterns; `EqGraphView.kt` and `Theme.kt` may remain for later.

## Self-Review

- Spec coverage: implements the approved `EffectSections.kt` slice only.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: `ViperTabs`, `ViperDialog`, and `ViperTextFieldDialog` signatures match current project code.
- Scope check: leaves `EqGraphView.kt` and `Theme.kt` for later dedicated slices.

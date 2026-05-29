# JDSP MiuiX Card Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the main effect list foundation into a RootlessJamesDSP-inspired MiuiX card stack without changing audio behavior.

**Architecture:** Keep all existing effect sections, state reads, and `MainViewModel` callbacks unchanged. Replace only the reusable visual primitives (`EffectSection`, `LabeledSlider`, `LabeledSwitch`, `LabeledDropdown`) so every effect card inherits the new spacing, typography, and row rhythm.

**Tech Stack:** Kotlin, Jetpack Compose, MiuiX KMP `0.9.x`, existing ViPER wrappers, remote Gradle verification over SSH.

---

## File Structure

- Modify `app/src/main/java/com/llsl/viper4android/ui/components/EffectSection.kt`: JDSP-like rounded card group, stronger header row, icon chip, switch on the right, section content spacing.
- Modify `app/src/main/java/com/llsl/viper4android/ui/components/LabeledSlider.kt`: compact preference row rhythm, label/value header, full-width MiuiX slider under the header.
- Modify `app/src/main/java/com/llsl/viper4android/ui/components/LabeledSwitch.kt`: preference row with text block and right-side MiuiX switch.
- Modify `app/src/main/java/com/llsl/viper4android/ui/components/LabeledDropdown.kt`: preference row with title/current value and explicit dropdown button.
- Verify only: `EffectSections.kt` and audio state/callback files are not touched for this slice.

## Task 1: Record Current Component State

**Files:**
- Inspect: `app/src/main/java/com/llsl/viper4android/ui/components/EffectSection.kt`
- Inspect: `app/src/main/java/com/llsl/viper4android/ui/components/LabeledSlider.kt`
- Inspect: `app/src/main/java/com/llsl/viper4android/ui/components/LabeledSwitch.kt`
- Inspect: `app/src/main/java/com/llsl/viper4android/ui/components/LabeledDropdown.kt`

- [ ] **Step 1: Run the component hotspot check**

Run:

```bash
rg "Card\(|Switch\(|Slider\(|OverlayDropdownPreference|MiuixTheme|RoundedCornerShape|padding\(" "app/src/main/java/com/llsl/viper4android/ui/components/EffectSection.kt" "app/src/main/java/com/llsl/viper4android/ui/components/LabeledSlider.kt" "app/src/main/java/com/llsl/viper4android/ui/components/LabeledSwitch.kt" "app/src/main/java/com/llsl/viper4android/ui/components/LabeledDropdown.kt"
```

Expected: current MiuiX components and spacing usages are listed for comparison.

## Task 2: Restyle EffectSection Card

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/EffectSection.kt`

- [ ] **Step 1: Implement JDSP-like card shell**

Use MiuiX `Card`, `Text`, `Switch`, and `Icon` only. Preserve this public signature exactly:

```kotlin
@Composable
fun EffectSection(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
)
```

Implementation requirements:

- Outer modifier remains `fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)`.
- Card corner radius is `20.dp` and inside padding is `0.dp`.
- Header row is clickable and toggles `onEnabledChange(!enabled)`.
- Header contains a `36.dp` rounded icon chip using primary alpha when enabled and surface variant when disabled.
- Title uses `MiuixTheme.textStyles.title4`; disabled title uses `onSurfaceVariantActions` alpha.
- Switch remains visible and explicit on the right.
- Content is shown only when enabled, with `padding(horizontal = 16.dp, vertical = 12.dp)` and `Arrangement.spacedBy(10.dp)`.

- [ ] **Step 2: Keep icons**

Do not remove or replace `androidx.compose.material.icons.*` imports in effect section callers.

## Task 3: Restyle Labeled Controls

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/LabeledSlider.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/LabeledSwitch.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/LabeledDropdown.kt`

- [ ] **Step 1: LabeledSlider row rhythm**

Keep the function signature unchanged. Layout requirements:

- Outer `Column` uses `fillMaxWidth().padding(vertical = 4.dp)`.
- Header row has title left and formatted current value right.
- Title uses `MiuixTheme.textStyles.body2`; disabled title uses `onSurfaceVariantActions` alpha.
- Value uses `body2`, `primary` when enabled, `onSurfaceVariantActions` when disabled.
- Slider is full width below the header and uses existing MiuiX `Slider` parameters.
- Preserve `formatValue`, `steps`, `valueRange`, and `enabled` behavior.

- [ ] **Step 2: LabeledSwitch row rhythm**

Keep the function signature unchanged. Layout requirements:

- Outer row uses `fillMaxWidth().padding(vertical = 6.dp)`.
- Text gets `weight(1f)` and switch stays on right.
- Title uses `body2`, enabled/disabled color logic matching slider.
- Preserve clicking behavior only if already present; do not add hidden long-press behavior.

- [ ] **Step 3: LabeledDropdown row rhythm**

Keep the function signature unchanged. Layout requirements:

- Title/current value row above or beside the dropdown, using `body2` and `onSurfaceVariantActions`.
- Dropdown remains explicit and tappable via existing MiuiX dropdown component.
- No hidden long-press actions.

## Task 4: Verification

**Files:**
- Verify changed component files and full remote debug build.

- [ ] **Step 1: Confirm no Material3 direct imports returned**

Run:

```bash
rg "import androidx\.compose\.material3|androidx\.compose\.material3|MaterialTheme|AlertDialog|OutlinedTextField|PrimaryTabRow|PrimaryScrollableTabRow" "app/src/main/java"
```

Expected: no matches.

- [ ] **Step 2: Confirm this slice did not touch effect logic files unexpectedly**

Run:

```bash
git diff -- app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt app/src/main/java/com/llsl/viper4android/ui/screens/main/MainViewModel.kt app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectStates.kt
```

Expected: no output for this slice.

- [ ] **Step 3: Sync changed files and build remotely**

Run:

```bash
ssh -p 8022 10645@localhost "mkdir -p ~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components" && scp -P 8022 "/root/AndroidIDEProjects/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/EffectSection.kt" "10645@localhost:~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/EffectSection.kt" && scp -P 8022 "/root/AndroidIDEProjects/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/LabeledSlider.kt" "10645@localhost:~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/LabeledSlider.kt" && scp -P 8022 "/root/AndroidIDEProjects/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/LabeledSwitch.kt" "10645@localhost:~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/LabeledSwitch.kt" && scp -P 8022 "/root/AndroidIDEProjects/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/LabeledDropdown.kt" "10645@localhost:~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/LabeledDropdown.kt" && ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"
```

Expected: `BUILD SUCCESSFUL`.

## Self-Review

- Spec coverage: covers the approved first visual slice only: reusable effect card and row primitives.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: public function signatures stay unchanged so call sites do not need audio-logic edits.
- Scope check: bottom bar animation, graph/no-graph mode, and effect grouping changes remain future slices.

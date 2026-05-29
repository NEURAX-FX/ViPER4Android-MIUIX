# INX Floating Bottom Bar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the two-mode ViPER bottom bar as a lightweight INX-inspired floating capsule with visible icon+text tabs and adaptive bottom insets.

**Architecture:** Keep the existing `ViperBottomBar` public API so `MainScreen` behavior and audio mode switching stay unchanged. Implement the capsule locally with Foundation + MiuiX `Text/Icon/MiuixTheme`, avoiding INX blur/liquid dependencies and avoiding Material3.

**Tech Stack:** Kotlin, Jetpack Compose Foundation, MiuiX KMP `0.9.x`, Android remote Gradle build over SSH.

---

## File Structure

- Modify `app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperBottomBar.kt`: replace MiuiX `FloatingNavigationBar` with project-local animated capsule layout.
- Verify `app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt`: no call-site changes expected.

## Task 1: Record Current Bottom Bar State

**Files:**
- Inspect: `app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperBottomBar.kt`

- [ ] **Step 1: Run the red-line structural check**

Run:

```bash
rg "FloatingNavigationBar|FloatingNavigationBarItem|NavigationBar|androidx\.compose\.material3|windowInsetsPadding|navigationBars" "app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperBottomBar.kt"
```

Expected before implementation: matches for `FloatingNavigationBar` and `FloatingNavigationBarItem`.

## Task 2: Rewrite Bottom Bar Capsule

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperBottomBar.kt`

- [ ] **Step 1: Preserve public API**

Keep `ViperBottomBar` parameters unchanged:

```kotlin
@Composable
fun ViperBottomBar(
    firstLabel: String,
    secondLabel: String,
    firstIcon: ImageVector,
    secondIcon: ImageVector,
    firstSelected: Boolean,
    secondSelected: Boolean,
    onFirstClick: () -> Unit,
    onSecondClick: () -> Unit,
    deviceName: String,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: Use adaptive bottom inset padding**

Inside `ViperBottomBar`, calculate:

```kotlin
val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
val outerBottomPadding = if (bottomPadding > 0.dp) bottomPadding + 12.dp else 20.dp
```

- [ ] **Step 3: Add optional device status pill**

When `deviceName.isNotEmpty()`, show a small centered rounded pill above the bar using `surfaceContainerHigh`, `onSurfaceVariantActions`, `body2`, and horizontal padding.

- [ ] **Step 4: Add floating capsule shell**

Create a centered `Box` or `Row` with:

- `widthIn(max = 360.dp)`
- `height(64.dp)`
- `clip(RoundedCornerShape(28.dp))`
- `background(MiuixTheme.colorScheme.surfaceContainer)`
- `shadow(10.dp, RoundedCornerShape(28.dp), ambientColor = ..., spotColor = ...)`
- `padding(4.dp)`

- [ ] **Step 5: Add animated selected pill**

Use `animateFloatAsState` with target `0f` for first selected and `1f` for second selected. Place a selected background pill behind item content by measuring two equal slots with `BoxWithConstraints` and offsetting the pill by half of available width.

- [ ] **Step 6: Add two explicit tab items**

Each item should use `Modifier.selectable(role = Role.Tab)` and show icon + label. Selected tint: `MiuixTheme.colorScheme.primary`; unselected tint: `onSurfaceVariantActions`. Text stays visible for both items.

## Task 3: Verification

**Files:**
- Verify changed bottom bar and full remote build.

- [ ] **Step 1: Run targeted bottom bar check**

Run:

```bash
rg "FloatingNavigationBar|FloatingNavigationBarItem|androidx\.compose\.material3|NavigationBarItem" "app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperBottomBar.kt"
```

Expected: no matches.

- [ ] **Step 2: Confirm call site still compiles by API shape**

Run:

```bash
rg "ViperBottomBar\(" "app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt"
```

Expected: unchanged call site in `MainScreen.kt`.

- [ ] **Step 3: Sync and build remotely**

Run:

```bash
ssh -p 8022 10645@localhost "mkdir -p ~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/viper ~/ViPER4Android/docs/superpowers/plans" && scp -P 8022 "/root/AndroidIDEProjects/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperBottomBar.kt" "10645@localhost:~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperBottomBar.kt" && scp -P 8022 "/root/AndroidIDEProjects/ViPER4Android/docs/superpowers/plans/2026-05-29-inx-floating-bottom-bar.md" "10645@localhost:~/ViPER4Android/docs/superpowers/plans/2026-05-29-inx-floating-bottom-bar.md" && ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"
```

Expected: `BUILD SUCCESSFUL`.

## Self-Review

- Spec coverage: implements the approved lightweight INX capsule version without blur/liquid dependency risk.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: `ViperBottomBar` public API stays unchanged.
- Scope check: only bottom bar visuals change; main screen behavior and audio features remain intact.

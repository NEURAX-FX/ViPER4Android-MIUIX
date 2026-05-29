# Simple Dialog Material3 Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace simple Material3 dialog surfaces in driver status and settings with project-local MiuiX-style components while preserving behavior.

**Architecture:** Use existing `ViperDialog` as the dialog shell and MiuiX/basic or project-local primitives inside it. Keep this as a small, buildable slice; do not modify MiuiX library source and do not remove Material Icons.

**Tech Stack:** Kotlin, Jetpack Compose, MiuiX KMP `0.9.0`, existing `ViperDialog`, remote Gradle verification over SSH.

---

## File Structure

- Modify `app/src/main/java/com/llsl/viper4android/ui/screens/status/DriverStatusDialog.kt`: replace `AlertDialog`, Material3 text/theme/buttons, and divider styling with `ViperDialog`, MiuiX text, MiuiX theme colors, and foundation divider.
- Modify `app/src/main/java/com/llsl/viper4android/ui/screens/settings/SettingsScreen.kt`: replace `AlertDialog`, `TextButton`, `Switch`, `OutlinedButton`, and Material3 text/theme usage with `ViperDialog`, `ViperSwitchRow`, `ViperActionRow` if present or a local foundation action row, MiuiX text, and MiuiX theme colors.
- Verification only: run the remote `assembleDebug` command after syncing changed files.

## Task 1: Migrate Driver Status Dialog

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/status/DriverStatusDialog.kt`

- [ ] **Step 1: Record the existing Material3 hotspot**

Run: `rg "androidx\.compose\.material3|AlertDialog|MaterialTheme|TextButton" app/src/main/java/com/llsl/viper4android/ui/screens/status/DriverStatusDialog.kt`

Expected: matches for `AlertDialog`, `HorizontalDivider`, `MaterialTheme`, `Text`, and `TextButton` before the migration.

- [ ] **Step 2: Replace the dialog shell**

Use `ViperDialog(show = true, onDismissRequest = onDismiss, title = stringResource(R.string.menu_driver_status), confirmText = stringResource(R.string.action_close), onConfirm = onDismiss)`.

Move the old `text` content into the `content` lambda without changing installed/not-installed behavior.

- [ ] **Step 3: Replace Material3 styling**

Use `top.yukonga.miuix.kmp.basic.Text` and `top.yukonga.miuix.kmp.theme.MiuixTheme`.

Use `MiuixTheme.textStyles.body1` for the not-found message and `MiuixTheme.textStyles.body2` for status rows. Use `MiuixTheme.colorScheme.error` and `MiuixTheme.colorScheme.onSurfaceVariantActions` for the equivalent colors.

For dividers, use a tiny foundation `Spacer` or `Box` with `height(1.dp)` and `background(MiuixTheme.colorScheme.dividerLine)` if the color is available in the current dependency; otherwise use `onSurface.copy(alpha = 0.12f)`.

- [ ] **Step 4: Verify this file no longer imports Material3 UI**

Run: `rg "androidx\.compose\.material3|AlertDialog|MaterialTheme|TextButton" app/src/main/java/com/llsl/viper4android/ui/screens/status/DriverStatusDialog.kt`

Expected: no matches.

## Task 2: Migrate Settings Dialog

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/settings/SettingsScreen.kt`

- [ ] **Step 1: Record the existing Material3 hotspot**

Run: `rg "androidx\.compose\.material3|AlertDialog|MaterialTheme|OutlinedButton|Switch|TextButton" app/src/main/java/com/llsl/viper4android/ui/screens/settings/SettingsScreen.kt`

Expected: matches for dialog, buttons, switch, text, and theme before the migration.

- [ ] **Step 2: Replace the dialog shell**

Use `ViperDialog(show = true, onDismissRequest = onDismiss, title = stringResource(R.string.menu_settings), confirmText = stringResource(R.string.action_close), onConfirm = onDismiss)`.

Move the old dialog body into the `content` lambda. Preserve all callbacks: `onAutoStartChanged`, `onGlobalModeChanged`, `onImportPreset`, `onImportKernel`, `onImportVdc`, `onDebugUnlocked`, and `onDismiss`.

- [ ] **Step 3: Replace toggle rows**

If `ViperSwitchRow` exists, use it. If it does not exist, keep `SettingsToggleRow` and implement it with a clickable `Row`, MiuiX `Text`, and MiuiX `Switch` if available in the dependency. If MiuiX has no public `Switch`, use `top.yukonga.miuix.kmp.preference.SwitchPreference` only if its API fits a standalone row without source changes.

The row must remain explicit and tappable; do not hide state changes behind a long press.

- [ ] **Step 4: Replace import buttons**

If `ViperActionRow` exists, use it for preset/kernel/VDC import actions. If it does not exist, create local `SettingsActionRow` in this file with a foundation `Row`, `clickable`, `fillMaxWidth`, vertical padding, and MiuiX `Text` colored with `MiuixTheme.colorScheme.primary`.

Preserve labels `settings_import_preset`, `settings_import_kernel`, and `settings_import_vdc`.

- [ ] **Step 5: Replace text/theme and dividers**

Use MiuiX `Text` and `MiuixTheme.textStyles`. Use the same divider helper shape as Task 1.

Driver-version debug unlocking must still increment `tapCount`, call `onDebugUnlocked()` at 7 taps, reset to 0, and show the same toast.

- [ ] **Step 6: Verify this file no longer imports Material3 UI**

Run: `rg "androidx\.compose\.material3|AlertDialog|MaterialTheme|OutlinedButton|Switch|TextButton" app/src/main/java/com/llsl/viper4android/ui/screens/settings/SettingsScreen.kt`

Expected: no matches.

## Task 3: Build Verification

**Files:**
- Verify: full project remote copy and Gradle build.

- [ ] **Step 1: Sync changed files to remote build copy**

Run: `rsync -a --delete --exclude '.gradle' --exclude 'build' --exclude 'app/build' --exclude '.git' "/root/AndroidIDEProjects/ViPER4Android/" "10645@localhost:~/ViPER4Android/" -e "ssh -p 8022"`

Expected: command completes without transfer errors.

- [ ] **Step 2: Run debug build**

Run: `ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Report remaining Material3 hotspots**

Run: `rg "androidx\.compose\.material3|MaterialTheme|AlertDialog|OutlinedTextField|OutlinedButton|NavigationBar|NavigationBarItem|PrimaryTabRow|PrimaryScrollableTabRow|TabRow|TextButton|Card\(|Switch\(|Slider\(" app/src/main/java`

Expected: `DriverStatusDialog.kt` and `SettingsScreen.kt` are no longer in the output. Other known files may remain for later migration slices.

## Self-Review

- Spec coverage: covers the approved slice, `DriverStatusDialog` and `SettingsScreen` simple dialog migration.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: uses existing `ViperDialog` parameters from the current source and current project paths.
- Scope check: focused on one incremental UI migration slice and leaves complex text-field/tab graph migrations for later.

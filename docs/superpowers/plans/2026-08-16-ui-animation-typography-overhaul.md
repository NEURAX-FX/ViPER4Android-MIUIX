# UI Animation, Typography & Material3 Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Overhaul the visual typography scale, add responsive touch feedback and smooth transition animations across all screens and components, and eliminate remaining Material3 UI components.

**Architecture:** Create a unified `ViperMotion` animation utility with spring-based press feedback (`viperBounce`) and cross-tab/expansion transitions, refactor `ViperType` into a cohesive typographic hierarchy (28sp hero to 12sp caption, eliminating 10sp text), apply `animateColorAsState` for state transitions, and replace legacy Material3 dialogs/tabs with project-local MiuiX wrappers.

**Tech Stack:** Jetpack Compose, Compose Animation (`AnimatedContent`, `animateFloatAsState`, `animateColorAsState`, `spring`), MiuiX 0.9.x, Android Gradle Plugin.

## Global Constraints

- Do not remove `androidx.compose.material.icons.*` or `ImageVector`.
- Remove/replace Material3 UI components (`AlertDialog`, `TextButton`, `OutlinedTextField`, `PrimaryScrollableTabRow`, etc.) with MiuiX or Viper wrappers.
- Do not edit MiuiX library source; use public MiuiX APIs and project-local wrappers.
- Do not drop existing audio effect features or parameters.
- Build and verify using the local or remote toolchain (`./gradlew assembleDebug`).

---

### Task 1: Typography Hierarchy and Color System Modernization

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/theme/Type.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/theme/Theme.kt`

**Interfaces:**
- Produces: `ViperType` with:
  - `heroTitle`: 28.sp, Bold, tracking -0.5.sp
  - `pageTitle`: 22.sp, SemiBold, tracking -0.2.sp
  - `sectionTitle`: 17.sp, SemiBold
  - `cardTitle`: 15.sp, Medium
  - `body`: 14.sp, Normal
  - `caption`: 12.sp, Normal (minimum floor size)
  - `badge`: 11.sp, Medium
  - `valueNumber`: 13.sp, Medium, Monospace
- Produces: Soft ink color definitions (`ViperDarkInk`, `ViperSubtleInk`) avoiding harsh `#000000`.

- [ ] **Step 1: Update `Type.kt` with clean scale**
- [ ] **Step 2: Update `Color.kt` with balanced neutral & secondary inks**
- [ ] **Step 3: Update `Theme.kt` to expose consistent typography and colors**
- [ ] **Step 4: Verify with unit test/lint**
- [ ] **Step 5: Commit changes**

---

### Task 2: Add Motion Physics and Press Feedback (`ViperMotion`)

**Files:**
- Create: `app/src/main/java/com/llsl/viper4android/ui/theme/ViperMotion.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEffectCard.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperPowerButton.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/LabeledSwitch.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/LabeledSlider.kt`

**Interfaces:**
- Produces: `Modifier.viperBounce(onClick, enabled, targetScale)`: pointer-driven spring scale physics (0.97f on press, snappy spring release).
- Produces: `ViperMotionSpec`: shared spring & tween specs for layout expansion, alpha transitions, and color shifts.
- Implements: Animated color transitions (`animateColorAsState`) for switch row states and card headers.

- [ ] **Step 1: Create `ViperMotion.kt` with spring curves and `viperBounce` modifier**
- [ ] **Step 2: Enhance `ViperEffectCard` with spring expansion, animated header colors, and card bounce**
- [ ] **Step 3: Enhance `LabeledSwitch` and `LabeledSlider` with smooth state alpha/color tweening**
- [ ] **Step 4: Verify interactive feedback in components**
- [ ] **Step 5: Commit changes**

---

### Task 3: Tab & Screen Transition Animations

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/IemEditorScreen.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/preset/PresetDialog.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/device/DeviceDialog.kt`

**Interfaces:**
- Consumes: `ViperMotion` transition specs (`slideIntoContainer`, `fadeIn`, `fadeOut`, `AnimatedContent`).
- Implements: Smooth horizontal slide and crossfade between IEM editor tabs (Halo, Convolver, Compressor, Spatial).
- Implements: Animated transitions for preset/device dialog tab switches.

- [ ] **Step 1: Wrap `IemEditorScreen` tab content in `AnimatedContent` with directional slide/fade**
- [ ] **Step 2: Add smooth tab switching animation to `PresetDialog` and `DeviceDialog`**
- [ ] **Step 3: Enhance `MainScreen` scrolling header transition smoothness**
- [ ] **Step 4: Verify tab switching smoothness**
- [ ] **Step 5: Commit changes**

---

### Task 4: Replace Remaining Material3 UI Components

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/EffectEditorScreen.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/editor/IemEditorScreen.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt`
- Modify: `app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperDropdownRow.kt`

**Interfaces:**
- Replaces: Any lingering `androidx.compose.material3.*` UI components (such as `PrimaryScrollableTabRow`, `Tab`, `OutlinedTextField`, `TextButton`, `AlertDialog`) with MiuiX or project-local wrappers.
- Keeps: `androidx.compose.material.icons.*`.

- [ ] **Step 1: Audit and replace Material3 UI imports in editor and main screens**
- [ ] **Step 2: Replace `PrimaryScrollableTabRow` with MiuiX-styled animated tab row**
- [ ] **Step 3: Ensure all text fields and buttons use `top.yukonga.miuix.kmp` or `Viper` components**
- [ ] **Step 4: Verify zero prohibited Material3 UI imports**
- [ ] **Step 5: Commit changes**

---

### Task 5: Build Verification and Visual QA

**Files:**
- Test all UI modules across debug build.

- [ ] **Step 1: Run unit tests**
- [ ] **Step 2: Run lint check**
- [ ] **Step 3: Run assembleDebug to ensure clean build**
- [ ] **Step 4: Verify APK generation**
- [ ] **Step 5: Final commit on feature branch**

# Settings MiuiX Refactor Implementation Plan

**Goal:** Refactor the settings dialog into a clearer MiuiX-style grouped settings surface and add the approved main-screen curve preview toggle.

**Architecture:** Keep this as a small, buildable migration slice. Reuse `ViperDialog` and MiuiX public components only. Persist a simple `showCurvePreviews` preference that defaults to enabled, hide only main-screen curve previews when disabled, and keep dedicated graph/editor dialogs available.

**Tech Stack:** Kotlin, Jetpack Compose, MiuiX KMP `0.9.x`, DataStore-backed preferences, JUnit source-policy tests, Gradle verification.

## Files To Change

- Add `app/src/test/java/com/llsl/viper4android/ui/screens/settings/SettingsScreenPolicyTest.kt` for source-policy coverage.
- Modify `app/src/main/java/com/llsl/viper4android/ui/screens/main/MainViewModel.kt` to expose and persist `showCurvePreviews`.
- Modify `app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt` to collect the preference and pass it to settings/effect list.
- Modify `app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt` to gate main-screen EQ curve preview only.
- Modify `app/src/main/java/com/llsl/viper4android/ui/screens/settings/SettingsScreen.kt` to use grouped MiuiX cards and explicit rows.
- Modify `app/src/main/res/values*/strings.xml` to add settings group labels and the curve preview toggle label.

## Tasks

1. Write failing source-policy tests for curve preview preference wiring and settings page grouping.
2. Verify the new tests fail against the current implementation.
3. Add `PREF_SHOW_CURVE_PREVIEWS`, `showCurvePreviews`, loading with default `true`, and `setShowCurvePreviews` persistence.
4. Pass `showCurvePreviews` through `MainScreen`, `SettingsDialog`, `EffectList`, and `EqualizerSection`.
5. Refactor `SettingsScreen.kt` into grouped cards: playback, display, files, and about.
6. Add localized strings in English, Simplified Chinese, and Russian.
7. Run targeted tests and assemble/build verification.

## Design Notes

- The settings dialog remains an overlay dialog for this slice; full-screen settings can be a later navigation change.
- No audio effect behavior changes are allowed.
- Hidden debug unlock remains on the driver version row because it is a developer shortcut, not a required user operation.
- Resource management changes remain a separate slice; this pass keeps import actions explicit.

## Acceptance Criteria

- Main-screen EQ curve preview is visible by default.
- Settings exposes a visible toggle for main-screen curve previews.
- Turning the toggle off hides the main EQ preview in the effect list but does not remove the EQ edit dialog path from graph-capable screens.
- Settings are visually grouped with MiuiX cards and dividers rather than a flat mixed list.
- New strings exist in all current locales.
- Targeted tests and build verification pass or any environment blocker is documented.

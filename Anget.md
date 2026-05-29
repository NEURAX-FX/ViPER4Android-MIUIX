# ViPER4Android Agent Notes

This file captures the current migration intent so future coding agents can continue the work without re-litigating the design direction.

## Primary Goal

Rewrite the existing UI step by step into a consistent MiuiX-style interface. This is not a mechanical Material3-to-MiuiX replacement. The existing UI components are considered too mixed and confusing, so the target is a cleaner component system with better audio-effect visibility and fewer hidden interactions.

## Current User Decisions

- Keep icons. Do not remove `androidx.compose.material.icons.*` just because Material3 UI is being removed.
- Remove/replace Material3 UI components over time, not Material Icons.
- Add support for a configurable no-graph mode as `1 + 3` from the discussion:
  - Main screen can hide curve/graph previews and show list/value controls only.
  - Settings should expose a way to enable/disable main-screen curve previews.
  - Dedicated graph/editor screens should still keep graph capabilities.
- Do not edit MiuiX library source. Use public MiuiX APIs and project-local wrappers.
- Follow the canonical MiuiX repository guidance when writing MiuiX UI code.
- Use `https://github.com/compose-miuix-ui/miuix` as the MiuiX reference source. Do not use similarly named/forked repositories as API authority.
- Before each MiuiX UI migration slice, re-read the relevant docs under the checked-out MiuiX repository, especially `docs/components/*.md` and the matching source file under `miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp`.
- Prefer component-provided adaptive behavior such as default window inset padding, `Scaffold.contentWindowInsets`, and MiuiX defaults. Do not hard-code fixed bottom/top padding when the component can handle system bars or cutouts correctly.

## Toolchain State

The project is currently being moved toward newer tooling required by MiuiX and modern Compose:

- AGP: `9.2.0`
- Kotlin: `2.3.21`
- KSP: `2.3.7`
- Compose BOM: `2026.04.01`
- Hilt: `2.59.2`
- Room: `2.8.4`
- MiuiX: `0.9.0`
- Gradle wrapper: `9.4.1`

Important local/Termux build details:

- `gradle.properties` includes `android.suppressUnsupportedCompileSdk=37`.
- `gradle.properties` includes `android.aapt2FromMavenOverride=/data/data/com.tom.rv2ide/files/home/android-sdk/build-tools/35.0.1/aapt2`.
- Verify builds via SSH when possible:
  - `ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"`
- The remote project copy may need syncing from local files before remote builds.

## MiuiX API Notes Already Discovered

Canonical reference repository:

- `https://github.com/compose-miuix-ui/miuix`
- If cloned locally for reference, prefer `/tmp/opencode/compose-miuix-ui-miuix`.
- Documentation lives in `docs/components/*.md`, `docs/guide/*.md`, and `docs/zh_CN/*`.
- API signatures must be verified against the matching source files, not guessed from memory.

MiuiX 0.9.x package locations are important:

- Theme:
  - `top.yukonga.miuix.kmp.theme.MiuixTheme`
  - `top.yukonga.miuix.kmp.theme.ThemeController`
  - `top.yukonga.miuix.kmp.theme.ColorSchemeMode`
- Preference components:
  - `top.yukonga.miuix.kmp.preference.SwitchPreference`
  - `top.yukonga.miuix.kmp.preference.OverlayDropdownPreference`
- Basic components:
   - `top.yukonga.miuix.kmp.basic.Scaffold`
   - `top.yukonga.miuix.kmp.basic.TopAppBar`
   - `top.yukonga.miuix.kmp.basic.Card`
   - `top.yukonga.miuix.kmp.basic.Slider`
   - `top.yukonga.miuix.kmp.basic.Switch`
   - `top.yukonga.miuix.kmp.basic.Text`
   - `top.yukonga.miuix.kmp.basic.TextField`
   - `top.yukonga.miuix.kmp.basic.Button`
   - `top.yukonga.miuix.kmp.basic.TextButton`
   - `top.yukonga.miuix.kmp.basic.HorizontalDivider`
   - `top.yukonga.miuix.kmp.basic.VerticalDivider`
   - `top.yukonga.miuix.kmp.basic.NavigationBar`
   - `top.yukonga.miuix.kmp.basic.NavigationBarItem`
   - `top.yukonga.miuix.kmp.basic.FloatingNavigationBar`
   - `top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem`
   - `top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode`
   - `top.yukonga.miuix.kmp.basic.TabRow`
   - `top.yukonga.miuix.kmp.basic.TabRowWithContour`
- Overlay components:
  - `top.yukonga.miuix.kmp.overlay.OverlayDialog`

Important behavior confirmed from the canonical docs:

- `Scaffold` provides `MiuixPopupHost`; overlay components such as `OverlayDialog`, `OverlayDropdownPreference`, and overlay list/dropdown components must be rendered inside a MiuiX `Scaffold` host.
- `Scaffold.contentWindowInsets` defaults to `WindowInsets.systemBars.union(WindowInsets.displayCutout)`. Prefer this over manual system bar padding unless there is a specific layout reason.
- `NavigationBar` and `FloatingNavigationBar` both have `defaultWindowInsetsPadding = true`; leave it enabled unless the app shell intentionally handles insets externally.
- `FloatingNavigationBar` is documented as icon-only visually, although its item API still requires `label` for semantics. If visible bottom-bar text is required, use `NavigationBar` with `NavigationBarDisplayMode.IconAndText` or `IconWithSelectedLabel`.
- Use MiuiX `HorizontalDivider`/`VerticalDivider` instead of hand-written 1dp divider boxes.
- MiuiX `TextButton` is allowed as a replacement for Material3 `TextButton`; the Material3 removal policy targets `androidx.compose.material3.TextButton`, not MiuiX buttons.

Do not assume old or guessed packages such as `top.yukonga.miuix.kmp.utils.ThemeController` or `top.yukonga.miuix.kmp.basic.SwitchPreference`.

## Design Direction

Prefer building project-local wrappers instead of scattering raw MiuiX calls everywhere:

- `ViperScaffold`: app shell, background, top bar, bottom navigation, insets.
- `ViperTopBar`: title and action layout.
- `ViperEffectCard`: effect module card with icon, title, summary, enable switch, expansion, and optional curve preview.
- `ViperSwitchRow`: switch preference row.
- `ViperSliderRow`: slider row with value text, unit, range, key points, and reset where useful.
- `ViperDropdownRow`: simple dropdown row.
- `ViperResourceRow`: resource picker for DDC/VDC/kernel files with explicit delete/manage actions.
- `ViperActionRow`: button-like preference row.
- `ViperDialog`: consistent confirmation/info dialog.
- `ViperTextFieldDialog`: save/rename/input dialog.
- `ViperActionSheet`: explicit action list for resource management and overflow actions.
- `ViperCurvePreview`: lightweight read-only curve preview for the main screen.

Page code should depend on project-local wrappers where practical. This keeps MiuiX API churn isolated.

## Material3 Removal Policy

Remove these Material3 UI usages over time:

- `androidx.compose.material3.MaterialTheme`
- `AlertDialog`
- `TextButton`
- `OutlinedTextField`
- `OutlinedButton`
- `NavigationBar`
- `NavigationBarItem`
- `Card`
- `Switch`
- `Slider`
- `Tab`
- `TabRow`
- `PrimaryTabRow`
- `PrimaryScrollableTabRow`

Allowed to keep:

- `androidx.compose.material.icons.*`
- `ImageVector`
- Existing icon choices for main actions and effect sections.

If a Material3 UI component has no MiuiX equivalent yet, create a project-local wrapper using Compose Foundation or minimal custom layout rather than expanding Material3 usage.

## No-Graph Mode

Initial implementation should be simple:

- Add a persisted setting like `showCurvePreviews` or `displayCurvePreviews`.
- Default can be `true` unless there is a performance reason to default false.
- Main effect cards render `ViperCurvePreview` only when this setting is enabled.
- Graph-capable editor screens remain available even when main-screen previews are disabled.
- Avoid making a full three-mode density system until the basic preview toggle is stable.

## Curve Preview Priorities

Start with read-only previews, not draggable graph editors:

1. Equalizer curve.
2. Dynamic EQ simplified response curve.
3. MBC band/crossover and compression summary.
4. Limiter/FET input-output mapping.
5. DDC/Convolver should show file/status cards, not heavy graphs.

## Resource Picker UX

Old behavior used hidden long-press deletion in dropdowns. Do not preserve that hidden interaction as the only delete path.

Target behavior:

- Click row to select resource.
- Show current resource clearly.
- Show explicit delete/manage action for non-default resources.
- Confirm destructive deletion with a MiuiX-style dialog.
- Use this for DDC/VDC and Convolver kernel files.

## Migration Order

Keep changes incremental and build after each meaningful step:

1. Stabilize build/toolchain.
2. Fix currently broken MiuiX imports/API usage.
3. Establish theme and base wrapper components.
4. Restore any functional regressions from partial conversion, especially deletable resource dropdowns.
5. Rewrite main scaffold/top bar/bottom navigation.
6. Migrate effect modules one by one:
   - Master Limiter
   - Playback Gain
   - Equalizer
   - DDC
   - Convolver
   - Field/Differential Surround
   - Bass/Clarity
   - FET
   - MBC
   - Dynamic EQ
   - Dynamic System
   - Reverberation
   - Speaker Optimization
7. Migrate dialogs:
   - Preset
   - Device
   - Settings
   - Driver Status
   - Debug Log
8. Add main-screen curve previews and the show/hide setting.
9. Remove leftover Material3 UI components while keeping icons.

## Important Implementation Rules

- Do not drop existing audio features during UI migration.
- Do not remove icons.
- Do not rely on hidden long-press actions for important operations.
- Do not perform a giant one-shot rewrite. Keep each step buildable.
- Prefer small wrappers with stable app-specific APIs.
- Use English for code names and comments.
- User-facing conversation can be Chinese.
- Run formatting/build verification before claiming success.

## Useful Checks

Search for remaining Material3 UI usage:

```bash
rg "androidx\.compose\.material3|MaterialTheme|AlertDialog|OutlinedTextField|OutlinedButton|NavigationBar|NavigationBarItem|PrimaryTabRow|PrimaryScrollableTabRow|TabRow|TextButton|Card\(|Switch\(|Slider\(" app/src/main/java
```

Search for Material Icons. These are allowed and should not be removed just for cleanup:

```bash
rg "androidx\.compose\.material\.icons|Icons\." app/src/main/java
```

Remote build command:

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"
```

# Agent Instructions

Canonical migration notes are in `Anget.md`. Read and follow `Anget.md` before making UI, build, or dependency changes in this project.

## Critical Rules

- Keep icons. Do not remove `androidx.compose.material.icons.*` just because Material3 UI is being removed.
- Remove/replace Material3 UI components over time, not Material Icons.
- Do not edit MiuiX library source. Use public MiuiX APIs and project-local wrappers.
- Do not drop existing audio features during UI migration.
- Do not rely on hidden long-press actions for important operations.
- Keep migration incremental and buildable after each meaningful step.

## Current Direction

- Rewrite the UI toward a consistent MiuiX-style interface.
- Prefer project-local wrappers such as `ViperScaffold`, `ViperTopBar`, `ViperEffectCard`, `ViperSwitchRow`, `ViperSliderRow`, `ViperDropdownRow`, `ViperResourceRow`, `ViperDialog`, and `ViperCurvePreview`.
- Add configurable no-graph mode: main screen can hide curve previews, settings exposes the toggle, dedicated graph/editor screens keep graph capabilities.
- Resource pickers should have explicit manage/delete actions instead of only hidden long-press deletion.

## Material3 Policy

Remove Material3 UI usage over time, including `MaterialTheme`, `AlertDialog`, `TextButton`, `OutlinedTextField`, `OutlinedButton`, `NavigationBar`, `NavigationBarItem`, `Card`, `Switch`, `Slider`, `Tab`, `TabRow`, `PrimaryTabRow`, and `PrimaryScrollableTabRow`.

Allowed to keep:

- `androidx.compose.material.icons.*`
- `ImageVector`
- Existing icon choices for main actions and effect sections

## MiuiX API Notes

Known MiuiX 0.9.x packages:

- `top.yukonga.miuix.kmp.theme.MiuixTheme`
- `top.yukonga.miuix.kmp.theme.ThemeController`
- `top.yukonga.miuix.kmp.theme.ColorSchemeMode`
- `top.yukonga.miuix.kmp.preference.SwitchPreference`
- `top.yukonga.miuix.kmp.preference.OverlayDropdownPreference`
- `top.yukonga.miuix.kmp.basic.Scaffold`
- `top.yukonga.miuix.kmp.basic.TopAppBar`
- `top.yukonga.miuix.kmp.basic.Card`
- `top.yukonga.miuix.kmp.basic.Slider`

Do not assume guessed package names such as `top.yukonga.miuix.kmp.utils.ThemeController` or `top.yukonga.miuix.kmp.basic.SwitchPreference`.

## Build Notes

Current intended tooling is documented in `Anget.md`. Remote build command:

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"
```

The remote project copy may need syncing before remote builds.

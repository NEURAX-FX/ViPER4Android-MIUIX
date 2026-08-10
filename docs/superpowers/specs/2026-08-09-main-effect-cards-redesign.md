# Main Effect Cards & Curve Previews Redesign

## Goal

Redesign the main screen effect cards in ViPER4Android to align with MiuiX design language while incorporating key UI layout advantages from RootlessJamesDSP (JDSP).

The redesign replaces cluttered main-screen card layouts with:

- compact card headers featuring an icon, title, and one live status-summary line;
- non-intrusive 85dp rounded curve preview surfaces (`ViperCurvePreview`) with subtle gridlines and soft translucent fills;
- clean card expansion states that keep core parameter controls tidy;
- dedicated "Open Editor ›" action rows for launching full-screen or dialog-based curve editors;
- full respect for the user's `showCurvePreviews` setting.

## Background & Problem Statement

Previously, effect cards containing response curves (Equalizer, Multiband Compressor, and Dynamic EQ) embedded heavy, standalone 180-230dp grid boxes directly into the main list. This created visual noise, broke MiuiX list alignment, caused excessive vertical scrolling, and made card interaction feel awkward.

RootlessJamesDSP uses compact preference items with short status summaries (e.g. `15-band · IIR response`), clean surface plots, and dedicated activities/dialogs for interactive tuning.

## Design

### 1. Compact Card Header & Live Summary

Each effect card in `EffectSections.kt` will use a revised header layout:

- **Icon**: 32dp rounded square with theme color tint.
- **Title**: Effect name (e.g. "Graphic Equalizer", "Convolver").
- **Summary**: One live, ellipsized summary line below the title, generated from `EffectState` (e.g., `Neurax08.wav · Wet 100% · 0.3125 ms` for Convolver; `10-band · Custom` for Equalizer).
- **Control Icons**: An explicit expand/collapse chevron and the existing MiuiX switch.

The header uses a 58dp minimum height when collapsed.

`EffectSection` gains a nullable `summary` argument. Header taps toggle expansion, switch taps only change the effect's enabled state, and the switch no longer implicitly opens or closes the card. All effect cards start collapsed unless a concrete workflow requires otherwise; expansion remains saved with `rememberSaveable`.

### 2. Integrated Non-Intrusive Curve Preview Surface (`ViperCurvePreview`)

Create the project-local `ViperCurvePreview` composable for Equalizer, Multiband Compressor, and Dynamic EQ. Dedicated editors continue using the full `VstResponseGraph`; only main-screen previews use the compact component.

- Height is reduced to **85dp**.
- Heavy dark background borders and sharp grid lines are removed.
- Grid lines use **10% opacity** reference lines (`rgba(255,255,255,0.1)` in dark mode).
- Curves render with 2.0dp anti-aliased stroke and soft vertical translucent gradients.
- Corners are rounded (12dp radius) to blend seamlessly into the MiuiX card container.
- Preview handles and grid labels are omitted; full handles remain available in the dedicated editor.
- The preview reuses the existing driver-derived graph models and does not recalculate DSP curves.
- When `showCurvePreviews` is disabled in settings, the 85dp curve surface is hidden, leaving only scalar controls and the editor link.

### 3. Editor Link Action Row

At the bottom of Equalizer, Multiband Compressor, and Dynamic EQ cards when expanded, a new project-local `ViperEditorRow` is rendered:

- Icon: Existing Material curve/equalizer icon; icons remain allowed by project policy.
- Text: Localized action text (e.g. "Open Equalizer Editor", "Open Multiband Compressor Editor").
- Action: Launches the dedicated full-screen editor or dialog.

The row is always present regardless of `showCurvePreviews`, so editor access never depends on a hidden graph or long-press gesture.

### 4. Live Summary Rules

| Effect | Summary Format | Example |
| :--- | :--- | :--- |
| **Convolver** | `{kernelFile} · Wet {wet}% · {delay} ms` | `Neurax08.wav · Wet 100% · 0.3125 ms` |
| **Equalizer** | `{bands}-band · {presetName}` | `10-band · Custom` |
| **Multiband Compressor** | `{bands}-band · {mode}` | `4-band · Dynamic` |
| **Dynamic EQ** | `{bands}-band · {mode}` | `4-band · Dynamic` |
| **Reverb** | `{roomSize}m² · Damp {damp}%` | `50m² · Damp 20%` |
| **Bass** | `{mode} · {gain} dB` | `Sub-Bass · 3.5 dB` |
| **Clarity** | `{mode} · {clipping} dB` | `Natural · -0.5 dB` |
| **Output** | `{gain} dB · Pan {pan}` | `0.0 dB · 50:50` |

Other cards use the same rule: show two or three existing scalar values that best identify the active configuration. Resource-backed cards display only the file basename, never a full path. Missing resources use the localized `None` label. Long summaries are restricted to one line with ellipsis.

## Localization

Add new strings to `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rCN/strings.xml`, and `app/src/main/res/values-ru/strings.xml`:

- `action_open_eq_editor`: "Open Equalizer Editor", "打开图形均衡器调音台", "Открыть редактор эквалайзера"
- `action_open_mb_editor`: "Open Multiband Compressor View", "进入多段压缩全屏视图", "Открыть редактор мультиполосного компрессора"
- `action_open_dyn_eq_editor`: "Open Dynamic EQ View", "进入动态均衡全屏视图", "Открыть редактор динамического EQ"
- `summary_convolver_format`: "%1$s · Wet %2$d%% · %3$.4f ms"

## Testing & Verification Strategy

1. **Unit Tests**:
   - Test summary formatter functions in `EffectSectionSummaryTest.kt`, including empty resources, extreme values, and decimal precision.
   - Test compact curve area/path normalization and invalid-point rejection in `ViperCurvePreviewTest.kt`.
   - Extend `EffectSectionsMaterialPolicyTest` to require `ViperCurvePreview` and project-local editor rows while continuing to reject Material3 card, switch, slider, tab, and button imports.
2. **Remote Compose Verification**:
   - Sync changed files to `~/ViPER4Android` on the remote Android environment.
   - Run `bash ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --no-daemon` remotely.
3. **Remote APK Verification**:
   - Run `bash ./gradlew assembleDebug --stacktrace --no-daemon` remotely.
   - Install the debug APK only after the automated checks pass, then inspect the compact/collapsed and expanded states in light and dark themes.

# Viper MiuiX Theme System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the remaining Material3 theme wrapper with a MiuiX-only ViPER design-system root inspired by InstallerX's theme architecture.

**Architecture:** `ViperTheme` becomes the single app theme boundary, backed by a local theme config and MiuiX `ThemeController`. Project-level design tokens live beside the theme root so future MiuiX rewrites can share colors, corner radii, and spacing without reintroducing Material3.

**Tech Stack:** Kotlin, Jetpack Compose, MiuiX KMP `0.9.x`, AndroidX WindowCompat, remote Gradle verification over SSH.

---

## File Structure

- Modify `app/src/main/java/com/llsl/viper4android/ui/theme/Theme.kt`: remove Material3 imports and `MaterialTheme`, add `ViperThemeMode`, `ViperThemeConfig`, `ViperDesign`, system-bar handling, and MiuiX-only `ViperTheme`.
- Modify `app/src/main/java/com/llsl/viper4android/ui/theme/Color.kt`: replace legacy `md_theme_*` values with ViPER seed/design colors, or leave unused only if build/API compatibility requires it.
- Verify `app/src/main/java/com/llsl/viper4android/ui/MainActivity.kt`: keep `ViperTheme { ViperNavigation() }` unchanged.

## Task 1: Record Current Material3 Theme Usage

**Files:**
- Inspect: `app/src/main/java/com/llsl/viper4android/ui/theme/Theme.kt`
- Inspect: `app/src/main/java/com/llsl/viper4android/ui/theme/Color.kt`

- [ ] **Step 1: Run the red-line check**

Run:

```bash
rg "import androidx\.compose\.material3|MaterialTheme|dynamicLightColorScheme|dynamicDarkColorScheme|lightColorScheme|darkColorScheme" "app/src/main/java/com/llsl/viper4android/ui/theme"
```

Expected before implementation: matches in `Theme.kt` for Material3 theme imports/functions.

## Task 2: Implement MiuiX-Only Theme Root

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/theme/Theme.kt`

- [ ] **Step 1: Replace Theme.kt with MiuiX theme root**

Implement:

```kotlin
package com.llsl.viper4android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

enum class ViperThemeMode {
    System,
    Light,
    Dark,
}

data class ViperThemeConfig(
    val mode: ViperThemeMode = ViperThemeMode.System,
    val dynamicColor: Boolean = true,
    val monet: Boolean = true,
    val seedColor: Color = ViperDesign.seedColor,
    val paletteStyle: ThemePaletteStyle = ThemePaletteStyle.Expressive,
    val colorSpec: ThemeColorSpec = ThemeColorSpec.Spec2025,
)

object ViperDesign {
    val seedColor: Color = Color(0xFF7B4DFF)
    val graphPositiveColor: Color = Color(0xFF64D2FF)
    val graphNegativeColor: Color = Color(0xFFFF6B8A)
    val graphNeutralColor: Color = Color(0xFF8E8E93)
    val cardCorner = 18.dp
    val dialogCorner = 24.dp
    val rowSpacing = 12.dp
    val sectionSpacing = 16.dp
    val compactControlHeight = 40.dp
}

@Composable
fun ViperTheme(
    config: ViperThemeConfig,
    content: @Composable () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark =
        when (config.mode) {
            ViperThemeMode.System -> isSystemDark
            ViperThemeMode.Light -> false
            ViperThemeMode.Dark -> true
        }
    val colorSchemeMode = config.colorSchemeMode()
    val keyColor = rememberSystemAccentColor(config)

    val controller =
        remember(colorSchemeMode, keyColor, config.paletteStyle, config.colorSpec, isDark) {
            ThemeController(
                colorSchemeMode = colorSchemeMode,
                keyColor = keyColor,
                paletteStyle = config.paletteStyle,
                colorSpec = config.colorSpec,
            )
        }

    ViperSystemBars(isDark = isDark)

    MiuixTheme(controller = controller) {
        content()
    }
}

private fun ViperThemeConfig.colorSchemeMode(): ColorSchemeMode =
    if (dynamicColor && monet) {
        when (mode) {
            ViperThemeMode.System -> ColorSchemeMode.MonetSystem
            ViperThemeMode.Light -> ColorSchemeMode.MonetLight
            ViperThemeMode.Dark -> ColorSchemeMode.MonetDark
        }
    } else {
        when (mode) {
            ViperThemeMode.System -> ColorSchemeMode.MonetSystem
            ViperThemeMode.Light -> ColorSchemeMode.MonetLight
            ViperThemeMode.Dark -> ColorSchemeMode.MonetDark
        }
    }

@Composable
private fun rememberSystemAccentColor(config: ViperThemeConfig): Color {
    val context = LocalContext.current
    return remember(config.dynamicColor, config.monet, config.seedColor) {
        if (config.dynamicColor && config.monet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Color(context.getColor(android.R.color.system_accent1_500))
        } else {
            config.seedColor
        }
    }
}

@Composable
private fun ViperSystemBars(isDark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context as? Activity ?: return
    SideEffect {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
}
```

## Task 3: Replace Legacy Material Color File

**Files:**
- Modify: `app/src/main/java/com/llsl/viper4android/ui/theme/Color.kt`

- [ ] **Step 1: Remove `md_theme_*` values**

Replace the file with:

```kotlin
package com.llsl.viper4android.ui.theme

import androidx.compose.ui.graphics.Color

val ViperSeedPurple = Color(0xFF7B4DFF)
val ViperGraphPositive = Color(0xFF64D2FF)
val ViperGraphNegative = Color(0xFFFF6B8A)
val ViperGraphNeutral = Color(0xFF8E8E93)
```

- [ ] **Step 2: If duplicate constants appear**

If both `Color.kt` and `ViperDesign` duplicate values and build warnings are undesirable, keep `Color.kt` as the source and point `ViperDesign` at these constants.

## Task 4: Verify and Build

**Files:**
- Verify: app source tree and remote project copy.

- [ ] **Step 1: Run targeted checks**

Run:

```bash
rg "import androidx\.compose\.material3|MaterialTheme|dynamicLightColorScheme|dynamicDarkColorScheme|lightColorScheme|darkColorScheme|md_theme_" "app/src/main/java"
```

Expected: no matches.

- [ ] **Step 2: Sync changed theme files to remote**

Run:

```bash
ssh -p 8022 10645@localhost "mkdir -p ~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/theme" && scp -P 8022 "/root/AndroidIDEProjects/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/theme/Theme.kt" "10645@localhost:~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/theme/Theme.kt" && scp -P 8022 "/root/AndroidIDEProjects/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/theme/Color.kt" "10645@localhost:~/ViPER4Android/app/src/main/java/com/llsl/viper4android/ui/theme/Color.kt"
```

Expected: command completes without transfer errors.

- [ ] **Step 3: Run remote build**

Run:

```bash
ssh -p 8022 10645@localhost "cd ~/ViPER4Android && ./gradlew assembleDebug --stacktrace --no-daemon 2>&1"
```

Expected: `BUILD SUCCESSFUL`.

## Self-Review

- Spec coverage: covers MiuiX-only theme root, InstallerX-style system accent seed, ViPER seed fallback, design tokens, and system bars.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: names match MiuiX 0.9.x docs and discovered APIs.
- Scope check: focused on app theme root; settings persistence and page redesign remain future work.

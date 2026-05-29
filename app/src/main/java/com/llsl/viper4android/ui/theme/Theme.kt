package com.llsl.viper4android.ui.theme

import android.annotation.SuppressLint
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
import androidx.compose.ui.unit.Dp
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
    val seedColor: Color = ViperSeedPurple,
    val paletteStyle: ThemePaletteStyle = ThemePaletteStyle.Expressive,
    val colorSpec: ThemeColorSpec = ThemeColorSpec.Spec2025,
)

object ViperDesign {
    val seedColor: Color = ViperSeedPurple
    val graphPositiveColor: Color = ViperGraphPositive
    val graphNegativeColor: Color = ViperGraphNegative
    val graphNeutralColor: Color = ViperGraphNeutral
    val cardCorner: Dp = 18.dp
    val dialogCorner: Dp = 24.dp
    val rowSpacing: Dp = 12.dp
    val sectionSpacing: Dp = 16.dp
    val compactControlHeight: Dp = 40.dp
}

@Composable
fun ViperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    ViperTheme(
        config = ViperThemeConfig(
            mode = if (darkTheme) ViperThemeMode.Dark else ViperThemeMode.Light,
            dynamicColor = dynamicColor,
        ),
        content = content,
    )
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
    val keyColor = rememberThemeKeyColor(config)
    val controller =
        remember(colorSchemeMode, keyColor, config.colorSpec, config.paletteStyle, isDark) {
            ThemeController(
                colorSchemeMode = colorSchemeMode,
                keyColor = keyColor,
                colorSpec = config.colorSpec,
                paletteStyle = config.paletteStyle,
                isDark = isDark,
            )
        }

    ViperSystemBars(isDark = isDark)

    MiuixTheme(controller = controller) {
        content()
    }
}

private fun ViperThemeConfig.colorSchemeMode(): ColorSchemeMode =
    when (mode) {
        ViperThemeMode.System -> ColorSchemeMode.MonetSystem
        ViperThemeMode.Light -> ColorSchemeMode.MonetLight
        ViperThemeMode.Dark -> ColorSchemeMode.MonetDark
    }

@Composable
private fun rememberThemeKeyColor(config: ViperThemeConfig): Color {
    val context = LocalContext.current
    return remember(config.dynamicColor, config.seedColor) {
        if (config.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            systemAccentColor(context as? Activity, config.seedColor)
        } else {
            config.seedColor
        }
    }
}

@SuppressLint("InlinedApi")
private fun systemAccentColor(activity: Activity?, fallback: Color): Color =
    if (activity != null) {
        Color(activity.getColor(android.R.color.system_accent1_500))
    } else {
        fallback
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

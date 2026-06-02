package com.llsl.viper4android.ui.screens.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SettingsScreenPolicyTest {
    @Test
    fun curvePreviewPreferenceDefaultsOnAndIsWiredThroughMainScreen() {
        val viewModel = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/MainViewModel.kt")
        val mainScreen = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt")
        val effectSections = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt")

        assertTrue("MainViewModel should define the persisted curve preview key", "PREF_SHOW_CURVE_PREVIEWS" in viewModel)
        assertTrue("Curve previews should default to visible", "MutableStateFlow(true)" in viewModel)
        assertTrue(
            "Curve preview preference should load with default true",
            "getBooleanPreference(PREF_SHOW_CURVE_PREVIEWS, true)" in viewModel,
        )
        assertTrue("MainViewModel should expose showCurvePreviews state", "val showCurvePreviews" in viewModel)
        assertTrue("MainViewModel should persist curve preview changes", "fun setShowCurvePreviews(enabled: Boolean)" in viewModel)
        assertTrue(
            "MainViewModel should save curve preview changes",
            "repository.setBooleanPreference(PREF_SHOW_CURVE_PREVIEWS, enabled)" in viewModel,
        )

        assertTrue("MainScreen should collect showCurvePreviews", "val showCurvePreviews by viewModel.showCurvePreviews.collectAsStateWithLifecycle()" in mainScreen)
        assertTrue("MainScreen should pass showCurvePreviews to EffectList", "showCurvePreviews = showCurvePreviews" in effectListCall(mainScreen))
        assertTrue("MainScreen should pass showCurvePreviews to SettingsDialog", "showCurvePreviews = showCurvePreviews" in settingsDialogCall(mainScreen))
        assertTrue(
            "SettingsDialog should persist curve preview changes through MainViewModel",
            "onShowCurvePreviewsChanged = viewModel::setShowCurvePreviews" in settingsDialogCall(mainScreen),
        )

        assertTrue("EffectList should accept showCurvePreviews", "showCurvePreviews: Boolean" in mainScreen)
        assertTrue(
            "EffectList should pass showCurvePreviews to EqualizerSection",
            "EqualizerSection(state, viewModel, isSpkMode, showCurvePreview = showCurvePreviews)" in mainScreen,
        )
        assertTrue("EqualizerSection should accept a preview flag", "showCurvePreview: Boolean = true" in effectSections)
        assertTrue(
            "EQ curve should be hidden when main-screen previews are disabled",
            "if (showCurvePreview && bands.size >= bandCount)" in effectSections,
        )
    }

    @Test
    fun settingsDialogUsesGroupedMiuixCardsAndExplicitRows() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/settings/SettingsScreen.kt")

        assertTrue("Settings dialog should use MiuiX cards for groups", "import top.yukonga.miuix.kmp.basic.Card" in source)
        assertTrue("Settings dialog should use MiuiX horizontal dividers", "import top.yukonga.miuix.kmp.basic.HorizontalDivider" in source)
        assertTrue("Settings dialog should define grouped cards", "private fun SettingsGroupCard(" in source)
        assertTrue("Settings dialog should define visible section titles", "private fun SettingsSectionTitle(" in source)
        assertTrue("Settings dialog should use explicit switch rows", "private fun SettingsSwitchRow(" in source)
        assertTrue("Settings dialog should expose display group", "settings_display_section" in source)
        assertTrue("Settings dialog should expose curve preview toggle", "settings_show_curve_previews" in source)
        assertTrue("Settings dialog should expose about group", "settings_about_section" in source)
        assertFalse("Settings dialog should not keep hand-written divider boxes", "private fun SettingsDivider" in source)
    }

    @Test
    fun settingsStringsExistInAllLocales() {
        val locales = listOf(
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values-zh-rCN/strings.xml",
            "app/src/main/res/values-ru/strings.xml",
        )

        locales.forEach { path ->
            val source = readSource(path)
            assertTrue("$path should define playback settings section", "name=\"settings_playback_section\"" in source)
            assertTrue("$path should define display settings section", "name=\"settings_display_section\"" in source)
            assertTrue("$path should define about settings section", "name=\"settings_about_section\"" in source)
            assertTrue("$path should define curve preview toggle", "name=\"settings_show_curve_previews\"" in source)
        }
    }

    private fun effectListCall(source: String): String = sectionBetween(source, "EffectList(", ")\n\n            ViperBottomBar(")

    private fun settingsDialogCall(source: String): String = sectionBetween(source, "SettingsDialog(", ")\n        }")

    private fun sectionBetween(
        source: String,
        start: String,
        end: String,
    ): String =
        source.substringAfter(start, missingDelimiterValue = "")
            .substringBefore(end, missingDelimiterValue = "")

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }
}

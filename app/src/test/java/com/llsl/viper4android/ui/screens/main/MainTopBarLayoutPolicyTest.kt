package com.llsl.viper4android.ui.screens.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class MainTopBarLayoutPolicyTest {
    @Test
    fun mainScreenMovesPrimaryActionsOutOfCollapsedTopBar() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt")

        assertTrue("MainScreen should pass active device name to ViperTopBar", "deviceName = state.activeDeviceName" in source)
        assertTrue("MainScreen should pass ViperFX as the expanded title", "largeTitle = stringResource(R.string.app_expanded_name)" in source)
        assertFalse("MainScreen should not pass expanded actions into the top bar", "expandedActions =" in source)
        assertTrue("MainScreen should put primary actions in the effect list header", "headerContent =" in source)
        assertTrue("Header should expose preset entry", "MainActionButton(Icons.Default.LibraryMusic, stringResource(R.string.menu_presets)" in source)
        assertTrue("Header should expose devices entry", "MainActionButton(Icons.Default.Devices, stringResource(R.string.menu_devices)" in source)
        assertTrue("Header should expose driver status entry", "MainActionButton(Icons.Default.Info, stringResource(R.string.menu_driver_status)" in source)
        assertTrue("Header should expose settings entry", "MainActionButton(Icons.Default.Settings, stringResource(R.string.menu_settings)" in source)
        assertFalse("Header should not expose debug entry", headerContent(source).contains("debug_log_title"))
    }

    @Test
    fun topBarOnlyShowsDebugShortcutWhenDebugModeIsEnabled() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt")
        val topBar = sectionBetween(source, "ViperTopBar(", ") { paddingValues ->")

        assertTrue("MainScreen should define top bar actions", "actions =" in source)
        assertTrue("Debug icon should remain gated by debugMode", "if (debugMode)" in topBar)
        assertTrue("Top bar should expose debug log", "contentDescription = stringResource(R.string.debug_log_title)" in topBar)
        assertTrue("Top bar should open debug log", "showDebugLog = true" in topBar)
        assertFalse("Top bar should not expose preset shortcut", "menu_presets" in topBar)
        assertFalse("Top bar should not expose overflow menu", "action_more" in topBar || "MoreVert" in topBar || "WindowListPopup" in topBar)
        assertFalse("MainScreen should not keep overflow menu state", "showTopBarMenu" in source || "TopBarMenuAction" in source)
    }

    @Test
    fun viperTopBarMorphsViperFxIntoCollapsedAppName() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperTopBar.kt")

        assertTrue("ViperTopBar should accept a distinct expanded title", "largeTitle: String" in source)
        assertTrue("Custom top bar should restore scroll-driven title morphing", "collapsedFraction" in source)
        assertTrue("Expanded ViperFX title should use the semantic display style", "style = ViperType.display" in source)
        assertTrue("Collapsed app title should use the semantic title style", "style = ViperType.title" in source)
        assertTrue("Top bar actions should fade out while collapsing", "actionsAlpha" in source)
        assertFalse("ViperTopBar should not use SmallTopAppBar", "SmallTopAppBar(" in source)
        assertFalse("ViperTopBar should not use the split large-title TopAppBar", "TopAppBar(" in source)
        assertFalse("ViperTopBar should not use a separate visibility-switched title", "AnimatedVisibility" in source)
    }

    @Test
    fun bottomBarNoLongerAcceptsDeviceName() {
        val bottomBar = readSource("app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperBottomBar.kt")
        val mainScreen = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt")

        assertFalse("ViperBottomBar should not accept deviceName", "deviceName:" in bottomBar)
        assertFalse("MainScreen should not pass deviceName to bottom bar", "deviceName = state.activeDeviceName" in viperBottomBarCall(mainScreen))
    }

    @Test
    fun automaticDeviceArchitectureDoesNotRenderLegacyModeCapsule() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt")
        val scaffoldCall = sectionBetween(source, "ViperScaffold(", ") { paddingValues ->")

        assertFalse("MainScreen should not use Scaffold bottomBar for a legacy mode capsule", "bottomBar =" in scaffoldCall)
        assertFalse("Automatic device routing should not render ViperBottomBar", "ViperBottomBar(" in source)
        assertTrue("Active device should remain visible in the top bar", "deviceName = state.activeDeviceName" in source)
        assertFalse("MainScreen should not branch effects by the removed fxType state", "state.fxType" in source)
    }

    @Test
    fun mainDialogsRemainInsideMiuixScaffoldPopupHostScope() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt")

        listOf(
            "showPresetDialog",
            "showDriverStatusDialog",
            "showDeviceDialog",
            "showSettingsDialog",
        ).forEach { state ->
            assertTrue(
                "$state should be rendered inside ViperScaffold so MiuiX can find its popup host",
                "\n        if ($state)" in source,
            )
            assertFalse(
                "$state must not be rendered outside ViperScaffold's popup host scope",
                "\n    if ($state)" in source,
            )
        }
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)

    private fun headerContent(source: String): String = sectionBetween(source, "headerContent = {", "state = state")

    private fun viperBottomBarCall(source: String): String = sectionBetween(source, "ViperBottomBar(", ")\n        }")

    private fun sectionBetween(source: String, start: String, end: String): String =
        source.substringAfter(start, missingDelimiterValue = "")
            .substringBefore(end, missingDelimiterValue = "")

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }
}

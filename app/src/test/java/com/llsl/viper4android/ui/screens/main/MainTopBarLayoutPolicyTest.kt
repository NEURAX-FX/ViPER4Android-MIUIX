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
        assertFalse("MainScreen should not pass expanded actions into the top bar", "expandedActions =" in source)
        assertTrue("MainScreen should put primary actions in the effect list header", "headerContent =" in source)
        assertTrue("Header should expose preset entry", "MainActionButton(" in source && "contentDescription = stringResource(R.string.menu_presets)" in source)
        assertTrue("Header should expose devices entry", "contentDescription = stringResource(R.string.menu_devices)" in source)
        assertTrue("Header should expose driver status entry", "contentDescription = stringResource(R.string.menu_driver_status)" in source)
        assertTrue("Header should expose settings entry", "contentDescription = stringResource(R.string.menu_settings)" in source)
        assertFalse("Header should not expose debug entry", headerContent(source).contains("debug_log_title"))
    }

    @Test
    fun collapsedTopBarOnlyShowsDebugShortcutWhenDebugModeIsEnabled() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt")
        val topBar = sectionBetween(source, "ViperTopBar(", ") { paddingValues ->")

        assertTrue("MainScreen should define collapsed top bar actions", "collapsedActions =" in source)
        assertTrue("Debug icon should remain gated by debugMode", "if (debugMode)" in topBar)
        assertTrue("Collapsed top bar should expose debug log", "contentDescription = stringResource(R.string.debug_log_title)" in topBar)
        assertTrue("Collapsed top bar should open debug log", "showDebugLog = true" in topBar)
        assertFalse("Collapsed top bar should not expose preset shortcut", "menu_presets" in topBar)
        assertFalse("Collapsed top bar should not expose overflow menu", "action_more" in topBar || "MoreVert" in topBar || "WindowListPopup" in topBar)
        assertFalse("MainScreen should not keep overflow menu state", "showTopBarMenu" in source || "TopBarMenuAction" in source)
    }

    @Test
    fun viperTopBarDoesNotToggleBottomContentFromScrollFraction() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperTopBar.kt")

        assertFalse("ViperTopBar should not read collapsedFraction in composition", "collapsedFraction" in source)
        assertFalse("ViperTopBar should not derive action-row visibility from scroll", "derivedStateOf" in source)
        assertFalse("ViperTopBar should not use dynamic bottomContent for the primary action row", "bottomContent =" in source)
    }

    @Test
    fun bottomBarNoLongerAcceptsDeviceName() {
        val bottomBar = readSource("app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperBottomBar.kt")
        val mainScreen = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt")

        assertFalse("ViperBottomBar should not accept deviceName", "deviceName:" in bottomBar)
        assertFalse("MainScreen should not pass deviceName to bottom bar", "deviceName = state.activeDeviceName" in viperBottomBarCall(mainScreen))
    }

    @Test
    fun bottomBarFloatsAsContentOverlayInsteadOfScaffoldBottomBar() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt")
        val scaffoldCall = sectionBetween(source, "ViperScaffold(", ") { paddingValues ->")
        val scaffoldContent = source.substringAfter(") { paddingValues ->", missingDelimiterValue = "")

        assertFalse("MainScreen should not use Scaffold bottomBar for the floating capsule", "bottomBar =" in scaffoldCall)
        assertTrue("Main content should own the floating bottom capsule overlay", "ViperBottomBar(" in scaffoldContent)
        assertTrue("Floating capsule should be aligned to the content bottom", "contentAlignment = Alignment.BottomCenter" in source)
        assertTrue("Effect list should reserve space behind the floating capsule", "bottomContentPadding =" in source)
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

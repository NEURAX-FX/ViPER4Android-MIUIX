package com.llsl.viper4android.ui.screens.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class DebugLogUiPolicyTest {
    @Test
    fun debugLogUsesMiuixSearchBarInsteadOfStandaloneTextField() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/debug/DebugLogScreen.kt")

        assertFalse("DebugLogScreen should not import the standalone MiuiX TextField", "import top.yukonga.miuix.kmp.basic.TextField" in source)
        assertTrue("DebugLogScreen should import MiuiX SearchBar", "import top.yukonga.miuix.kmp.basic.SearchBar" in source)
        assertTrue("DebugLogScreen should import MiuiX InputField", "import top.yukonga.miuix.kmp.basic.InputField" in source)
        assertTrue("DebugLogScreen should render DebugLogSearchBar", "DebugLogSearchBar(" in source)
        assertTrue("Search should clear focus instead of expanding into a result panel", "onExpandedChange = {}" in source)
    }

    @Test
    fun debugLogGroupsFiltersAsLeftTitleAndRightPills() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/debug/DebugLogScreen.kt")
        val group = source.substringAfter("private fun DebugFilterGroup(", missingDelimiterValue = "")
            .substringBefore("@Composable\nprivate fun DebugFilterChip", missingDelimiterValue = "")

        assertTrue("DebugLogScreen should render grouped filter rows", "DebugFilterGroup(" in source)
        assertTrue("Filter group should lay out title and chips in one row", "Row(" in group)
        assertFalse("Filter title should not sit above chips in a column", "Column(" in group)
        assertTrue("Filter title should occupy a fixed left column", ".weight(0.22f)" in group)
        assertTrue("Filter chip strip should occupy the remaining right side", ".weight(0.78f)" in group)
        assertTrue("Right-side chips should start after the title", "horizontalArrangement = Arrangement.spacedBy(6.dp)" in group)
        assertTrue("Source filters should have an explicit section label", "title = stringResource(R.string.debug_filter_source)" in source)
        assertTrue("Level filters should have an explicit section label", "title = stringResource(R.string.debug_filter_level)" in source)
        assertTrue("Category filters should have an explicit section label", "title = stringResource(R.string.debug_filter_category)" in source)
        assertTrue("Debug chips should use pill clipping", ".clip(CircleShape)" in source)
    }

    @Test
    fun debugLogListHasCardChromeAndEmptyState() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/debug/DebugLogScreen.kt")

        assertTrue("Log list should be wrapped in a card-like container", "DebugLogList(" in source)
        assertTrue("Log list should have a rounded clipped surface", ".clip(RoundedCornerShape(18.dp))" in source)
        assertTrue("Log list should use a MiuiX surface color", "MiuixTheme.colorScheme.surfaceContainer" in source)
        assertTrue("Log list should render an empty state", "R.string.debug_log_empty" in source)
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }
}

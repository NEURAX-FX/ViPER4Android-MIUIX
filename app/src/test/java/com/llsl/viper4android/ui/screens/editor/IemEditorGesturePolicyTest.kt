package com.llsl.viper4android.ui.screens.editor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class IemEditorGesturePolicyTest {
    @Test
    fun editorUsesPagerForHorizontalSwipeAndKeepsVerticalPageScrolling() {
        val source = readSource(
            "app/src/main/java/com/llsl/viper4android/ui/screens/editor/IemEditorScreen.kt",
        )

        assertTrue("IEM tabs should be backed by HorizontalPager", "HorizontalPager(" in source)
        assertTrue("IEM pager should expose user swiping", "userScrollEnabled = true" in source)
        assertTrue("Each IEM page should remain vertically scrollable", ".verticalScroll(" in source)
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }
}

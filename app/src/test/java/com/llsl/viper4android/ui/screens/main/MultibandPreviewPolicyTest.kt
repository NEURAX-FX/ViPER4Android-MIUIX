package com.llsl.viper4android.ui.screens.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class MultibandPreviewPolicyTest {
    @Test
    fun multibandCardRespectsPreviewSettingAndKeepsExplicitEditorAccess() {
        val mainScreen = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/MainScreen.kt")
        val sections = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt")

        assertTrue("MainScreen must pass the preview preference", "showCurvePreview = showCurvePreviews" in mainScreen)
        assertTrue("The section must accept the preview preference", "showCurvePreview: Boolean = true" in sections)
        assertTrue("Preview mode must be explicit", "if (showCurvePreview)" in sections)
        assertTrue("No-graph mode must keep an edit action", "PreviewEditAction(onClick = onOpenEditor)" in sections)
    }

    @Test
    fun multibandCardContainsNoDeadInlineEditorOrThresholdFrequencyHandles() {
        val sections = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt")

        assertFalse("Dead inline editor must be removed", "val multibandCompressorVals" in sections)
        assertFalse("Frequency preview must not contain threshold handles", "threshold-" in sections)
        assertFalse("Dead Material3 tab row must be removed", "PrimaryTabRow" in sections)
        assertFalse("Dead Material3 tab must be removed", "import androidx.compose.material3.Tab" in sections)
    }

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)
}

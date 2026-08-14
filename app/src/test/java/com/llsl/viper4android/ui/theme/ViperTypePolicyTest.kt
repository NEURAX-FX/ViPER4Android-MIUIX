package com.llsl.viper4android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ViperTypePolicyTest {
    @Test
    fun typeScaleDefinesSemanticGroups() {
        assertEquals(40f, ViperType.display.fontSize.value)
        assertEquals(20f, ViperType.title.fontSize.value)
        assertEquals(13f, ViperType.section.fontSize.value)
        assertEquals(15f, ViperType.body.fontSize.value)
        assertEquals(12f, ViperType.caption.fontSize.value)
        assertEquals(15f, ViperType.value.fontSize.value)
        assertEquals(10f, ViperType.micro.fontSize.value)
        assertEquals(10f, ViperType.mono.fontSize.value)
        assertTrue(ViperDesign.type === ViperType)
    }

    @Test
    fun themedTextUsesViperTypeInsteadOfRawSizes() {
        val files = listOf(
            "app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperTopBar.kt",
            "app/src/main/java/com/llsl/viper4android/ui/screens/settings/SettingsDialog.kt",
            "app/src/main/java/com/llsl/viper4android/ui/screens/debug/DebugLogDialog.kt",
            "app/src/main/java/com/llsl/viper4android/ui/components/LabeledSlider.kt",
            "app/src/main/java/com/llsl/viper4android/ui/components/LabeledSwitch.kt",
            "app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEffectCard.kt",
            "app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt",
            "app/src/main/java/com/llsl/viper4android/ui/components/viper/VstResponseGraph.kt",
        )
        files.forEach { path ->
            val source = readSource(path)
            assertTrue("$path should use ViperType", "ViperType." in source)
            assertFalse("$path should not hard-code fontSize", Regex("""fontSize\s*=\s*\d+\.sp""").containsMatchIn(source))
        }
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }
}

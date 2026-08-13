package com.llsl.viper4android.ui.screens.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class IemEditorLocalizationPolicyTest {
    @Test
    fun editorHasNoHardcodedEnglishControlLabels() {
        val source = read("app/src/main/java/com/llsl/viper4android/ui/screens/editor/IemEditorScreen.kt")
        val labels =
            listOf(
                "Azimuth", "Elevation", "Sample-wise Panning", "Spatial Mode",
                "Delta Time", "Grain Length", "Source Probability", "Runtime only",
                "Invert Overall Rotation", "Effective Order", "Headphone EQ",
                "Latency Profile", "Actual Latency", "Active Grains",
                "Queue Underflow / Overflow", "Grain Exhaustion", "Limiter Reduction",
                "Fault / Preparation",
            )

        labels.forEach { label ->
            assertFalse("Hardcoded IEM editor label: $label", "\"$label\"" in source)
        }
    }

    @Test
    fun everyIemEditorResourceHasChineseAndRussianTranslations() {
        val defaultStrings = read("app/src/main/res/values/strings.xml")
        val chineseStrings = read("app/src/main/res/values-zh-rCN/strings.xml")
        val russianStrings = read("app/src/main/res/values-ru/strings.xml")
        val keys = Regex("name=\"(iem_editor_[^\"]+)\"").findAll(defaultStrings).map { it.groupValues[1] }.toList()

        assertTrue("Expected IEM editor resources", keys.isNotEmpty())
        keys.forEach { key ->
            assertTrue("Missing simplified Chinese translation: $key", "name=\"$key\"" in chineseStrings)
            assertTrue("Missing Russian translation: $key", "name=\"$key\"" in russianStrings)
        }
    }

    private fun read(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }
}

package com.llsl.viper4android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class LabeledSliderPrecisionInputPolicyTest {
    @Test
    fun labeledSliderValueOpensPrecisionInputDialog() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/components/LabeledSlider.kt")

        assertTrue("LabeledSlider should track precision dialog visibility", "showPrecisionInput" in source)
        assertTrue("Value text should be clickable when slider is enabled", ".clickable(enabled = enabled)" in source)
        assertTrue("Value text should open the precision dialog", "showPrecisionInput = true" in source)
        assertFalse("Slider precision input should not use the overlay-backed ViperTextFieldDialog", "ViperTextFieldDialog(" in source)
        assertTrue("Slider precision input should render a WindowDialog", "WindowDialog(" in source)
        assertTrue("Slider precision input should import WindowDialog", "import top.yukonga.miuix.kmp.window.WindowDialog" in source)
        assertTrue("Slider precision input should use MiuiX TextField directly inside WindowDialog", "TextField(" in source)
        assertTrue("Precision dialog should use the slider label as title", "title = label" in source)
        assertTrue("Precision dialog should confirm only valid parsed input", "confirmEnabled = parsedInputValue != null" in source)
    }

    @Test
    fun precisionInputShowsRawNumberWithoutUnitButParsesPastedUnits() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/components/LabeledSlider.kt")

        assertTrue("Initial precision text should come from the raw numeric value", "initialPrecisionInput(value)" in source)
        assertTrue("Precision parsing should be implemented as a local helper", "fun parseSliderPrecisionInput(" in source)
        assertFalse("Precision input should not seed the display label with units", "TextFieldValue(valueLabel ?:" in source)

        assertEquals(42f, parseSliderPrecisionInput("42", 0f..100f))
        assertEquals(-12.5f, parseSliderPrecisionInput("-12.5 dB", -48f..0f))
        assertEquals(1.2f, parseSliderPrecisionInput("1.2x", 0f..4f))
        assertEquals(24f, parseSliderPrecisionInput("24 LUFS", 0f..240f))
        assertEquals(0f, parseSliderPrecisionInput("-200 dB", 0f..100f))
        assertEquals(100f, parseSliderPrecisionInput("120%", 0f..100f))
        assertEquals(null, parseSliderPrecisionInput("left:right", -100f..100f))
        assertEquals(null, parseSliderPrecisionInput("", 0f..100f))
    }

    @Test
    fun sliderPrecisionInputStringsExistInAllLocales() {
        val locales = listOf(
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values-zh-rCN/strings.xml",
            "app/src/main/res/values-ru/strings.xml",
        )

        locales.forEach { path ->
            val source = readSource(path)
            assertTrue("$path should define slider precision hint", "name=\"slider_precision_hint\"" in source)
            assertTrue("$path should define slider precision summary", "name=\"slider_precision_summary\"" in source)
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

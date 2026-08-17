package com.llsl.viper4android.ui.screens.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class EffectSectionsMaterialPolicyTest {
    @Test
    fun mainEffectCardsStayOnMiuixDuringUpstreamEditorMigration() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEffectCard.kt")

        assertTrue("ViperEffectCard should use the MiuiX card alias", "Card as MiuixCard" in source && "MiuixCard(" in source)
        assertTrue("ViperEffectCard should use the MiuiX switch alias", "Switch as MiuixSwitch" in source && "MiuixSwitch(" in source)
        assertFalse("Main effect cards should not use Material3 Card", "import androidx.compose.material3.Card" in source)
        assertFalse("Main effect cards should not use Material3 Switch", "import androidx.compose.material3.Switch" in source)
    }

    @Test
    fun convolverExposesEelMixAndRoutingControls() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt")

        assertTrue("Convolver should expose Wet", "Effects.convolver.wet" in source)
        assertTrue("Convolver should expose Output Gain", "Effects.convolver.outputGain" in source)
        assertTrue("Convolver should expose Routing", "Effects.convolver.routing" in source)
        assertTrue("Convolver should expose precise Cross Delay", "Effects.convolver.crossDelay100Ns" in source)
    }

    @Test
    fun compactEffectCardUsesMiuixAndSeparatesExpansionFromPower() {
        val card =
            readSource(
                "app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEffectCard.kt",
            )

        assertTrue("Card must expose summary", "summary: String?" in card)
        assertTrue("Summary must ellipsize", "TextOverflow.Ellipsis" in card && "maxLines = 1" in card)
        assertTrue("Header must own expansion", "expanded = !expanded" in card)
        assertFalse("Power switch must not mutate expansion", "expanded = checked" in card)
        assertTrue("Card must use MiuiX Card", "Card as MiuixCard" in card)
        assertTrue("Card must use MiuiX Switch", "Switch as MiuixSwitch" in card)
        assertFalse("Wrapper must not import Material3", "androidx.compose.material3" in card)
    }

    @Test
    fun editorRowIsExplicitAndMaterial3Free() {
        val row =
            readSource(
                "app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEditorRow.kt",
            )

        assertTrue("Editor row must be clickable", ".clickable(" in row)
        assertTrue("Editor row must show a chevron", "KeyboardArrowRight" in row)
        assertFalse("Editor row must not use Material3", "androidx.compose.material3" in row)
    }

    @Test
    fun mainGraphCardsUseCompactPreviewAndExplicitEditors() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt")

        assertTrue("Main cards must use compact preview", "ViperCurvePreview(" in source)
        assertTrue("Main cards must expose editor rows", "ViperEditorRow(" in source)
        assertFalse("Main cards must not render full editor graph", "VstResponseGraph(" in source)
    }

    @Test
    fun effectSectionsIsCompletelyMaterial3Free() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt")
        assertFalse("EffectSections.kt must not import any Material3 components", "androidx.compose.material3" in source)
    }

    @Test
    fun everyMainEffectCardHasAStatusSummary() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt")

        assertTrue("Every main card should pass a summary", Regex("summary\\s*=").findAll(source).count() >= 24)
    }

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)
}

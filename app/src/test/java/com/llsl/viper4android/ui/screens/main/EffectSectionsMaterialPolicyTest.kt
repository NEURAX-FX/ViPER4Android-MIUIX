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
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt")

        assertTrue("EffectSection should use the MiuiX card alias", "Card as MiuixCard" in source && "MiuixCard(" in source)
        assertTrue("EffectSection should use the MiuiX switch alias", "Switch as MiuixSwitch" in source && "MiuixSwitch(" in source)
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

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)
}

package com.llsl.viper4android.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class EffectSectionExpansionPolicyTest {
    @Test
    fun headerExpansionAndPowerSwitchStayIndependent() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEffectCard.kt")

        assertTrue("Header should own expansion", "Modifier.clickable { expanded = !expanded }" in source)
        assertTrue("Power switch should directly delegate enable state", "onCheckedChange = onEnabledChange" in source)
        assertFalse("Power switch must not mutate expansion", "expanded = checked" in source)
    }

    @Test
    fun toggleOnlySectionsStillDoNotExposeExpandableContent() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEffectCard.kt")

        assertTrue("Toggle-only sections should not make the header clickable", "if (toggleOnly) {" in source)
        assertTrue("Toggle-only sections should not render AnimatedVisibility content", "if (!toggleOnly) {" in source && "AnimatedVisibility(" in source)
    }

    @Test
    fun switchDirectlyDelegatesWithoutHiddenExpansionSideEffects() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperEffectCard.kt")

        assertTrue("Switch should directly delegate enable changes", "onCheckedChange = onEnabledChange" in source)
        assertFalse("Switch should not own an expansion handler", "onCheckedChange = { checked ->" in source)
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }
}

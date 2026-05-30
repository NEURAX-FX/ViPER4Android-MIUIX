package com.llsl.viper4android.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class EffectSectionExpansionPolicyTest {
    @Test
    fun enableSwitchExpandsAndDisablesCollapseForNonToggleOnlySections() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/components/EffectSection.kt")

        assertTrue("EffectSection should route switch changes through a local handler", "onCheckedChange = { checked ->" in source)
        assertTrue("Switch handler should preserve existing enable callback", "onEnabledChange(checked)" in source)
        assertTrue("Switch handler should expand normal sections when enabled", "expanded = checked" in source)
        assertTrue("Switch handler should skip expansion state changes for toggle-only sections", "if (!toggleOnly)" in switchHandler(source))
    }

    @Test
    fun toggleOnlySectionsStillDoNotExposeExpandableContent() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/components/EffectSection.kt")

        assertTrue("Toggle-only sections should not make the header clickable", ".then(if (toggleOnly) Modifier else Modifier.clickable" in source)
        assertTrue("Toggle-only sections should not render AnimatedVisibility content", "if (!toggleOnly) {" in source && "AnimatedVisibility(" in source)
    }

    @Test
    fun switchNoLongerDirectlyDelegatesToEnableCallback() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/components/EffectSection.kt")

        assertFalse("Switch should not directly delegate because expansion state must be updated too", "onCheckedChange = onEnabledChange" in source)
    }

    private fun switchHandler(source: String): String =
        source.substringAfter("onCheckedChange = { checked ->", missingDelimiterValue = "")
            .substringBefore("}", missingDelimiterValue = "")

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }
}

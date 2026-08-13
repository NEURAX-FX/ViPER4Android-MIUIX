package com.llsl.viper4android.ui.screens.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class IemAttributionPolicyTest {
    @Test
    fun aboutSurfaceContainsCompleteIemAttribution() {
        val source = readSource("app/src/main/java/com/llsl/viper4android/ui/screens/settings/SettingsDialog.kt")
        val strings = readSource("app/src/main/res/values/strings.xml")

        assertTrue("Settings/About should expose an IEM license entry", "iem_license_title" in source)
        assertTrue("IEM attribution should link the official project", "https://plugins.iem.at" in source)
        assertTrue("IEM attribution should name GPL-3.0", "GPL-3.0" in strings)
        assertTrue("IEM attribution should include the pinned commit", "39de1dd5883f1bd8d65fe1662487f2470a1d7b55" in strings)
        assertTrue("IEM attribution should cite Bernschuetz", "Bernschuetz" in strings)
        assertTrue("IEM attribution should cite Schoerkhuber", "Schoerkhuber" in strings)
        assertTrue("IEM attribution should cite Zaunschirm", "Zaunschirm" in strings)
        assertTrue("IEM attribution should cite Hoeldrich", "Hoeldrich" in strings)
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }
}

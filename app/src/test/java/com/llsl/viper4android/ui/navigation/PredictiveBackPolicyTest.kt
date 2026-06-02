package com.llsl.viper4android.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class PredictiveBackPolicyTest {
    @Test
    fun mainActivityEnablesPlatformPredictiveBackCallback() {
        val manifest = readSource("app/src/main/AndroidManifest.xml")

        assertTrue(
            "MainActivity should opt in to platform predictive back callbacks",
            "android:enableOnBackInvokedCallback=\"true\"" in manifest,
        )
    }

    @Test
    fun viperDialogsKeepUsingMiuixOverlayDialogForPredictiveBackAnimation() {
        val dialog = readSource("app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperDialog.kt")
        val textFieldDialog = readSource("app/src/main/java/com/llsl/viper4android/ui/components/viper/ViperTextFieldDialog.kt")

        listOf(dialog, textFieldDialog).forEach { source ->
            assertTrue(
                "Viper dialogs should use MiuiX OverlayDialog, which provides NavigationBackHandler animation",
                "import top.yukonga.miuix.kmp.overlay.OverlayDialog" in source,
            )
            assertTrue(
                "Viper dialogs should call MiuiX OverlayDialog",
                "OverlayDialog(" in source,
            )
        }
    }

    @Test
    fun appDoesNotInstallActivityBackHandlersThatBypassMiuixPredictiveBack() {
        val kotlinSources = Files.walk(projectRoot().resolve("app/src/main/java"))
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .toList()

        kotlinSources.forEach { path ->
            val source = String(Files.readAllBytes(path), Charsets.UTF_8)
            assertFalse(
                "${projectRoot().relativize(path)} should not import activity BackHandler; use MiuiX overlays or NavigationEvent instead",
                "import androidx.activity.compose.BackHandler" in source,
            )
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

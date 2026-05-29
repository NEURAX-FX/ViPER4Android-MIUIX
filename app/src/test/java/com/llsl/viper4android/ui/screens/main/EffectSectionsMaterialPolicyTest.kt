package com.llsl.viper4android.ui.screens.main

import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class EffectSectionsMaterialPolicyTest {
    @Test
    fun effectSectionsDoesNotImportMaterial3UiComponents() {
        val source = Files.readString(projectRoot().resolve("app/src/main/java/com/llsl/viper4android/ui/screens/main/EffectSections.kt"))

        val forbiddenImports =
            listOf(
                "import androidx.compose.material3.AlertDialog",
                "import androidx.compose.material3.MaterialTheme",
                "import androidx.compose.material3.OutlinedTextField",
                "import androidx.compose.material3.PrimaryScrollableTabRow",
                "import androidx.compose.material3.PrimaryTabRow",
                "import androidx.compose.material3.Tab",
                "import androidx.compose.material3.TextButton",
            )

        val remainingImports = forbiddenImports.filter { it in source }
        assertFalse("EffectSections.kt still imports Material3 UI components: $remainingImports", remainingImports.isNotEmpty())
    }

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }
}

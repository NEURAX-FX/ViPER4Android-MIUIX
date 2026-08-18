package com.llsl.viper4android.ui.components.viper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class VstResponseGraphGesturePolicyTest {
    @Test
    fun graphConsumesDragOnlyAfterAHandleWasCaptured() {
        val source = readSource(
            "app/src/main/java/com/llsl/viper4android/ui/components/viper/VstResponseGraph.kt",
        )

        assertTrue("Graph should use one cooperative gesture detector", "awaitEachGesture" in source)
        assertTrue("Graph drag consumption must be gated by a captured handle", "if (grabbed == null)" in source)
        assertFalse(
            "detectTapGestures consumes the initial down and blocks parent scrolling",
            "detectTapGestures" in source,
        )
        assertFalse(
            "detectDragGestures captures background drags before the parent scroll can win",
            "detectDragGestures(" in source,
        )
    }

    private fun readSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot().resolve(relativePath)), Charsets.UTF_8)

    private fun projectRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        return generateSequence(start) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
    }
}

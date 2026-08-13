package com.llsl.viper4android.ui.screens.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffectEditorRoutingTest {
    @Test
    fun editorKindsRoundTripThroughStableRoutes() {
        EditorKind.entries.forEach { kind ->
            assertEquals(kind, editorKindFromRoute(kind.route))
        }
    }

    @Test
    fun iemUsesStableEditorRoute() {
        assertEquals(EditorKind.IEM, editorKindFromRoute("iem"))
    }

    @Test
    fun unknownEditorRouteIsRejected() {
        assertNull(editorKindFromRoute("unknown-effect"))
        assertNull(editorKindFromRoute(null))
    }
}

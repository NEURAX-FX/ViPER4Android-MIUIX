package com.llsl.viper4android.ui.screens.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorHistoryTest {
    @Test
    fun completeDragProducesOneUndoOperation() {
        val history = EditorHistory<Int>()

        history.beginGesture(10)
        assertTrue(history.settleGesture(40))

        assertEquals(1, history.undoSize)
        assertEquals(10, history.undo())
        assertEquals(40, history.redo())
    }

    @Test
    fun unchangedGestureDoesNotPolluteHistory() {
        val history = EditorHistory<Int>()

        history.beginGesture(10)

        assertFalse(history.settleGesture(10))
        assertEquals(0, history.undoSize)
        assertNull(history.undo())
    }

    @Test
    fun newGestureClearsRedoAndCapacityIsBounded() {
        val history = EditorHistory<Int>(capacity = 2)
        history.beginGesture(0)
        history.settleGesture(1)
        history.beginGesture(1)
        history.settleGesture(2)
        history.beginGesture(2)
        history.settleGesture(3)

        assertEquals(2, history.undoSize)
        assertEquals(2, history.undo())

        history.beginGesture(2)
        history.settleGesture(7)
        assertNull(history.redo())
    }
}

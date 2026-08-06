package com.llsl.viper4android.ui.components.viper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphDragReducerTest {
    @Test
    fun latestSampleWinsWithinOneFrame() {
        val reducer = GraphDragReducer()
        assertTrue(reducer.begin(GraphDragSample("a", 0.2f, 0.3f)))

        assertTrue(reducer.offer(GraphDragSample("a", 0.4f, 0.5f)))
        assertTrue(reducer.offer(GraphDragSample("a", 0.6f, 0.7f)))

        assertEquals(GraphDragSample("a", 0.6f, 0.7f), reducer.drain())
        assertNull(reducer.drain())
    }

    @Test
    fun finishAlwaysReturnsTheExactLatestPointAndClearsCapture() {
        val reducer = GraphDragReducer()
        reducer.begin(GraphDragSample("a", 0.2f, 0.3f))
        reducer.offer(GraphDragSample("a", 0.8f, 0.9f))
        reducer.drain()

        assertEquals(GraphDragSample("a", 0.8f, 0.9f), reducer.finish())
        assertFalse(reducer.isActive)
        assertNull(reducer.finish())
    }

    @Test
    fun disabledHandleAndSecondPointerCannotCapture() {
        val reducer = GraphDragReducer()

        assertFalse(reducer.begin(GraphDragSample("disabled", 0.1f, 0.2f), enabled = false))
        assertTrue(reducer.begin(GraphDragSample("first", 0.2f, 0.3f)))
        assertFalse(reducer.begin(GraphDragSample("second", 0.6f, 0.7f)))
        assertFalse(reducer.offer(GraphDragSample("second", 0.8f, 0.9f)))
    }

    @Test
    fun dragAxisPreservesTheLockedCoordinate() {
        val start = GraphDragSample("a", 0.25f, 0.75f)
        val candidate = GraphDragSample("a", 0.9f, 0.1f)

        assertEquals(candidate, applyGraphDragAxis(GraphDragAxis.FREE, start, candidate))
        assertEquals(
            GraphDragSample("a", 0.9f, 0.75f),
            applyGraphDragAxis(GraphDragAxis.HORIZONTAL, start, candidate),
        )
        assertEquals(
            GraphDragSample("a", 0.25f, 0.1f),
            applyGraphDragAxis(GraphDragAxis.VERTICAL, start, candidate),
        )
    }

    @Test
    fun cancellationUsesTheSameFinalSampleAsNormalFinish() {
        val reducer = GraphDragReducer()
        reducer.begin(GraphDragSample("a", 0.2f, 0.3f))
        reducer.offer(GraphDragSample("a", 0.4f, 0.5f))

        assertEquals(GraphDragSample("a", 0.4f, 0.5f), reducer.cancel())
        assertFalse(reducer.isActive)
    }
}

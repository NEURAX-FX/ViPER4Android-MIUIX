package com.llsl.viper4android.ui.components.viper

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViperCurvePreviewTest {
    @Test
    fun areaPolygonClosesResponseToBottomEdge() {
        val result = previewAreaPolygon(listOf(Offset(0.2f, 0.3f), Offset(0.8f, 0.6f)))

        assertEquals(
            listOf(
                Offset(0.2f, 1f),
                Offset(0.2f, 0.3f),
                Offset(0.8f, 0.6f),
                Offset(0.8f, 1f),
            ),
            result,
        )
    }

    @Test
    fun invalidOrSinglePointCurvesAreRejected() {
        assertTrue(previewAreaPolygon(listOf(Offset.Zero)).isEmpty())
        assertTrue(previewAreaPolygon(listOf(Offset(Float.NaN, 0f), Offset(1f, 1f))).isEmpty())
    }
}

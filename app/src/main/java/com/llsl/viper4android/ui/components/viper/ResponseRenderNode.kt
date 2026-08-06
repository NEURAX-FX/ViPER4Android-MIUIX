package com.llsl.viper4android.ui.components.viper

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderNode
import androidx.compose.ui.geometry.Offset

/**
 * Native display-list renderer for response curves only.
 *
 * Grid, labels, handles and gestures stay in Compose. Curves are the expensive, frequently
 * redrawn layer, so isolating them in one RenderNode avoids rebuilding Android paths and
 * draw commands when Compose redraws for a handle/semantics change.
 *
 * The app baseline is API 29, where android.graphics.RenderNode is public API.
 */
internal class ResponseRenderNode {
    private val node = RenderNode("ViperResponse")
    private val cache = ResponseDisplayListCache()
    private val path = Path()
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        curve: List<Offset>,
        bandCurves: List<List<Offset>>,
        referenceCurves: List<List<Offset>>,
        responseColor: Int,
        bandColors: IntArray,
        referenceColor: Int,
        curveStrokeWidth: Float,
        bandStrokeWidth: Float,
        referenceStrokeWidth: Float,
        curveDashed: Boolean,
    ) {
        if (
            width <= 0 ||
            height <= 0 ||
            (curve.size < 2 && bandCurves.none { it.size >= 2 } && referenceCurves.none { it.size >= 2 })
        ) {
            return
        }

        var styleRevision = responseColor
        styleRevision = styleRevision * 31 + bandColors.contentHashCode()
        styleRevision = styleRevision * 31 + referenceColor
        styleRevision = styleRevision * 31 + curveStrokeWidth.toBits()
        styleRevision = styleRevision * 31 + bandStrokeWidth.toBits()
        styleRevision = styleRevision * 31 + referenceStrokeWidth.toBits()
        styleRevision = styleRevision * 31 + curveDashed.hashCode()
        if (
            cache.needsRecording(
                width = width,
                height = height,
                curveToken = curve,
                bandCurvesToken = bandCurves,
                styleRevision = styleRevision,
                referenceCurvesToken = referenceCurves,
            )
        ) {
            record(
                width = width,
                height = height,
                curve = curve,
                bandCurves = bandCurves,
                referenceCurves = referenceCurves,
                responseColor = responseColor,
                bandColors = bandColors,
                referenceColor = referenceColor,
                curveStrokeWidth = curveStrokeWidth,
                bandStrokeWidth = bandStrokeWidth,
                referenceStrokeWidth = referenceStrokeWidth,
                curveDashed = curveDashed,
            )
            cache.markRecorded(
                width = width,
                height = height,
                curveToken = curve,
                bandCurvesToken = bandCurves,
                styleRevision = styleRevision,
                referenceCurvesToken = referenceCurves,
            )
        }
        canvas.drawRenderNode(node)
    }

    private fun record(
        width: Int,
        height: Int,
        curve: List<Offset>,
        bandCurves: List<List<Offset>>,
        referenceCurves: List<List<Offset>>,
        responseColor: Int,
        bandColors: IntArray,
        referenceColor: Int,
        curveStrokeWidth: Float,
        bandStrokeWidth: Float,
        referenceStrokeWidth: Float,
        curveDashed: Boolean,
    ) {
        node.setPosition(0, 0, width, height)
        val recordingCanvas = node.beginRecording(width, height)
        try {
            if (bandColors.isNotEmpty()) {
                paint.pathEffect = null
                paint.strokeWidth = bandStrokeWidth
                bandCurves.forEachIndexed { index, points ->
                    if (points.size < 2) return@forEachIndexed
                    paint.color = bandColors[index % bandColors.size]
                    recordingCanvas.drawPath(buildPath(points, width, height), paint)
                }
            }
            if (referenceCurves.isNotEmpty()) {
                paint.color = referenceColor
                paint.strokeWidth = referenceStrokeWidth
                paint.pathEffect =
                    DashPathEffect(
                        floatArrayOf(referenceStrokeWidth * 4f, referenceStrokeWidth * 3f),
                        0f,
                    )
                referenceCurves.forEach { points ->
                    if (points.size < 2) return@forEach
                    recordingCanvas.drawPath(buildPath(points, width, height), paint)
                }
            }
            if (curve.size >= 2) {
                paint.color = responseColor
                paint.strokeWidth = curveStrokeWidth
                paint.pathEffect =
                    if (curveDashed) {
                        DashPathEffect(floatArrayOf(curveStrokeWidth * 4f, curveStrokeWidth * 3f), 0f)
                    } else {
                        null
                    }
                recordingCanvas.drawPath(buildPath(curve, width, height), paint)
            }
        } finally {
            paint.pathEffect = null
            node.endRecording()
        }
    }

    private fun buildPath(points: List<Offset>, width: Int, height: Int): Path {
        path.rewind()
        path.moveTo(points[0].x * width, points[0].y * height)
        for (index in 1 until points.size) {
            path.lineTo(points[index].x * width, points[index].y * height)
        }
        return path
    }
}

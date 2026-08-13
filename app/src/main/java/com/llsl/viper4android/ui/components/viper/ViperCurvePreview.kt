package com.llsl.viper4android.ui.components.viper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun previewAreaPolygon(points: List<Offset>): List<Offset> {
    if (points.size < 2 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return emptyList()
    val normalized = points.map { Offset(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }
    return buildList(normalized.size + 2) {
        add(Offset(normalized.first().x, 1f))
        addAll(normalized)
        add(Offset(normalized.last().x, 1f))
    }
}

@Composable
fun ViperCurvePreview(
    curve: List<Offset>,
    bandCurves: List<List<Offset>> = emptyList(),
    referenceCurves: List<List<Offset>> = emptyList(),
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MiuixTheme.colorScheme.primary
    val secondary = MiuixTheme.colorScheme.secondary
    val outline = MiuixTheme.colorScheme.outline
    val surface = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.34f)
    val bandColors = listOf(primary, secondary)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(85.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(surface)
                .semantics { this.contentDescription = contentDescription }
                .clickable(onClick = onClick),
    ) {
        for (index in 1..3) {
            val y = size.height * index / 4f
            drawLine(
                color = outline.copy(alpha = if (index == 2) 0.12f else 0.08f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        for (index in 1..4) {
            val x = size.width * index / 5f
            drawLine(
                color = outline.copy(alpha = 0.08f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }

        referenceCurves.forEach { points ->
            drawNormalizedCurve(points, outline.copy(alpha = 0.45f), 1.dp.toPx())
        }
        bandCurves.forEachIndexed { index, points ->
            drawNormalizedCurve(
                points,
                bandColors[index % bandColors.size].copy(alpha = 0.55f),
                1.25.dp.toPx(),
            )
        }

        val area = previewAreaPolygon(curve)
        if (area.isNotEmpty()) {
            val areaPath = normalizedPath(area)
            areaPath.close()
            drawPath(
                path = areaPath,
                brush =
                    Brush.verticalGradient(
                        listOf(primary.copy(alpha = 0.35f), primary.copy(alpha = 0f)),
                    ),
            )
        }
        drawNormalizedCurve(curve, primary, 2.dp.toPx())
    }
}

private fun DrawScope.normalizedPath(points: List<Offset>): Path {
    val path = Path()
    points.forEachIndexed { index, point ->
        val x = point.x.coerceIn(0f, 1f) * size.width
        val y = point.y.coerceIn(0f, 1f) * size.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

private fun DrawScope.drawNormalizedCurve(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
) {
    if (points.size < 2 || points.any { !it.x.isFinite() || !it.y.isFinite() }) return
    drawPath(
        path = normalizedPath(points),
        color = color,
        style = Stroke(width = strokeWidth),
    )
}

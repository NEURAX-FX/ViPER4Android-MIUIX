package com.llsl.viper4android.ui.components.viper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llsl.viper4android.ui.screens.editor.GraphBandRegion
import com.llsl.viper4android.ui.screens.editor.GraphHandleModel
import com.llsl.viper4android.ui.screens.editor.bandRegionAt
import com.llsl.viper4android.ui.screens.editor.nearestGraphHandle
import kotlinx.coroutines.channels.Channel
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

enum class GraphDragAxis {
    FREE,
    HORIZONTAL,
    VERTICAL,
}

data class GraphHandle(
    val id: String,
    val x: Float,
    val y: Float,
    val color: Color,
    val label: String,
    val dragAxis: GraphDragAxis = GraphDragAxis.FREE,
    val valueDescription: String? = null,
    val enabled: Boolean = true,
    val badge: String? = null,
)

/**
 * A single grid line expressed in normalized graph coordinates (0f..1f).
 * [position] is the x fraction for vertical lines and the y fraction for horizontal lines.
 */
data class GraphGridLine(
    val position: Float,
    val major: Boolean = false,
    val label: String? = null,
)

private val DefaultGraphHeight = 230.dp
private val HandleTouchRadius = 28.dp
private val CurveStroke = 2.dp
private val GridStroke = 0.5.dp
private val GridMajorStroke = 0.8.dp
private val SurfaceCorner = 9.dp
private val HandleHalo = 9.dp
private val HandleHaloSelected = 13.dp
private val HandleRadius = 4.dp
private val HandleRadiusSelected = 5.dp
private val HandleCenterRadius = 1.5.dp
private val LabelInset = 2.dp

/**
 * Index of the handle nearest to a pointer position, or `null` when the pointer is farther
 * away than [radiusPx]. Coordinates are compared in pixel space so the hit area stays
 * circular regardless of the graph aspect ratio.
 */
private fun nearestHandleAt(
    handles: List<GraphHandle>,
    point: Offset,
    width: Float,
    height: Float,
    radiusPx: Float,
): GraphHandle? {
    val enabledHandles = handles.filter { it.enabled }
    val index = nearestGraphHandle(
        handles = enabledHandles.map { GraphHandleModel(it.id, it.x * width, it.y * height) },
        x = point.x,
        y = point.y,
        hitRadius = radiusPx,
    )
    return index?.let(enabledHandles::get)
}

private fun defaultVerticalLines(): List<GraphGridLine> =
    (1..7).map { GraphGridLine(position = it / 8f) }

private fun defaultHorizontalLines(): List<GraphGridLine> =
    (1..4).map { GraphGridLine(position = it / 5f) }

@Composable
fun VstResponseGraph(
    handles: List<GraphHandle>,
    curve: List<Offset> = emptyList(),
    /**
     * Additional curves drawn beneath [curve], one per band. Used by the multiband
     * compressor to show each crossover band's own response instead of a single summed
     * line that would hide where the splits actually are.
     */
    bandCurves: List<List<Offset>> = emptyList(),
    referenceCurves: List<List<Offset>> = emptyList(),
    bandCurveColors: List<Color> = emptyList(),
    bandRegions: List<GraphBandRegion> = emptyList(),
    selectedBandRegionIndex: Int? = null,
    selectedHandleId: String? = null,
    interactive: Boolean = true,
    graphHeight: Dp = DefaultGraphHeight,
    verticalGridLines: List<GraphGridLine> = defaultVerticalLines(),
    horizontalGridLines: List<GraphGridLine> = defaultHorizontalLines(),
    showGridLabels: Boolean = false,
    curveDashed: Boolean = false,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    onHandleSelected: (String) -> Unit = {},
    onBandRegionSelected: ((Int) -> Unit)? = null,
    onHandleDragStart: (String) -> Unit = {},
    onHandleDrag: (String, x: Float, y: Float) -> Unit,
    onHandleDragSettled: (String, x: Float, y: Float) -> Unit = { _, _, _ -> },
    onHandleDragEnd: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var activeHandleId by remember { mutableStateOf<String?>(null) }
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    var dragStart by remember { mutableStateOf<GraphDragSample?>(null) }
    var activeDragAxis by remember { mutableStateOf(GraphDragAxis.FREE) }
    val latestHandles by rememberUpdatedState(handles)
    val latestBandRegions by rememberUpdatedState(bandRegions)
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnHandleSelected by rememberUpdatedState(onHandleSelected)
    val latestOnBandRegionSelected by rememberUpdatedState(onBandRegionSelected)
    val latestOnHandleDragStart by rememberUpdatedState(onHandleDragStart)
    val latestOnHandleDrag by rememberUpdatedState(onHandleDrag)
    val latestOnHandleDragSettled by rememberUpdatedState(onHandleDragSettled)
    val latestOnHandleDragEnd by rememberUpdatedState(onHandleDragEnd)
    val dragReducer = remember { GraphDragReducer() }
    val dragSignals = remember { Channel<Unit>(Channel.CONFLATED) }
    val grid = MiuixTheme.colorScheme.outline.copy(alpha = 0.16f)
    val gridMajor = MiuixTheme.colorScheme.outline.copy(alpha = 0.32f)
    val graphSurface = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.76f)
    val responseColor = MiuixTheme.colorScheme.primary
    val referenceColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.70f)
    val handleCenterColor = MiuixTheme.colorScheme.background
    val labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val primaryTint = MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
    val secondaryTint = MiuixTheme.colorScheme.secondary.copy(alpha = 0.10f)
    val bandCurveBase = MiuixTheme.colorScheme.primary
    val bandCurveAlt = MiuixTheme.colorScheme.secondary
    val resolvedBandCurveColors = remember(bandCurveColors, bandCurveBase, bandCurveAlt) {
        bandCurveColors.ifEmpty {
            listOf(
                bandCurveBase.copy(alpha = 0.55f),
                bandCurveAlt.copy(alpha = 0.55f),
            )
        }
    }
    val regionTints = remember(resolvedBandCurveColors, primaryTint, secondaryTint) {
        resolvedBandCurveColors
            .ifEmpty { listOf(primaryTint, secondaryTint) }
            .map { it.copy(alpha = 0.10f) }
    }
    val bandCurveArgb = remember(resolvedBandCurveColors) {
        IntArray(resolvedBandCurveColors.size) { index -> resolvedBandCurveColors[index].toArgb() }
    }
    val responseRenderNode = remember { ResponseRenderNode() }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(labelColor) { TextStyle(color = labelColor, fontSize = 9.sp) }
    // Canvas draws in pixels, so every visual dimension has to be converted once here.
    // Leaving them as raw floats made strokes and handles shrink on high-density screens
    // while the 28dp touch target stayed the same size.
    val density = LocalDensity.current
    val touchRadiusPx = with(density) { HandleTouchRadius.toPx() }
    val curveStrokePx = with(density) { CurveStroke.toPx() }
    val gridStrokePx = with(density) { GridStroke.toPx() }
    val gridMajorStrokePx = with(density) { GridMajorStroke.toPx() }
    val surfaceCornerPx = with(density) { SurfaceCorner.toPx() }
    val handleHaloPx = with(density) { HandleHalo.toPx() }
    val handleHaloSelectedPx = with(density) { HandleHaloSelected.toPx() }
    val handleRadiusPx = with(density) { HandleRadius.toPx() }
    val handleRadiusSelectedPx = with(density) { HandleRadiusSelected.toPx() }
    val handleCenterPx = with(density) { HandleCenterRadius.toPx() }
    val labelInsetPx = with(density) { LabelInset.toPx() }
    val selectedHandle = handles.firstOrNull { it.id == (activeHandleId ?: selectedHandleId) }
    val semanticsLabel = buildString {
        append(
            contentDescription ?: handles.joinToString(separator = ", ") { handle ->
                buildString {
                    append(handle.label)
                    handle.valueDescription?.let {
                        append(' ')
                        append(it)
                    }
                    handle.badge?.let {
                        append(' ')
                        append(it)
                    }
                    if (!handle.enabled) append(" disabled")
                }
            },
        )
        selectedHandle?.let { handle ->
            append(", ")
            append(handle.label)
            handle.valueDescription?.let {
                append(' ')
                append(it)
            }
            handle.badge?.let {
                append(' ')
                append(it)
            }
        }
    }

    LaunchedEffect(dragSignals) {
        for (signal in dragSignals) {
            withFrameNanos { }
            dragReducer.drain()?.let { sample ->
                latestOnHandleDrag(sample.handleId, sample.x, sample.y)
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(graphHeight)
            .semantics { this.contentDescription = semanticsLabel }
            .onSizeChanged { size = it }
            .pointerInput(size, interactive) {
                detectTapGestures { point ->
                    if (!interactive) {
                        latestOnClick?.invoke()
                        return@detectTapGestures
                    }
                    val grabbed = nearestHandleAt(
                        handles = latestHandles,
                        point = point,
                        width = size.width.coerceAtLeast(1).toFloat(),
                        height = size.height.coerceAtLeast(1).toFloat(),
                        radiusPx = touchRadiusPx,
                    )
                    if (grabbed != null) {
                        latestOnHandleSelected(grabbed.id)
                    } else {
                        val width = size.width.coerceAtLeast(1).toFloat()
                        val region = bandRegionAt(latestBandRegions, point.x / width)
                        if (region != null && latestOnBandRegionSelected != null) {
                            latestOnBandRegionSelected?.invoke(region)
                        } else {
                            latestOnClick?.invoke()
                        }
                    }
                }
            }
            .pointerInput(size, interactive) {
                if (!interactive) return@pointerInput
                detectDragGestures(
                    onDragStart = { point ->
                        val w = size.width.coerceAtLeast(1).toFloat()
                        val h = size.height.coerceAtLeast(1).toFloat()
                        val grabbed = nearestHandleAt(latestHandles, point, w, h, touchRadiusPx)
                        grabbed?.let {
                            val start = GraphDragSample(it.id, it.x, it.y)
                            if (!dragReducer.begin(start, enabled = it.enabled)) return@let
                            activeHandleId = it.id
                            dragStart = start
                            activeDragAxis = it.dragAxis
                            dragX = it.x
                            dragY = it.y
                            latestOnHandleSelected(it.id)
                            latestOnHandleDragStart(it.id)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val id = activeHandleId ?: return@detectDragGestures
                        val start = dragStart ?: return@detectDragGestures
                        val w = size.width.coerceAtLeast(1).toFloat()
                        val h = size.height.coerceAtLeast(1).toFloat()
                        dragX = (dragX + dragAmount.x / w).coerceIn(0f, 1f)
                        dragY = (dragY + dragAmount.y / h).coerceIn(0f, 1f)
                        val sample =
                            applyGraphDragAxis(
                                axis = activeDragAxis,
                                start = start,
                                candidate = GraphDragSample(id, dragX, dragY),
                            )
                        change.consume()
                        if (dragReducer.offer(sample)) dragSignals.trySend(Unit)
                    },
                    onDragEnd = {
                        dragReducer.finish()?.let { sample ->
                            latestOnHandleDrag(sample.handleId, sample.x, sample.y)
                            latestOnHandleDragSettled(sample.handleId, sample.x, sample.y)
                            latestOnHandleDragEnd(sample.handleId)
                        }
                        activeHandleId = null
                        dragStart = null
                    },
                    onDragCancel = {
                        dragReducer.cancel()?.let { sample ->
                            latestOnHandleDrag(sample.handleId, sample.x, sample.y)
                            latestOnHandleDragSettled(sample.handleId, sample.x, sample.y)
                            latestOnHandleDragEnd(sample.handleId)
                        }
                        activeHandleId = null
                        dragStart = null
                    },
                )
            },
    ) {
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        drawRoundRect(
            color = graphSurface,
            cornerRadius = CornerRadius(surfaceCornerPx, surfaceCornerPx),
        )
        bandRegions.forEachIndexed { index, region ->
            val start = width * region.startX
            val end = width * region.endX
            if (end <= start) return@forEachIndexed
            drawRect(
                color =
                    regionTints[index % regionTints.size].copy(
                        alpha = if (index == selectedBandRegionIndex) 0.20f else 0.10f,
                    ),
                topLeft = Offset(start, 0f),
                size = Size(end - start, height),
            )
            if (index == selectedBandRegionIndex) {
                drawRect(
                    color = resolvedBandCurveColors[index % resolvedBandCurveColors.size],
                    topLeft = Offset(start, 0f),
                    size = Size(end - start, height),
                    style = Stroke(width = gridMajorStrokePx),
                )
            }
        }
        verticalGridLines.forEach { line ->
            val x = width * line.position
            drawLine(
                color = if (line.major) gridMajor else grid,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = if (line.major) gridMajorStrokePx else gridStrokePx,
            )
        }
        horizontalGridLines.forEach { line ->
            val y = height * line.position
            drawLine(
                color = if (line.major) gridMajor else grid,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = if (line.major) gridMajorStrokePx else gridStrokePx,
            )
        }
        if (showGridLabels) {
            // Drop labels that would collide with an already drawn one. LSP's Graph does the
            // same with bounding boxes; clamping alone just stacks text on narrow screens.
            var lastLabelRight = Float.NEGATIVE_INFINITY
            verticalGridLines.filter { it.label != null }.forEach { line ->
                val measured = textMeasurer.measure(line.label!!, labelStyle)
                val x = (width * line.position - measured.size.width / 2f)
                    .coerceIn(
                        labelInsetPx,
                        (width - measured.size.width - labelInsetPx).coerceAtLeast(labelInsetPx),
                    )
                if (x < lastLabelRight) return@forEach
                lastLabelRight = x + measured.size.width + labelInsetPx * 2f
                drawText(
                    measured,
                    topLeft = Offset(x, height - measured.size.height - labelInsetPx),
                )
            }
            horizontalGridLines.filter { it.label != null }.forEach { line ->
                val measured = textMeasurer.measure(line.label!!, labelStyle)
                val y = (height * line.position - measured.size.height / 2f)
                    .coerceIn(
                        labelInsetPx,
                        (height - measured.size.height - labelInsetPx).coerceAtLeast(labelInsetPx),
                    )
                drawText(measured, topLeft = Offset(labelInsetPx * 2f, y))
            }
        }
        // Only response curves live in the native display list. Compose continues to own
        // background, grid, labels, handles and gestures, while RenderNode replays this
        // expensive layer until curve data, size or style actually changes.
        drawIntoCanvas { canvas ->
            responseRenderNode.draw(
                canvas = canvas.nativeCanvas,
                width = width.roundToInt(),
                height = height.roundToInt(),
                curve = curve,
                bandCurves = bandCurves,
                referenceCurves = referenceCurves,
                responseColor = responseColor.toArgb(),
                bandColors = bandCurveArgb,
                referenceColor = referenceColor.toArgb(),
                curveStrokeWidth = curveStrokePx,
                bandStrokeWidth = gridMajorStrokePx,
                referenceStrokeWidth = gridMajorStrokePx,
                curveDashed = curveDashed,
            )
        }
        handles.forEach { handle ->
            val selected = handle.id == selectedHandleId || handle.id == activeHandleId
            val point = Offset(handle.x * width, handle.y * height)
            drawCircle(
                color =
                    handle.color.copy(
                        alpha =
                            when {
                                !handle.enabled -> 0.08f
                                selected -> 0.3f
                                else -> 0.14f
                            },
                    ),
                radius = if (selected) handleHaloSelectedPx else handleHaloPx,
                center = point,
            )
            drawCircle(
                handle.color.copy(alpha = if (handle.enabled) 1f else 0.45f),
                radius = if (selected) handleRadiusSelectedPx else handleRadiusPx,
                center = point,
            )
            drawCircle(handleCenterColor, radius = handleCenterPx, center = point)
            handle.badge?.let { badge ->
                val measured = textMeasurer.measure(badge, labelStyle)
                val badgeX =
                    (point.x - measured.size.width / 2f)
                        .coerceIn(0f, (width - measured.size.width).coerceAtLeast(0f))
                val badgeY = (point.y - handleHaloSelectedPx - measured.size.height).coerceAtLeast(0f)
                drawText(measured, topLeft = Offset(badgeX, badgeY))
            }
        }
    }
}

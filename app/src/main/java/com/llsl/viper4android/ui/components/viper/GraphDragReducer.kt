package com.llsl.viper4android.ui.components.viper

internal data class GraphDragSample(
    val handleId: String,
    val x: Float,
    val y: Float,
)

internal class GraphDragReducer {
    private var activeHandleId: String? = null
    private var latest: GraphDragSample? = null
    private var pending: GraphDragSample? = null

    val isActive: Boolean
        get() = activeHandleId != null

    fun begin(
        sample: GraphDragSample,
        enabled: Boolean = true,
    ): Boolean {
        if (!enabled || activeHandleId != null) return false
        activeHandleId = sample.handleId
        latest = sample
        pending = null
        return true
    }

    fun offer(sample: GraphDragSample): Boolean {
        if (sample.handleId != activeHandleId) return false
        latest = sample
        pending = sample
        return true
    }

    fun drain(): GraphDragSample? = pending.also { pending = null }

    fun finish(): GraphDragSample? {
        if (activeHandleId == null) return null
        return latest.also { clear() }
    }

    fun cancel(): GraphDragSample? = finish()

    private fun clear() {
        activeHandleId = null
        latest = null
        pending = null
    }
}

internal fun applyGraphDragAxis(
    axis: GraphDragAxis,
    start: GraphDragSample,
    candidate: GraphDragSample,
): GraphDragSample =
    candidate.copy(
        x = if (axis == GraphDragAxis.VERTICAL) start.x else candidate.x.coerceIn(0f, 1f),
        y = if (axis == GraphDragAxis.HORIZONTAL) start.y else candidate.y.coerceIn(0f, 1f),
    )

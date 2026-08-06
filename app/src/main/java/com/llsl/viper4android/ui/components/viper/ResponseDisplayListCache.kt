package com.llsl.viper4android.ui.components.viper

/**
 * Tracks which response scene is currently recorded in the native display list.
 *
 * Curves are immutable lists produced by the graph model and retained with `remember`, so
 * identity is the cheapest correct invalidation signal. Structural equality would walk up
 * to hundreds of response points on every draw and negate part of the RenderNode benefit.
 */
internal class ResponseDisplayListCache {
    private var width = -1
    private var height = -1
    private var curveToken: Any? = null
    private var bandCurvesToken: Any? = null
    private var referenceCurvesToken: Any? = null
    private var styleRevision = Int.MIN_VALUE
    private var recorded = false

    fun needsRecording(
        width: Int,
        height: Int,
        curveToken: Any?,
        bandCurvesToken: Any?,
        styleRevision: Int,
        referenceCurvesToken: Any? = null,
    ): Boolean =
        !recorded ||
            this.width != width ||
            this.height != height ||
            this.curveToken !== curveToken ||
            this.bandCurvesToken !== bandCurvesToken ||
            this.referenceCurvesToken !== referenceCurvesToken ||
            this.styleRevision != styleRevision

    fun markRecorded(
        width: Int,
        height: Int,
        curveToken: Any?,
        bandCurvesToken: Any?,
        styleRevision: Int,
        referenceCurvesToken: Any? = null,
    ) {
        this.width = width
        this.height = height
        this.curveToken = curveToken
        this.bandCurvesToken = bandCurvesToken
        this.referenceCurvesToken = referenceCurvesToken
        this.styleRevision = styleRevision
        recorded = true
    }

    fun clear() {
        recorded = false
        curveToken = null
        bandCurvesToken = null
        referenceCurvesToken = null
    }
}

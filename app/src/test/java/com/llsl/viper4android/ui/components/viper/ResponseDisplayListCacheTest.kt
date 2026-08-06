package com.llsl.viper4android.ui.components.viper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseDisplayListCacheTest {
    private val curve = Any()
    private val bandCurves = Any()
    private val referenceCurves = Any()

    @Test
    fun firstFrameRecordsTheDisplayList() {
        val cache = ResponseDisplayListCache()

        assertTrue(cache.needsRecording(320, 180, curve, bandCurves, styleRevision = 7))
    }

    @Test
    fun unchangedSceneReusesTheDisplayList() {
        val cache = ResponseDisplayListCache()

        cache.markRecorded(320, 180, curve, bandCurves, styleRevision = 7)

        assertFalse(cache.needsRecording(320, 180, curve, bandCurves, styleRevision = 7))
    }

    @Test
    fun changingCurveIdentityRecordsAgain() {
        val cache = ResponseDisplayListCache()
        cache.markRecorded(320, 180, curve, bandCurves, styleRevision = 7)

        assertTrue(cache.needsRecording(320, 180, Any(), bandCurves, styleRevision = 7))
    }

    @Test
    fun changingBandCurvesIdentityRecordsAgain() {
        val cache = ResponseDisplayListCache()
        cache.markRecorded(320, 180, curve, bandCurves, styleRevision = 7)

        assertTrue(cache.needsRecording(320, 180, curve, Any(), styleRevision = 7))
    }

    @Test
    fun changingReferenceCurvesIdentityRecordsAgain() {
        val cache = ResponseDisplayListCache()
        cache.markRecorded(
            320,
            180,
            curve,
            bandCurves,
            styleRevision = 7,
            referenceCurvesToken = referenceCurves,
        )

        assertTrue(
            cache.needsRecording(
                320,
                180,
                curve,
                bandCurves,
                styleRevision = 7,
                referenceCurvesToken = Any(),
            ),
        )
    }

    @Test
    fun unchangedReferenceCurvesReuseTheDisplayList() {
        val cache = ResponseDisplayListCache()
        cache.markRecorded(
            320,
            180,
            curve,
            bandCurves,
            styleRevision = 7,
            referenceCurvesToken = referenceCurves,
        )

        assertFalse(
            cache.needsRecording(
                320,
                180,
                curve,
                bandCurves,
                styleRevision = 7,
                referenceCurvesToken = referenceCurves,
            ),
        )
    }

    @Test
    fun changingSizeOrStyleRecordsAgain() {
        val cache = ResponseDisplayListCache()
        cache.markRecorded(320, 180, curve, bandCurves, styleRevision = 7)

        assertTrue(cache.needsRecording(321, 180, curve, bandCurves, styleRevision = 7))
        assertTrue(cache.needsRecording(320, 181, curve, bandCurves, styleRevision = 7))
        assertTrue(cache.needsRecording(320, 180, curve, bandCurves, styleRevision = 8))
    }

    @Test
    fun clearingForcesTheNextFrameToRecord() {
        val cache = ResponseDisplayListCache()
        cache.markRecorded(320, 180, curve, bandCurves, styleRevision = 7)

        cache.clear()

        assertTrue(cache.needsRecording(320, 180, curve, bandCurves, styleRevision = 7))
    }

    @Test
    fun cacheUsesIdentityNotExpensiveStructuralEquality() {
        val first = listOf(1f, 2f, 3f)
        val structurallyEqualCopy = first.toList()
        val cache = ResponseDisplayListCache()
        cache.markRecorded(320, 180, first, bandCurves, styleRevision = 7)

        assertEquals(first, structurallyEqualCopy)
        assertTrue(cache.needsRecording(320, 180, structurallyEqualCopy, bandCurves, 7))
    }
}

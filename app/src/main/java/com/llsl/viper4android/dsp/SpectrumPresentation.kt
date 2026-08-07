package com.llsl.viper4android.dsp

import androidx.compose.ui.geometry.Offset

const val SPECTRUM_FLOOR_DB = -96f
const val SPECTRUM_CEILING_DB = 0f

fun smoothSpectrum(
    previous: List<Float>,
    current: List<Float>,
    attack: Float = 0.6f,
    release: Float = 0.2f,
): List<Float> {
    val target = current.map(::sanitizeSpectrumDb)
    if (previous.size != target.size) return target
    val attackAmount = attack.coerceIn(0f, 1f)
    val releaseAmount = release.coerceIn(0f, 1f)
    return target.mapIndexed { index, value ->
        val prior = sanitizeSpectrumDb(previous[index])
        val amount = if (value >= prior) attackAmount else releaseAmount
        prior + (value - prior) * amount
    }
}

fun interpolateSpectrum(
    from: List<Float>,
    to: List<Float>,
    progress: Float,
): List<Float> {
    val target = to.map(::sanitizeSpectrumDb)
    if (from.size != target.size) return target
    val amount = progress.coerceIn(0f, 1f)
    return target.mapIndexed { index, value ->
        val start = sanitizeSpectrumDb(from[index])
        start + (value - start) * amount
    }
}

fun fitSpectrumDb(
    buckets: List<Float>,
    subdivisions: Int = 3,
): List<Float> {
    if (buckets.isEmpty()) return emptyList()
    if (buckets.size == 1) return listOf(sanitizeSpectrumDb(buckets.single()))
    val steps = subdivisions.coerceAtLeast(1)
    val values = buckets.map(::sanitizeSpectrumDb)
    val deltas = List(values.lastIndex) { index -> values[index + 1] - values[index] }
    val tangents = MutableList(values.size) { 0f }
    tangents[0] = deltas.first()
    tangents[tangents.lastIndex] = deltas.last()
    for (index in 1 until values.lastIndex) {
        val before = deltas[index - 1]
        val after = deltas[index]
        tangents[index] =
            if (before == 0f || after == 0f || before * after <= 0f) {
                0f
            } else {
                2f * before * after / (before + after)
            }
    }

    return buildList(values.lastIndex * steps + 1) {
        for (segment in 0 until values.lastIndex) {
            val start = values[segment]
            val end = values[segment + 1]
            val minimum = minOf(start, end)
            val maximum = maxOf(start, end)
            for (step in 0 until steps) {
                val t = step.toFloat() / steps
                val t2 = t * t
                val t3 = t2 * t
                val fitted =
                    (2f * t3 - 3f * t2 + 1f) * start +
                        (t3 - 2f * t2 + t) * tangents[segment] +
                        (-2f * t3 + 3f * t2) * end +
                        (t3 - t2) * tangents[segment + 1]
                add(fitted.coerceIn(minimum, maximum))
            }
        }
        add(values.last())
    }
}

fun spectrumCurvePoints(
    buckets: List<Float>,
    subdivisions: Int = 3,
): List<Offset> {
    val fitted = fitSpectrumDb(buckets, subdivisions)
    if (fitted.isEmpty()) return emptyList()
    if (fitted.size == 1) return listOf(Offset(0f, spectrumDbToY(fitted.single())))
    return fitted.mapIndexed { index, db ->
        Offset(
            x = index.toFloat() / fitted.lastIndex,
            y = spectrumDbToY(db),
        )
    }
}

private fun sanitizeSpectrumDb(value: Float): Float =
    if (value.isFinite()) {
        value.coerceIn(SPECTRUM_FLOOR_DB, SPECTRUM_CEILING_DB)
    } else {
        SPECTRUM_FLOOR_DB
    }

private fun spectrumDbToY(value: Float): Float =
    ((SPECTRUM_CEILING_DB - sanitizeSpectrumDb(value)) /
        (SPECTRUM_CEILING_DB - SPECTRUM_FLOOR_DB)).coerceIn(0f, 1f)

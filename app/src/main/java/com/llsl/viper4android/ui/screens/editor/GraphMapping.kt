package com.llsl.viper4android.ui.screens.editor

import com.llsl.viper4android.effect.MULTIBAND_MAX_FREQUENCY
import com.llsl.viper4android.effect.MULTIBAND_MIN_FREQUENCY
import com.llsl.viper4android.effect.constrainMultibandCrossover
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

fun frequencyToX(
    frequency: Double,
    minFrequency: Double,
    maxFrequency: Double,
): Float {
    require(minFrequency > 0.0 && maxFrequency > minFrequency)
    val safeFrequency = frequency.takeIf { it.isFinite() }?.coerceIn(minFrequency, maxFrequency) ?: minFrequency
    return ((ln(safeFrequency) - ln(minFrequency)) / (ln(maxFrequency) - ln(minFrequency))).toFloat()
}

fun xToFrequency(
    x: Float,
    minFrequency: Double,
    maxFrequency: Double,
): Double {
    require(minFrequency > 0.0 && maxFrequency > minFrequency)
    val normalized = x.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    return exp(ln(minFrequency) + normalized * (ln(maxFrequency) - ln(minFrequency)))
}

fun dbToY(
    db: Double,
    minDb: Double,
    maxDb: Double,
): Float {
    require(maxDb > minDb)
    val safeDb = db.takeIf { it.isFinite() }?.coerceIn(minDb, maxDb) ?: minDb
    return ((maxDb - safeDb) / (maxDb - minDb)).toFloat()
}

fun yToDb(
    y: Float,
    minDb: Double,
    maxDb: Double,
): Double {
    require(maxDb > minDb)
    val normalized = y.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f
    return maxDb - normalized * (maxDb - minDb)
}

fun linearValueToX(
    value: Double,
    minValue: Double,
    maxValue: Double,
): Float {
    require(maxValue > minValue)
    val safeValue = value.takeIf { it.isFinite() }?.coerceIn(minValue, maxValue) ?: minValue
    return ((safeValue - minValue) / (maxValue - minValue)).toFloat()
}

fun xToLinearValue(
    x: Float,
    minValue: Double,
    maxValue: Double,
): Double {
    require(maxValue > minValue)
    val normalized = x.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    return minValue + normalized * (maxValue - minValue)
}

fun linearValueToY(
    value: Double,
    minValue: Double,
    maxValue: Double,
): Float = dbToY(value, minValue, maxValue)

fun yToLinearValue(
    y: Float,
    minValue: Double,
    maxValue: Double,
): Double = yToDb(y, minValue, maxValue)

fun nearestFixedBand(
    frequencies: List<Double>,
    x: Float,
    minFrequency: Double,
    maxFrequency: Double,
): Int {
    require(frequencies.isNotEmpty())
    val normalized = x.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    return frequencies.indices.minBy { index ->
        abs(frequencyToX(frequencies[index], minFrequency, maxFrequency) - normalized)
    }
}

/** Compose-free handle geometry in normalized graph coordinates (0f..1f). */
data class GraphHandleModel(
    val id: String,
    val x: Float,
    val y: Float,
)

/** Compose-free horizontal band region in normalized graph coordinates (0f..1f). */
data class GraphBandRegion(
    val startX: Float,
    val endX: Float,
    val label: String? = null,
)

fun bandRegionAt(
    regions: List<GraphBandRegion>,
    x: Float,
): Int? {
    if (regions.isEmpty() || !x.isFinite()) return null
    val normalized = x.coerceIn(0f, 1f)
    regions.forEachIndexed { index, region ->
        val containsEnd = index == regions.lastIndex && normalized <= region.endX
        if (normalized >= region.startX && (normalized < region.endX || containsEnd)) return index
    }
    return null
}

/**
 * Index of the handle closest to ([x], [y]) within [hitRadius], measured as Euclidean
 * distance in normalized graph space, or `null` when nothing is close enough.
 */
fun nearestGraphHandle(
    handles: List<GraphHandleModel>,
    x: Float,
    y: Float,
    hitRadius: Float,
): Int? {
    if (handles.isEmpty()) return null
    require(hitRadius > 0f) { "hitRadius must be positive" }
    val pointX = x.takeIf { it.isFinite() } ?: return null
    val pointY = y.takeIf { it.isFinite() } ?: return null
    var bestIndex = -1
    var bestDistance = Float.MAX_VALUE
    handles.forEachIndexed { index, handle ->
        val distance = hypot(handle.x - pointX, handle.y - pointY)
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    return bestIndex.takeIf { it >= 0 && bestDistance <= hitRadius }
}

data class FrequencyGridLine(
    val frequency: Double,
    val position: Float,
    val major: Boolean,
    val label: String,
)

data class DecibelGridLine(
    val db: Double,
    val position: Float,
    val major: Boolean,
    val label: String,
)

private fun formatFrequencyLabel(frequency: Double): String {
    if (frequency < 1000.0) {
        return frequency.roundToInt().toString()
    }
    val kilo = frequency / 1000.0
    val rounded = kilo.roundToInt()
    return if (abs(kilo - rounded) < 0.05) "${rounded}k" else "${(kilo * 10).roundToInt() / 10.0}k"
}

private fun formatDecibelLabel(db: Double): String {
    val rounded = db.roundToInt()
    return when {
        rounded > 0 -> "+$rounded"
        else -> rounded.toString()
    }
}

fun frequencyGridLines(
    minFrequency: Double,
    maxFrequency: Double,
): List<FrequencyGridLine> {
    require(minFrequency > 0.0 && maxFrequency > minFrequency)
    val startDecade = floor(log10(minFrequency)).toInt()
    val endDecade = ceil(log10(maxFrequency)).toInt()
    val lines = mutableListOf<FrequencyGridLine>()
    for (decade in startDecade..endDecade) {
        val base = 10.0.pow(decade)
        for (multiple in 1..9) {
            val frequency = base * multiple
            if (frequency < minFrequency || frequency > maxFrequency) continue
            lines += FrequencyGridLine(
                frequency = frequency,
                position = frequencyToX(frequency, minFrequency, maxFrequency),
                major = multiple == 1,
                label = formatFrequencyLabel(frequency),
            )
        }
    }
    return lines.sortedBy { it.position }
}

fun decibelGridLines(
    minDb: Double,
    maxDb: Double,
    step: Double,
): List<DecibelGridLine> {
    require(maxDb > minDb)
    require(step > 0.0)
    val lines = mutableListOf<DecibelGridLine>()
    var db = ceil(maxDb / step) * step
    while (db > maxDb) db -= step
    while (db >= minDb - 1e-9) {
        val value = if (abs(db) < 1e-9) 0.0 else db
        lines += DecibelGridLine(
            db = value,
            position = dbToY(value, minDb, maxDb),
            major = abs(value) < 1e-9,
            label = formatDecibelLabel(value),
        )
        db -= step
    }
    return lines
}

fun constrainCrossovers(
    values: List<Int>,
    changedIndex: Int,
    requestedFrequency: Int,
    minFrequency: Int = MULTIBAND_MIN_FREQUENCY,
    maxFrequency: Int = MULTIBAND_MAX_FREQUENCY,
): List<Int> =
    constrainMultibandCrossover(
        values = values,
        changedIndex = changedIndex,
        requestedFrequency = requestedFrequency,
        minFrequency = minFrequency,
        maxFrequency = maxFrequency,
    )

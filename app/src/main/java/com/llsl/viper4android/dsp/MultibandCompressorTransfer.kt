package com.llsl.viper4android.dsp

import androidx.compose.ui.geometry.Offset
import kotlin.math.pow
import kotlin.math.roundToInt

data class MultibandTransferSpec(
    val thresholdDb: Double,
    val ratioRaw: Int,
    val kneeDb: Double,
    val makeupGainDb: Double,
)

sealed interface MultibandRatioLabel {
    data class Conventional(
        val ratio: Double,
    ) : MultibandRatioLabel

    data object Limit : MultibandRatioLabel

    data class Over(
        val percent: Int,
    ) : MultibandRatioLabel
}

fun compressorOutputDb(
    inputDb: Double,
    spec: MultibandTransferSpec,
): Double =
    inputDb + spec.makeupGainDb -
        compressorReductionBaseDb(inputDb, spec) * (spec.ratioRaw.coerceIn(0, 200) / 100.0)

fun ratioCoefficientForOutput(
    inputDb: Double,
    outputDb: Double,
    spec: MultibandTransferSpec,
): Int {
    if (!inputDb.isFinite() || !outputDb.isFinite()) return spec.ratioRaw.coerceIn(0, 200)
    val reductionBase = compressorReductionBaseDb(inputDb, spec)
    if (reductionBase <= 1e-9) return spec.ratioRaw.coerceIn(0, 200)
    return (((inputDb + spec.makeupGainDb - outputDb) / reductionBase) * 100.0)
        .roundToInt()
        .coerceIn(0, 200)
}

fun multibandRatioLabel(rawRatio: Int): MultibandRatioLabel {
    val ratio = rawRatio.coerceIn(0, 200)
    return when {
        ratio < 100 -> MultibandRatioLabel.Conventional(100.0 / (100 - ratio))
        ratio == 100 -> MultibandRatioLabel.Limit
        else -> MultibandRatioLabel.Over(ratio - 100)
    }
}

fun multibandTransferCurve(
    spec: MultibandTransferSpec,
    sampleCount: Int = 121,
    minInputDb: Double = -60.0,
    maxInputDb: Double = 0.0,
    minOutputDb: Double = -60.0,
    maxOutputDb: Double = 24.0,
): List<Offset> {
    require(sampleCount >= 2)
    require(maxInputDb > minInputDb)
    require(maxOutputDb > minOutputDb)
    return List(sampleCount) { index ->
        val x = index.toDouble() / (sampleCount - 1)
        val inputDb = minInputDb + x * (maxInputDb - minInputDb)
        val outputDb = compressorOutputDb(inputDb, spec)
        val y = ((maxOutputDb - outputDb) / (maxOutputDb - minOutputDb)).coerceIn(0.0, 1.0)
        Offset(x.toFloat(), y.toFloat())
    }
}

private fun compressorReductionBaseDb(
    inputDb: Double,
    spec: MultibandTransferSpec,
): Double {
    val difference = inputDb - spec.thresholdDb
    if (spec.kneeDb <= 0.0) return difference.coerceAtLeast(0.0)
    val halfKnee = spec.kneeDb / 2.0
    return when {
        difference <= -halfKnee -> 0.0
        difference >= halfKnee -> difference
        else -> (difference + halfKnee).pow(2.0) / (2.0 * spec.kneeDb)
    }
}

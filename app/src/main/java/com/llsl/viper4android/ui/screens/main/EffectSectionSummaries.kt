package com.llsl.viper4android.ui.screens.main

import com.llsl.viper4android.effect.IemState
import java.io.File
import java.util.Locale
import kotlin.math.log10

internal fun basenameOrNone(
    path: String,
    noneLabel: String,
): String = path.takeIf(String::isNotBlank)?.let { File(it).name }.orEmpty().ifBlank { noneLabel }

internal fun formatConvolverSummary(
    kernelFile: String,
    wet: Int,
    crossDelay100Ns: Int,
    noneLabel: String,
    wetLabel: String,
): String =
    String.format(
        Locale.US,
        "%s · %s %d%% · %.4f ms",
        basenameOrNone(kernelFile, noneLabel),
        wetLabel,
        wet,
        crossDelay100Ns / 10000.0,
    )

internal fun formatOutputSummary(
    volume: Int,
    channelPan: Int,
    limiter: Int,
    limiterLabel: String,
): String {
    fun rawToDb(raw: Int): Double = if (raw > 0) 20.0 * log10(raw / 100.0) else -99.9

    val left = 50 - channelPan / 2
    val right = 50 + channelPan / 2
    return String.format(
        Locale.US,
        "%.1f dB · %d:%d · %s %.1f dB",
        rawToDb(volume),
        left,
        right,
        limiterLabel,
        rawToDb(limiter),
    )
}

internal fun formatMultiplier(rawHundredths: Int): String =
    String.format(Locale.US, "%.1fx", rawHundredths / 100.0)

internal fun joinEffectSummary(vararg values: String): String =
    values.filter(String::isNotBlank).joinToString(" · ")

internal fun shouldShowIemCard(aidlModeActive: Boolean): Boolean = !aidlModeActive

internal fun effectSectionOrder(): List<String> =
    listOf("convolver", "iem", "fieldSurround")

internal fun iemSummary(state: IemState): String {
    val mode =
        when (state.general.encoderMode) {
            1 -> "Multi"
            2 -> "Granular"
            3 -> "Halo"
            else -> "Stereo"
        }
    val order =
        when (state.general.order) {
            1 -> "1st"
            2 -> "2nd"
            else -> "3rd"
        }
    val render = when (state.general.renderMode) {
        0 -> "Off"
        1 -> "Simple"
        else -> "KU100"
    }
    return "$mode · $order order · $render"
}

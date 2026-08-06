package com.llsl.viper4android.effect

/**
 * Single source of truth for the FIR equalizer's fixed band frequencies.
 *
 * These tables mirror the driver exactly. The driver picks its filter coefficients from
 * the same fixed frequencies, so any divergence here means the UI labels a band index with
 * a frequency the DSP does not use.
 *
 * Driver reference: `ViPERFX_RE/ViPERDSP/viper/utils/MinPhaseIIRCoeffs.cpp:4-37`
 */
object EqBandSpec {
    private val FREQUENCIES_10 =
        listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)

    private val FREQUENCIES_15 =
        listOf(
            25.0, 40.0, 63.0, 100.0, 160.0, 250.0, 400.0, 630.0,
            1000.0, 1600.0, 2500.0, 4000.0, 6300.0, 10000.0, 16000.0,
        )

    private val FREQUENCIES_25 =
        listOf(
            20.0, 31.5, 40.0, 50.0, 80.0, 100.0, 125.0, 160.0, 250.0,
            315.0, 400.0, 500.0, 800.0, 1000.0, 1250.0, 1600.0, 2500.0, 3150.0,
            4000.0, 5000.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0,
        )

    private val FREQUENCIES_31 =
        listOf(
            20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0,
            125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0,
            800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0,
            5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0,
        )

    /** Supported band counts, matching the driver's fixed tables. */
    val supportedCounts = listOf(10, 15, 25, 31)

    /** Driver center frequencies in Hz. Unsupported counts fall back to the 10-band table. */
    fun frequenciesFor(count: Int): List<Double> =
        when (count) {
            15 -> FREQUENCIES_15
            25 -> FREQUENCIES_25
            31 -> FREQUENCIES_31
            else -> FREQUENCIES_10
        }

    /** Short axis labels derived from [frequenciesFor], so labels can never drift. */
    fun labelsFor(count: Int): List<String> = frequenciesFor(count).map(::formatFrequency)

    private fun formatFrequency(frequency: Double): String =
        if (frequency < 1000.0) {
            trimTrailingZero(frequency)
        } else {
            "${trimTrailingZero(frequency / 1000.0)}k"
        }

    private fun trimTrailingZero(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            rounded.toString().trimEnd('0').trimEnd('.')
        }
    }
}

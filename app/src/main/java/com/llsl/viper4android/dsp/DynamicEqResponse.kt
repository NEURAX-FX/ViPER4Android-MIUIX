package com.llsl.viper4android.dsp

/** One Dynamic EQ band as the driver sees it. */
data class DynamicEqBandSpec(
    val frequency: Double,
    val gainDb: Double,
    val q: Double,
    /** Driver filter type index: 0 = peak, 1 = low shelf, 2 = high shelf. */
    val filterType: Int,
)

/**
 * Magnitude response of the driver's Dynamic EQ.
 *
 * The driver chains one [BiquadResponse] per band, so the total transfer function is the
 * product of the per-band responses and magnitudes add in dB.
 *
 * The gain reported here is the band's *target* gain. At runtime the driver scales it by an
 * envelope follower relative to the band threshold, then smooths it with attack/release, so
 * the live response sits somewhere between flat and this curve. Callers must present this
 * as the maximum/target response rather than the instantaneous one.
 *
 * Driver references:
 * - `ViPERFX_RE/ViPERDSP/viper/effects/DynamicEQ.cpp`
 * - `ViPERFX_RE/ViPERDSP/viper/utils/MultiBiquad.cpp`
 */
class DynamicEqResponse(val sampleRate: Int) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
    }

    fun magnitudeDb(bands: List<DynamicEqBandSpec>, frequency: Double): Double {
        if (bands.isEmpty()) return 0.0
        requireAudibleFrequency(frequency, sampleRate)
        var total = 0.0
        bands.forEach { band ->
            if (band.gainDb == 0.0) return@forEach
            if (band.frequency <= 0.0 || band.frequency >= sampleRate / 2.0) return@forEach
            if (band.q <= 0.0) return@forEach
            total += filterFor(band).magnitudeDb(frequency)
        }
        return total
    }

    private fun filterFor(band: DynamicEqBandSpec): BiquadResponse =
        BiquadResponse.of(
            type = typeFor(band.filterType),
            gainDb = band.gainDb,
            frequency = band.frequency,
            q = band.q,
            sampleRate = sampleRate,
        )

    private companion object {
        /** Matches `DynamicEQ::SetBandFilterType`: unknown indices fall back to peak. */
        fun typeFor(filterType: Int): BiquadResponse.Companion.Type =
            when (filterType) {
                1 -> BiquadResponse.Companion.Type.LOW_SHELF
                2 -> BiquadResponse.Companion.Type.HIGH_SHELF
                else -> BiquadResponse.Companion.Type.PEAK
            }
    }
}

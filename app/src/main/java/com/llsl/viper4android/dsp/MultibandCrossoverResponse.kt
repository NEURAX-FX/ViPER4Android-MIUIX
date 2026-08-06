package com.llsl.viper4android.dsp

/**
 * Static per-band magnitude response of the driver's multiband compressor crossover.
 *
 * The driver splits the signal with fourth-order slopes: each crossover stage is two
 * identical second-order Butterworth sections in series (`Q = 0.70710678`). The lowest band
 * is low-passed at the first crossover, the highest band is high-passed at the last one, and
 * every middle band is high-passed at its lower crossover then low-passed at its upper one.
 *
 * This is the *structural* response only. The driver applies a stateful FET compressor per
 * band afterwards, so the live magnitude also depends on program material, thresholds and
 * auto attack/release. Callers must not present this as the compressed response.
 *
 * Driver reference: `ViPERFX_RE/ViPERDSP/viper/effects/MultibandCompressor.cpp`
 */
class MultibandCrossoverResponse(
    crossovers: List<Double>,
    val sampleRate: Int,
) {
    /** Butterworth Q used by the driver for every crossover section. */
    private val butterworthQ = 0.70710678

    private val crossovers: List<Double> = crossovers.sorted()

    val bandCount: Int = this.crossovers.size + 1

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        require(this.crossovers.all { it > 0.0 && it < sampleRate / 2.0 }) {
            "crossovers must be positive and below Nyquist, was $crossovers"
        }
    }

    /** Magnitude of band [band] at [frequency], in dB. */
    fun bandMagnitudeDb(band: Int, frequency: Double): Double =
        amplitudeToDb(bandMagnitude(band, frequency))

    fun bandMagnitude(band: Int, frequency: Double): Double {
        require(band in 0 until bandCount) { "band $band outside 0..${bandCount - 1}" }
        requireAudibleFrequency(frequency, sampleRate)

        var magnitude = 1.0
        crossovers.getOrNull(band - 1)?.let { lower ->
            // Two cascaded high passes at the lower crossover.
            val stage = BiquadResponse.highPass(lower, butterworthQ, sampleRate).magnitude(frequency)
            magnitude *= stage * stage
        }
        crossovers.getOrNull(band)?.let { upper ->
            // Two cascaded low passes at the upper crossover.
            val stage = BiquadResponse.lowPass(upper, butterworthQ, sampleRate).magnitude(frequency)
            magnitude *= stage * stage
        }
        return magnitude
    }

    /** Magnitude of all bands summed, in dB. Useful for showing crossover flatness. */
    fun sumMagnitudeDb(frequency: Double): Double {
        requireAudibleFrequency(frequency, sampleRate)
        var total = 0.0
        for (band in 0 until bandCount) {
            total += bandMagnitude(band, frequency)
        }
        return amplitudeToDb(total)
    }
}

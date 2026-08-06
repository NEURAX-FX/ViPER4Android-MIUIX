package com.llsl.viper4android.dsp

import com.llsl.viper4android.effect.EqBandSpec
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Magnitude response of the driver's FIR equalizer.
 *
 * The ViPER driver does not cascade shelving filters like LSP's graphic equalizer. It runs
 * a bank of second-order IIR band-passes in *parallel* and sums their outputs, each scaled
 * by `10^(gain/20) * 0.636`. Reproducing that structure is the only way the preview curve
 * can match what the DSP actually does.
 *
 * Driver references:
 * - `ViPERFX_RE/ViPERDSP/viper/utils/MinPhaseIIRCoeffs.cpp` (coefficient generation)
 * - `ViPERFX_RE/ViPERDSP/viper/effects/IIRFilter.cpp` (recurrence and band levels)
 */
class FirEqualizerResponse(
    val bandCount: Int,
    val sampleRate: Int,
) {
    /** Driver band level scaling, from `IIRFilter::SetBandLevel`. */
    private val levelScale = 0.636

    private val frequencies: List<Double> = EqBandSpec.frequenciesFor(bandCount)

    /** One `(c1, c2, c3)` triple per band, matching the driver's `coeffs_[i*4 + n]`. */
    private val coefficients: List<Triple<Double, Double, Double>>

    init {
        require(sampleRate > 0) { "sampleRate must be positive, was $sampleRate" }
        coefficients = frequencies.map { frequency ->
            computeCoefficients(frequency, bandwidthOctaves(bandCount), sampleRate)
        }
    }

    /**
     * Combined magnitude at [frequency] in dB.
     *
     * [gainsDb] is indexed by band. Missing entries are treated as 0 dB so a short
     * persisted list cannot crash the preview.
     */
    fun magnitudeDb(gainsDb: List<Double>, frequency: Double): Double =
        amplitudeToDb(magnitude(gainsDb, frequency))

    fun magnitude(gainsDb: List<Double>, frequency: Double): Double {
        requireAudibleFrequency(frequency, sampleRate)
        val z = unitDelay(frequency, sampleRate)
        val zz = z * z
        var total = Complex.ZERO
        coefficients.forEachIndexed { index, (c1, c2, c3) ->
            // H(z) = c2 * (1 - z^-2) / (1 - c3*z^-1 + c1*z^-2)
            val numerator = (Complex.ONE - zz) * c2
            val denominator = Complex.ONE - z * c3 + zz * c1
            val level = levelScale * 10.0.pow(gainsDb.getOrElse(index) { 0.0 } / 20.0)
            total += (numerator / denominator) * level
        }
        return total.magnitude
    }

    private companion object {
        /** Per-band bandwidth in octaves, from `MinPhaseIIRCoeffs::UpdateCoeffs`. */
        fun bandwidthOctaves(bandCount: Int): Double =
            when (bandCount) {
                15 -> 2.0 / 3.0
                25, 31 -> 1.0 / 3.0
                else -> 1.0
            }

        /**
         * Port of `MinPhaseIIRCoeffs::UpdateCoeffs` for a single band.
         *
         * Returns `(c1, c2, c3)` where `c1 = coeffs_[i*4]`, `c2 = coeffs_[i*4+1]` and
         * `c3 = coeffs_[i*4+2]`.
         */
        fun computeCoefficients(
            centerFrequency: Double,
            bandwidthOctaves: Double,
            sampleRate: Int,
        ): Triple<Double, Double, Double> {
            // Find_F1_F2: lower_freq is passed as ret2 and used for the second angle.
            val spread = 2.0.pow(bandwidthOctaves / 2.0)
            val lowerFrequency = centerFrequency / spread

            val x = 2.0 * Math.PI * centerFrequency / sampleRate
            val y = 2.0 * Math.PI * lowerFrequency / sampleRate

            val cosX = cos(x)
            val cosY = cos(y)
            val sinY = sin(y)

            val a = cosX * cosY
            val b = cosX * cosX / 2.0
            val c = sinY * sinY

            val quadA = b - a + 0.5 - c
            val quadB = c + (b + cosY * cosY - a - 0.5)
            val quadC = cosX * cosX * 0.125 - cosX * cosY * 0.25 + 0.125 - c * 0.25

            val root = solveRoot(quadA, quadB, quadC)
                ?: return Triple(0.0, 0.0, 0.0)

            return Triple(root + root, 0.5 - root, (root + 0.5) * cosX * 2.0)
        }

        /** Port of `MinPhaseIIRCoeffs::SolveRoot`; returns null when the driver bails out. */
        fun solveRoot(a: Double, b: Double, c: Double): Double? {
            if (a == 0.0) return null
            val x = (c - b * b / (a * 4.0)) / a
            val y = b / (a + a)
            if (x >= 0.0) return null
            val z = sqrt(-x)
            val first = -y - z
            val second = z - y
            return if (first > second) second else first
        }
    }
}

package com.llsl.viper4android.dsp

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Complex value helper kept internal to the DSP package.
 *
 * The driver evaluates its filters as rational functions of `z = e^(-j*omega)`, so a
 * minimal complex type is all that is needed to reproduce its magnitude response without
 * pulling in an external numerics dependency.
 */
internal data class Complex(val re: Double, val im: Double) {
    operator fun plus(other: Complex) = Complex(re + other.re, im + other.im)

    operator fun minus(other: Complex) = Complex(re - other.re, im - other.im)

    operator fun times(other: Complex) =
        Complex(re * other.re - im * other.im, re * other.im + im * other.re)

    operator fun times(scalar: Double) = Complex(re * scalar, im * scalar)

    operator fun div(other: Complex): Complex {
        val denominator = other.re * other.re + other.im * other.im
        return Complex(
            (re * other.re + im * other.im) / denominator,
            (im * other.re - re * other.im) / denominator,
        )
    }

    val magnitude: Double get() = sqrt(re * re + im * im)

    companion object {
        val ONE = Complex(1.0, 0.0)
        val ZERO = Complex(0.0, 0.0)
    }
}

/** `z^-1` evaluated on the unit circle at [frequency] Hz for the given [sampleRate]. */
internal fun unitDelay(frequency: Double, sampleRate: Int): Complex {
    val omega = -2.0 * Math.PI * frequency / sampleRate
    return Complex(cos(omega), sin(omega))
}

internal fun amplitudeToDb(amplitude: Double): Double =
    if (amplitude <= 0.0) Double.NEGATIVE_INFINITY else 20.0 * log10(amplitude)

/**
 * Magnitude response of one biquad section, using the driver's own coefficient layout.
 *
 * Driver reference: `ViPERFX_RE/ViPERDSP/viper/utils/MultiBiquad.cpp`. The driver stores
 * `a1_`/`a2_` already negated and its difference equation adds the feedback terms, so the
 * denominator here is `1 - a1*z^-1 - a2*z^-2` to match.
 */
class BiquadResponse internal constructor(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
    private val sampleRate: Int,
) {
    fun magnitude(frequency: Double): Double {
        val z = unitDelay(frequency, sampleRate)
        val zz = z * z
        val numerator = Complex(b0, 0.0) + z * b1 + zz * b2
        val denominator = Complex.ONE - z * a1 - zz * a2
        return (numerator / denominator).magnitude
    }

    fun magnitudeDb(frequency: Double): Double = amplitudeToDb(magnitude(frequency))

    companion object {
        /** Filter shapes the driver's Dynamic EQ can select. */
        enum class Type { LOW_PASS, HIGH_PASS, PEAK, LOW_SHELF, HIGH_SHELF }

        /**
         * Direct port of `MultiBiquad::RefreshFilter` with `is_bandwidth = false`, which is
         * the only mode the driver's Dynamic EQ and multiband crossovers use.
         */
        fun of(
            type: Type,
            gainDb: Double,
            frequency: Double,
            q: Double,
            sampleRate: Int,
        ): BiquadResponse {
            val gain = when (type) {
                Type.PEAK, Type.LOW_SHELF, Type.HIGH_SHELF -> 10.0.pow(gainDb / 40.0)
                else -> 10.0.pow(gainDb / 20.0)
            }
            val omega = 2.0 * Math.PI * frequency / sampleRate
            val sinOmega = sin(omega)
            val cosOmega = cos(omega)

            val y: Double
            val z: Double
            if (type == Type.LOW_SHELF || type == Type.HIGH_SHELF) {
                y = sinOmega / 2.0 * sqrt((1.0 / gain + gain) * (1.0 / q - 1.0) + 2.0)
                z = sqrt(gain) * 2.0 * y
            } else {
                y = sinOmega / (q + q)
                z = -1.0
            }

            var a0: Double
            var a1: Double
            var a2: Double
            var b0: Double
            var b1: Double
            var b2: Double

            when (type) {
                Type.LOW_PASS -> {
                    a0 = 1.0 + y
                    a1 = -2.0 * cosOmega
                    a2 = 1.0 - y
                    b0 = (1.0 - cosOmega) / 2.0
                    b1 = 1.0 - cosOmega
                    b2 = (1.0 - cosOmega) / 2.0
                }
                Type.HIGH_PASS -> {
                    a0 = 1.0 + y
                    a1 = -2.0 * cosOmega
                    a2 = 1.0 - y
                    b0 = (1.0 + cosOmega) / 2.0
                    b1 = -(1.0 + cosOmega)
                    b2 = (1.0 + cosOmega) / 2.0
                }
                Type.PEAK -> {
                    a0 = 1.0 + y / gain
                    a1 = -2.0 * cosOmega
                    a2 = 1.0 - y / gain
                    b0 = 1.0 + y * gain
                    b1 = -2.0 * cosOmega
                    b2 = 1.0 - y * gain
                }
                Type.LOW_SHELF -> {
                    val tmp1 = gain + 1.0 - (gain - 1.0) * cosOmega
                    val tmp2 = gain + 1.0 + (gain - 1.0) * cosOmega
                    a1 = (gain - 1.0 + (gain + 1.0) * cosOmega) * -2.0
                    a2 = tmp2 - z
                    b1 = gain * 2.0 * (gain - 1.0 - (gain + 1.0) * cosOmega)
                    a0 = tmp2 + z
                    b0 = (tmp1 + z) * gain
                    b2 = (tmp1 - z) * gain
                }
                Type.HIGH_SHELF -> {
                    val tmp1 = gain + 1.0 + (gain - 1.0) * cosOmega
                    val tmp2 = gain + 1.0 - (gain - 1.0) * cosOmega
                    a2 = tmp2 - z
                    a0 = tmp2 + z
                    a1 = (gain - 1.0 - (gain + 1.0) * cosOmega) * 2.0
                    b1 = gain * -2.0 * (gain - 1.0 + (gain + 1.0) * cosOmega)
                    b0 = (tmp1 + z) * gain
                    b2 = (tmp1 - z) * gain
                }
            }

            return BiquadResponse(
                b0 = b0 / a0,
                b1 = b1 / a0,
                b2 = b2 / a0,
                a1 = -(a1 / a0),
                a2 = -(a2 / a0),
                sampleRate = sampleRate,
            )
        }

        fun peak(gainDb: Double, frequency: Double, q: Double, sampleRate: Int) =
            of(Type.PEAK, gainDb, frequency, q, sampleRate)

        fun lowShelf(gainDb: Double, frequency: Double, q: Double, sampleRate: Int) =
            of(Type.LOW_SHELF, gainDb, frequency, q, sampleRate)

        fun highShelf(gainDb: Double, frequency: Double, q: Double, sampleRate: Int) =
            of(Type.HIGH_SHELF, gainDb, frequency, q, sampleRate)

        fun lowPass(frequency: Double, q: Double, sampleRate: Int) =
            of(Type.LOW_PASS, 1.0, frequency, q, sampleRate)

        fun highPass(frequency: Double, q: Double, sampleRate: Int) =
            of(Type.HIGH_PASS, 1.0, frequency, q, sampleRate)
    }
}

/** Shared guard so callers cannot ask for a response at or beyond Nyquist. */
internal fun requireAudibleFrequency(frequency: Double, sampleRate: Int) {
    require(frequency > 0.0) { "frequency must be positive, was $frequency" }
    require(frequency < sampleRate / 2.0) {
        "frequency $frequency must stay below Nyquist for sample rate $sampleRate"
    }
}

internal fun approximatelyEqual(a: Double, b: Double, epsilon: Double = 1e-12): Boolean =
    abs(a - b) <= epsilon

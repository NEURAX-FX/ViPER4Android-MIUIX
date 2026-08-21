package com.llsl.viper4android.viper

/**
 * Destination for raw ViPER parameter writes.
 *
 * `ViperDispatcher` owns the single mapping from `EffectState` to raw parameters.
 * Both backends consume that one mapping through this interface: `ViperEffect`
 * writes straight to the `AudioEffect`, while the daemon backend records the same
 * writes into a snapshot. A second copy of the mapping would drift.
 *
 * The overloads mirror the `effect_param_t` shapes the driver accepts: 1, 2 or 3
 * ints, or a byte array whose layout is parameter-specific.
 */
interface ParamSink {
    fun setParameter(
        param: Int,
        value: Int,
    )

    fun setParameter(
        param: Int,
        val1: Int,
        val2: Int,
    )

    fun setParameter(
        param: Int,
        val1: Int,
        val2: Int,
        val3: Int,
    )

    fun setParameter(
        param: Int,
        value: ByteArray,
    )
}

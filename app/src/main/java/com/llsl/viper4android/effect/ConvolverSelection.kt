package com.llsl.viper4android.effect

fun resolveConvolverKernel(
    current: String,
    available: List<String>,
): String? = current.takeIf(available::contains) ?: available.firstOrNull()

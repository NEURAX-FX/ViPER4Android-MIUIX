package com.llsl.viper4android.effect

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

const val MULTIBAND_BAND_COUNT = 5
const val MULTIBAND_CROSSOVER_COUNT = 4
const val MULTIBAND_MIN_FREQUENCY = 30
const val MULTIBAND_MAX_FREQUENCY = 16_000
val MULTIBAND_SPACING_RATIO: Double = 2.0.pow(1.0 / 12.0)

fun normalizeMultibandCompressorState(
    state: MultibandCompressorState,
    maxCrossoverFrequency: Int = MULTIBAND_MAX_FREQUENCY,
): MultibandCompressorState {
    val defaults = MultibandCompressorState()
    return state.copy(
        bandEnables = normalizeList(state.bandEnables, defaults.bandEnables),
        crossovers = normalizeMultibandCrossovers(state.crossovers, maxCrossoverFrequency),
        thresholds = normalizeIntList(state.thresholds, defaults.thresholds, -48..0),
        ratios = normalizeIntList(state.ratios, defaults.ratios, 0..200),
        gains = normalizeIntList(state.gains, defaults.gains, 0..24),
        knees = normalizeIntList(state.knees, defaults.knees, 0..12),
        kneeMultis = normalizeIntList(state.kneeMultis, defaults.kneeMultis, 0..100),
        attacks = normalizeIntList(state.attacks, defaults.attacks, 1..100),
        maxAttacks = normalizeIntList(state.maxAttacks, defaults.maxAttacks, 1..100),
        releases = normalizeIntList(state.releases, defaults.releases, 5..500),
        maxReleases = normalizeIntList(state.maxReleases, defaults.maxReleases, 5..500),
        crests = normalizeIntList(state.crests, defaults.crests, 5..300),
        adapts = normalizeIntList(state.adapts, defaults.adapts, 0..200),
        kneeAutos = normalizeList(state.kneeAutos, defaults.kneeAutos),
        gainAutos = normalizeList(state.gainAutos, defaults.gainAutos),
        attackAutos = normalizeList(state.attackAutos, defaults.attackAutos),
        releaseAutos = normalizeList(state.releaseAutos, defaults.releaseAutos),
        noClips = normalizeList(state.noClips, defaults.noClips),
    )
}

fun normalizeMultibandCrossovers(
    values: List<Int>,
    maxFrequency: Int = MULTIBAND_MAX_FREQUENCY,
): List<Int> {
    require(maxFrequency > MULTIBAND_MIN_FREQUENCY)
    val defaults = MultibandCompressorState().crossovers
    val minimums = MutableList(MULTIBAND_CROSSOVER_COUNT) { MULTIBAND_MIN_FREQUENCY }
    val maximums = MutableList(MULTIBAND_CROSSOVER_COUNT) { maxFrequency }
    for (index in 1 until MULTIBAND_CROSSOVER_COUNT) {
        minimums[index] = ceil(minimums[index - 1] * MULTIBAND_SPACING_RATIO).toInt()
    }
    for (index in MULTIBAND_CROSSOVER_COUNT - 2 downTo 0) {
        maximums[index] = floor(maximums[index + 1] / MULTIBAND_SPACING_RATIO).toInt()
    }
    require(minimums.indices.all { minimums[it] <= maximums[it] }) {
        "Crossover range is too small for $MULTIBAND_CROSSOVER_COUNT bands"
    }

    val normalized =
        MutableList(MULTIBAND_CROSSOVER_COUNT) { index ->
            (values.getOrNull(index) ?: defaults[index]).coerceIn(minimums[index], maximums[index])
        }
    for (index in 1 until MULTIBAND_CROSSOVER_COUNT) {
        val lower = ceil(normalized[index - 1] * MULTIBAND_SPACING_RATIO).toInt()
        normalized[index] = normalized[index].coerceIn(lower, maximums[index])
    }
    for (index in MULTIBAND_CROSSOVER_COUNT - 2 downTo 0) {
        val upper = floor(normalized[index + 1] / MULTIBAND_SPACING_RATIO).toInt()
        normalized[index] = normalized[index].coerceIn(minimums[index], upper)
    }
    return normalized
}

fun constrainMultibandCrossover(
    values: List<Int>,
    changedIndex: Int,
    requestedFrequency: Int,
    minFrequency: Int = MULTIBAND_MIN_FREQUENCY,
    maxFrequency: Int = MULTIBAND_MAX_FREQUENCY,
): List<Int> {
    require(changedIndex in values.indices)
    require(minFrequency > 0 && maxFrequency > minFrequency)
    val lower =
        if (changedIndex == 0) {
            minFrequency
        } else {
            ceil(values[changedIndex - 1] * MULTIBAND_SPACING_RATIO).toInt()
        }
    val upper =
        if (changedIndex == values.lastIndex) {
            maxFrequency
        } else {
            floor(values[changedIndex + 1] / MULTIBAND_SPACING_RATIO).toInt()
        }
    val constrained = requestedFrequency.coerceIn(lower.coerceAtMost(upper), upper.coerceAtLeast(lower))
    return values.toMutableList().also { it[changedIndex] = constrained }
}

private fun normalizeIntList(
    values: List<Int>,
    defaults: List<Int>,
    range: IntRange,
): List<Int> =
    List(MULTIBAND_BAND_COUNT) { index ->
        (values.getOrNull(index) ?: defaults[index]).coerceIn(range)
    }

private fun <T> normalizeList(
    values: List<T>,
    defaults: List<T>,
): List<T> = List(MULTIBAND_BAND_COUNT) { index -> values.getOrNull(index) ?: defaults[index] }

package com.llsl.viper4android.dsp

import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.MULTIBAND_BAND_COUNT
import com.llsl.viper4android.effect.normalizeMultibandCompressorState

/**
 * Shared, UI-agnostic graph geometry derived from [EffectState].
 *
 * The home screen preview and the dedicated editor both build their graphs from these
 * models. Previously each screen derived its own curve, which is how the preview ended up
 * drawing an average-of-all-values straight line while the editor drew something else for
 * the same effect.
 *
 * Handles use normalized graph coordinates so nothing here depends on Compose.
 */
data class GraphHandlePoint(
    val id: String,
    val x: Float,
    val y: Float,
    val label: String,
    val valueDescription: String,
)

/** Axis bounds every model shares, so screens cannot disagree on scaling. */
sealed interface EffectGraphModel {
    val sampleRate: Int
    val minFrequency: Double
    val maxFrequency: Double
    val minDb: Double
    val maxDb: Double
    val handles: List<GraphHandlePoint>
}

data class FirGraphModel(
    override val sampleRate: Int,
    override val minFrequency: Double,
    override val maxFrequency: Double,
    override val minDb: Double,
    override val maxDb: Double,
    override val handles: List<GraphHandlePoint>,
    val bandCount: Int,
    val frequencies: List<Double>,
    val labels: List<String>,
    val gainsDb: List<Double>,
    val curve: List<androidx.compose.ui.geometry.Offset>,
) : EffectGraphModel

data class DynamicEqGraphModel(
    override val sampleRate: Int,
    override val minFrequency: Double,
    override val maxFrequency: Double,
    override val minDb: Double,
    override val maxDb: Double,
    override val handles: List<GraphHandlePoint>,
    val bands: List<DynamicEqBandSpec>,
    val curve: List<androidx.compose.ui.geometry.Offset>,
) : EffectGraphModel

data class MultibandGraphModel(
    override val sampleRate: Int,
    override val minFrequency: Double,
    override val maxFrequency: Double,
    override val minDb: Double,
    override val maxDb: Double,
    override val handles: List<GraphHandlePoint>,
    val crossovers: List<Double>,
    val gainsDb: List<Double>,
    val gainAutos: List<Boolean>,
    val bandEnables: List<Boolean>,
    val bandCurves: List<List<androidx.compose.ui.geometry.Offset>>,
    val unitySumCurve: List<androidx.compose.ui.geometry.Offset>,
    val bandRegions: List<GraphBandRange>,
) : EffectGraphModel

data class GraphBandRange(
    val startX: Float,
    val endX: Float,
    val bandIndex: Int,
)

private const val FIR_MIN_DB = -12.0
private const val FIR_MAX_DB = 12.0
private const val DYNAMIC_MIN_DB = -12.0
private const val DYNAMIC_MAX_DB = 12.0
private const val MBC_MIN_DB = -48.0
private const val MBC_MAX_DB = 24.0

fun firGraphModel(state: EffectState, sampleRate: Int): FirGraphModel {
    val safeRate = sanitizeGraphSampleRate(sampleRate)
    val maxFrequency = graphMaxFrequency(safeRate)
    val bandCount = state.eq.bandCount
    val gains = state.eq.bands.take(bandCount)
    val frequencies = com.llsl.viper4android.effect.EqBandSpec.frequenciesFor(bandCount)
    val labels = com.llsl.viper4android.effect.EqBandSpec.labelsFor(bandCount)
    val handles = gains.mapIndexed { index, gain ->
        GraphHandlePoint(
            id = index.toString(),
            x = graphFrequencyToX(frequencies.getOrElse(index) { frequencies.last() }, safeRate),
            y = graphDbToY(gain, FIR_MIN_DB, FIR_MAX_DB),
            label = "${labels.getOrElse(index) { "" }} Hz",
            valueDescription = "%.1f dB".format(gain),
        )
    }
    return FirGraphModel(
        sampleRate = safeRate,
        minFrequency = GRAPH_MIN_FREQUENCY,
        maxFrequency = maxFrequency,
        minDb = FIR_MIN_DB,
        maxDb = FIR_MAX_DB,
        handles = handles,
        bandCount = bandCount,
        frequencies = frequencies,
        labels = labels,
        gainsDb = gains,
        curve = firCurvePoints(bandCount, gains, safeRate, FIR_MIN_DB, FIR_MAX_DB),
    )
}

fun dynamicEqGraphModel(state: EffectState, sampleRate: Int): DynamicEqGraphModel {
    val safeRate = sanitizeGraphSampleRate(sampleRate)
    val dynamic = state.dynamicEq
    // A malformed persisted state can claim more bands than its lists hold.
    val count = minOf(dynamic.bandCount, dynamic.freqs.size, dynamic.gains.size).coerceAtLeast(0)
    val bands = (0 until count).map { band ->
        DynamicEqBandSpec(
            frequency = dynamic.freqs[band].toDouble(),
            gainDb = dynamic.gains[band] / 10.0,
            q = dynamic.qs.getOrElse(band) { 100 } / 100.0,
            filterType = dynamic.filterTypes.getOrElse(band) { 0 },
        )
    }
    val handles = bands.mapIndexed { band, spec ->
        GraphHandlePoint(
            id = band.toString(),
            x = graphFrequencyToX(spec.frequency, safeRate),
            y = graphDbToY(spec.gainDb, DYNAMIC_MIN_DB, DYNAMIC_MAX_DB),
            label = "Band ${band + 1}",
            valueDescription = "%.0f Hz, %.1f dB".format(spec.frequency, spec.gainDb),
        )
    }
    return DynamicEqGraphModel(
        sampleRate = safeRate,
        minFrequency = GRAPH_MIN_FREQUENCY,
        maxFrequency = graphMaxFrequency(safeRate),
        minDb = DYNAMIC_MIN_DB,
        maxDb = DYNAMIC_MAX_DB,
        handles = handles,
        bands = bands,
        curve = dynamicEqCurvePoints(bands, safeRate, DYNAMIC_MIN_DB, DYNAMIC_MAX_DB),
    )
}

fun multibandGraphModel(state: EffectState, sampleRate: Int): MultibandGraphModel {
    val safeRate = sanitizeGraphSampleRate(sampleRate)
    val maxFrequency = graphMaxFrequency(safeRate)
    val compressor =
        normalizeMultibandCompressorState(
            state.multibandCompressor,
            maxCrossoverFrequency = safeMultibandCrossoverMax(safeRate),
        )
    val crossovers = compressor.crossovers.map(Int::toDouble)
    val gains = compressor.gains.map(Int::toDouble)
    val crossoverHandles = crossovers.mapIndexed { index, crossover ->
        GraphHandlePoint(
            id = "crossover-$index",
            x = graphFrequencyToX(crossover, safeRate),
            y = graphDbToY(gains[index], MBC_MIN_DB, MBC_MAX_DB),
            label = "Crossover ${index + 1}",
            valueDescription = "%.0f Hz, %.0f dB".format(crossover, gains[index]),
        )
    }
    val regionBoundaries = listOf(0f) + crossoverHandles.map { it.x } + listOf(1f)
    val bandRegions =
        List(MULTIBAND_BAND_COUNT) { band ->
            GraphBandRange(
                startX = regionBoundaries[band],
                endX = regionBoundaries[band + 1],
                bandIndex = band,
            )
        }
    val plottedGains =
        List(MULTIBAND_BAND_COUNT) { band ->
            if (compressor.gainAutos[band]) 0.0 else gains[band]
        }
    return MultibandGraphModel(
        sampleRate = safeRate,
        minFrequency = GRAPH_MIN_FREQUENCY,
        maxFrequency = maxFrequency,
        minDb = MBC_MIN_DB,
        maxDb = MBC_MAX_DB,
        handles = crossoverHandles,
        crossovers = crossovers,
        gainsDb = gains,
        gainAutos = compressor.gainAutos,
        bandEnables = compressor.bandEnables,
        bandCurves =
            multibandCrossoverCurves(
                crossovers = crossovers,
                sampleRate = safeRate,
                minDb = MBC_MIN_DB,
                maxDb = MBC_MAX_DB,
                bandGainsDb = plottedGains,
            ),
        unitySumCurve =
            multibandCrossoverSumCurve(
                crossovers = crossovers,
                sampleRate = safeRate,
                minDb = MBC_MIN_DB,
                maxDb = MBC_MAX_DB,
            ),
        bandRegions = bandRegions,
    )
}

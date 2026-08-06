package com.llsl.viper4android.ui.screens.editor

import com.llsl.viper4android.dsp.MultibandGraphModel
import com.llsl.viper4android.dsp.MultibandTransferSpec
import com.llsl.viper4android.dsp.compressorOutputDb
import com.llsl.viper4android.dsp.multibandTransferCurve
import com.llsl.viper4android.dsp.safeMultibandCrossoverMax
import com.llsl.viper4android.dsp.multibandGraphModel
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.normalizeMultibandCompressorState
import com.llsl.viper4android.ui.components.viper.GraphDragAxis

data class MultibandBandPresentation(
    val index: Int,
    val compressionEnabled: Boolean,
    val thresholdDb: Int,
    val gainDb: Int,
    val gainAuto: Boolean,
)

data class MultibandCrossoverHandlePresentation(
    val id: String,
    val crossoverIndex: Int,
    val controlledBand: Int,
    val x: Float,
    val y: Float,
    val dragAxis: GraphDragAxis,
    val badge: String?,
    val frequencyHz: Int,
    val gainDb: Int,
)

data class MultibandEditorPresentation(
    val graph: MultibandGraphModel,
    val bands: List<MultibandBandPresentation>,
    val crossoverHandles: List<MultibandCrossoverHandlePresentation>,
    val bandRegions: List<GraphBandRegion>,
)

data class MultibandTransferHandlePresentation(
    val id: String,
    val x: Float,
    val y: Float,
    val dragAxis: GraphDragAxis,
    val enabled: Boolean,
    val badge: String?,
)

data class MultibandControlAvailability(
    val thresholdEnabled: Boolean,
    val ratioEnabled: Boolean,
    val kneeEnabled: Boolean,
    val gainEnabled: Boolean,
    val attackEnabled: Boolean,
    val releaseEnabled: Boolean,
)

data class MultibandTransferPresentation(
    val transferSpec: MultibandTransferSpec,
    val curve: List<androidx.compose.ui.geometry.Offset>,
    val referenceCurve: List<androidx.compose.ui.geometry.Offset>,
    val handles: List<MultibandTransferHandlePresentation>,
    val controls: MultibandControlAvailability,
    val curveDashed: Boolean,
)

fun multibandEditorPresentation(
    state: EffectState,
    sampleRate: Int,
): MultibandEditorPresentation {
    val compressor =
        normalizeMultibandCompressorState(
            state.multibandCompressor,
            maxCrossoverFrequency = safeMultibandCrossoverMax(sampleRate),
        )
    val graph = multibandGraphModel(state.copy(multibandCompressor = compressor), sampleRate)
    val bands =
        compressor.bandEnables.indices.map { index ->
            MultibandBandPresentation(
                index = index,
                compressionEnabled = compressor.bandEnables[index],
                thresholdDb = compressor.thresholds[index],
                gainDb = compressor.gains[index],
                gainAuto = compressor.gainAutos[index],
            )
        }
    val crossoverHandles =
        graph.handles.mapIndexed { index, handle ->
            MultibandCrossoverHandlePresentation(
                id = handle.id,
                crossoverIndex = index,
                controlledBand = index,
                x = handle.x,
                y = handle.y,
                dragAxis = if (compressor.gainAutos[index]) GraphDragAxis.HORIZONTAL else GraphDragAxis.FREE,
                badge = "AUTO".takeIf { compressor.gainAutos[index] },
                frequencyHz = compressor.crossovers[index],
                gainDb = compressor.gains[index],
            )
        }
    return MultibandEditorPresentation(
        graph = graph,
        bands = bands,
        crossoverHandles = crossoverHandles,
        bandRegions =
            graph.bandRegions.map { region ->
                GraphBandRegion(
                    startX = region.startX,
                    endX = region.endX,
                )
            },
    )
}

fun multibandTransferPresentation(
    state: EffectState,
    band: Int,
): MultibandTransferPresentation {
    val compressor = normalizeMultibandCompressorState(state.multibandCompressor)
    val selectedBand = band.coerceIn(0 until compressor.bandEnables.size)
    val spec =
        MultibandTransferSpec(
            thresholdDb = compressor.thresholds[selectedBand].toDouble(),
            ratioRaw = compressor.ratios[selectedBand],
            kneeDb = compressor.knees[selectedBand].toDouble(),
            makeupGainDb =
                if (compressor.gainAutos[selectedBand]) 0.0 else compressor.gains[selectedBand].toDouble(),
        )
    val inputMin = -60.0
    val inputMax = 0.0
    val outputMin = -60.0
    val outputMax = 24.0
    val thresholdInput = spec.thresholdDb.coerceIn(inputMin, inputMax)
    val ratioInput = inputMax
    val kneeInput = (spec.thresholdDb + spec.kneeDb / 2.0).coerceIn(inputMin, inputMax)
    val handles =
        listOf(
            MultibandTransferHandlePresentation(
                id = "threshold",
                x = linearValueToX(thresholdInput, inputMin, inputMax),
                y = linearValueToY(compressorOutputDb(thresholdInput, spec), outputMin, outputMax),
                dragAxis = GraphDragAxis.HORIZONTAL,
                enabled = true,
                badge = null,
            ),
            MultibandTransferHandlePresentation(
                id = "ratio",
                x = linearValueToX(ratioInput, inputMin, inputMax),
                y = linearValueToY(compressorOutputDb(ratioInput, spec), outputMin, outputMax),
                dragAxis = GraphDragAxis.VERTICAL,
                enabled = !compressor.kneeAutos[selectedBand],
                badge = "AUTO".takeIf { compressor.kneeAutos[selectedBand] },
            ),
            MultibandTransferHandlePresentation(
                id = "knee",
                x = linearValueToX(kneeInput, inputMin, inputMax),
                y = linearValueToY(compressorOutputDb(kneeInput, spec), outputMin, outputMax),
                dragAxis = GraphDragAxis.HORIZONTAL,
                enabled = !compressor.kneeAutos[selectedBand],
                badge = "AUTO".takeIf { compressor.kneeAutos[selectedBand] },
            ),
        )
    val referenceSpec = MultibandTransferSpec(0.0, 0, 0.0, 0.0)
    return MultibandTransferPresentation(
        transferSpec = spec,
        curve = multibandTransferCurve(spec),
        referenceCurve = multibandTransferCurve(referenceSpec),
        handles = handles,
        controls =
            MultibandControlAvailability(
                thresholdEnabled = true,
                ratioEnabled = !compressor.kneeAutos[selectedBand],
                kneeEnabled = !compressor.kneeAutos[selectedBand],
                gainEnabled = !compressor.gainAutos[selectedBand],
                attackEnabled = !compressor.attackAutos[selectedBand],
                releaseEnabled = !compressor.releaseAutos[selectedBand],
            ),
        curveDashed = compressor.kneeAutos[selectedBand] || compressor.gainAutos[selectedBand],
    )
}

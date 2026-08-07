package com.llsl.viper4android.dsp

import com.llsl.viper4android.effect.DynamicEqState
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.EqState
import com.llsl.viper4android.effect.MultibandCompressorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home screen preview and the dedicated editor must render the same data. These tests
 * pin both to one shared derivation so a future change cannot silently reintroduce a second,
 * different curve for the same effect.
 */
class EffectGraphModelTest {
    private val sampleRate = 48_000

    @Test
    fun firModelMatchesTheSharedCurveSampler() {
        val state = EffectState(eq = EqState(bandCount = 10, bands = List(10) { it.toDouble() }))

        val model = firGraphModel(state, sampleRate)

        assertEquals(
            firCurvePoints(10, state.eq.bands, sampleRate, model.minDb, model.maxDb),
            model.curve,
        )
    }

    @Test
    fun firModelExposesOneHandlePerBandOnTheLogAxis() {
        val state = EffectState(eq = EqState(bandCount = 10, bands = List(10) { 0.0 }))

        val model = firGraphModel(state, sampleRate)

        assertEquals(10, model.handles.size)
        assertTrue(model.handles.zipWithNext().all { (a, b) -> a.x < b.x })
        assertTrue(model.handles.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun firModelTruncatesGainsToTheDeclaredBandCount() {
        val state = EffectState(eq = EqState(bandCount = 10, bands = List(31) { 3.0 }))

        assertEquals(10, firGraphModel(state, sampleRate).handles.size)
    }

    @Test
    fun firModelSurvivesAnEmptyBandList() {
        val state = EffectState(eq = EqState(bandCount = 10, bands = emptyList()))

        val model = firGraphModel(state, sampleRate)

        assertTrue(model.handles.isEmpty())
        assertTrue(model.curve.isEmpty())
    }

    @Test
    fun dynamicEqModelMatchesTheSharedCurveSampler() {
        val state = EffectState(
            dynamicEq = DynamicEqState(
                bandCount = 3,
                freqs = listOf(60, 150, 400),
                qs = listOf(100, 100, 150),
                gains = listOf(30, -20, 50),
                thresholds = List(3) { -200 },
                attacks = List(3) { 10 },
                releases = List(3) { 100 },
                filterTypes = listOf(1, 0, 2),
            )
        )

        val model = dynamicEqGraphModel(state, sampleRate)

        assertEquals(
            dynamicEqCurvePoints(model.bands, sampleRate, model.minDb, model.maxDb),
            model.curve,
        )
        assertEquals(listOf(1, 0, 2), model.bands.map { it.filterType })
        assertEquals(listOf(60.0, 150.0, 400.0), model.bands.map { it.frequency })
        assertEquals(listOf(3.0, -2.0, 5.0), model.bands.map { it.gainDb })
    }

    @Test
    fun dynamicEqModelClampsBandCountToTheShortestList() {
        val state = EffectState(
            dynamicEq = DynamicEqState(
                bandCount = 10,
                freqs = listOf(100, 200),
                gains = listOf(10),
            )
        )

        assertEquals(1, dynamicEqGraphModel(state, sampleRate).bands.size)
    }

    @Test
    fun dynamicEqModelSurvivesEmptyLists() {
        val state = EffectState(
            dynamicEq = DynamicEqState(bandCount = 3, freqs = emptyList(), gains = emptyList()),
        )

        val model = dynamicEqGraphModel(state, sampleRate)

        assertTrue(model.bands.isEmpty())
        assertTrue(model.curve.isEmpty())
        assertTrue(model.handles.isEmpty())
    }

    @Test
    fun multibandModelExposesFourCrossoverGainHandlesAndSharedCurves() {
        val state =
            EffectState(
                multibandCompressor =
                    MultibandCompressorState(
                        crossovers = listOf(120, 500, 4000, 8000),
                        gains = listOf(3, 6, 9, 12, 15),
                        gainAutos = listOf(false, true, false, true, false),
                        bandEnables = listOf(true, false, true, true, false),
                    ),
            )

        val model = multibandGraphModel(state, sampleRate)

        assertEquals(-72.0, model.minDb, 0.0)
        assertEquals(24.0, model.maxDb, 0.0)
        assertEquals(listOf("crossover-0", "crossover-1", "crossover-2", "crossover-3"), model.handles.map { it.id })
        listOf(3.0, 6.0, 9.0, 12.0).zip(model.handles).forEach { (expected, handle) ->
            assertEquals(expected, graphYToDb(handle.y, model.minDb, model.maxDb), 1e-5)
        }
        assertEquals(
            multibandCrossoverCurves(
                model.crossovers,
                sampleRate,
                model.minDb,
                model.maxDb,
                bandGainsDb = listOf(3.0, 0.0, 9.0, 0.0, 15.0),
            ),
            model.bandCurves,
        )
        assertEquals(
            multibandCrossoverSumCurve(
                model.crossovers,
                sampleRate,
                model.minDb,
                model.maxDb,
            ),
            model.unitySumCurve,
        )
        assertEquals(listOf(3.0, 6.0, 9.0, 12.0, 15.0), model.gainsDb)
        assertEquals(listOf(false, true, false, true, false), model.gainAutos)
        assertEquals(listOf(true, false, true, true, false), model.bandEnables)
        assertEquals(4, model.crossovers.size)
        assertEquals(5, model.bandRegions.size)
    }

    @Test
    fun multibandModelPadsShortPersistedLists() {
        val state =
            EffectState(
                multibandCompressor =
                    MultibandCompressorState(
                        crossovers = listOf(200),
                        gains = listOf(8),
                        gainAutos = listOf(false),
                    ),
            )

        val model = multibandGraphModel(state, sampleRate)

        assertEquals(5, model.gainsDb.size)
        assertEquals(5, model.gainAutos.size)
        assertEquals(4, model.crossovers.size)
        assertEquals(8.0, model.gainsDb.first(), 1e-9)
        assertTrue(
            "padded crossovers must stay ordered, got ${model.crossovers}",
            model.crossovers.zipWithNext().all { (a, b) -> a < b },
        )
    }

    @Test
    fun multibandHandlesUseCrossoverFrequencyAndStoredBandGain() {
        val state =
            EffectState(
                multibandCompressor =
                    MultibandCompressorState(
                        crossovers = listOf(200, 2000, 4000, 8000),
                        gains = listOf(4, 8, 12, 16, 20),
                    ),
            )

        val model = multibandGraphModel(state, sampleRate)
        val secondCrossover = model.handles.first { it.id == "crossover-1" }

        assertEquals(graphFrequencyToX(2000.0, sampleRate), secondCrossover.x, 1e-6f)
        assertEquals(8.0, graphYToDb(secondCrossover.y, model.minDb, model.maxDb), 1e-6)
    }

    @Test
    fun lowSampleRateShrinksEveryModelAxis() {
        val state = EffectState(eq = EqState(bandCount = 10, bands = List(10) { 0.0 }))

        val at8k = firGraphModel(state, 8_000)
        val multibandAt8k = multibandGraphModel(EffectState(), 8_000)

        assertTrue(at8k.maxFrequency < 4_000.0)
        assertTrue(at8k.handles.all { it.x in 0f..1f })
        assertTrue(multibandAt8k.crossovers.all { it < 4_000.0 })
        assertEquals(5, multibandAt8k.bandCurves.size)
    }
}

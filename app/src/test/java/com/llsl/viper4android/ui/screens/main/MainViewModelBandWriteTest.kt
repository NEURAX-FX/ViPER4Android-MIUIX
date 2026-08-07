package com.llsl.viper4android.ui.screens.main

import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.Effects
import com.llsl.viper4android.effect.MultibandCompressorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelBandWriteTest {
    @Test
    fun multibandWritesUseEachPreferenceListSize() {
        val mbc = Effects.multibandCompressor
        val writes = indexedBandPrefWrites(EffectState(), listOf(mbc.crossovers, mbc.thresholds))

        val crossoverWrites = writes.filter { it.pref === mbc.crossovers }
        assertEquals(listOf(0, 1, 2, 3), crossoverWrites.map { it.band })
        assertTrue(crossoverWrites.all { it.count == 4 })

        val thresholdWrites = writes.filter { it.pref === mbc.thresholds }
        assertEquals(listOf(0, 1, 2, 3, 4), thresholdWrites.map { it.band })
        assertTrue(thresholdWrites.all { it.count == 5 })
    }

    @Test
    fun multibandWritesRepairMalformedPersistedListSizes() {
        val mbc = Effects.multibandCompressor
        val state =
            EffectState(
                multibandCompressor =
                    MultibandCompressorState(
                        crossovers = listOf(120, 500, 4_000, 8_000, 12_000),
                        thresholds = listOf(-12),
                    ),
            )

        val writes = indexedBandPrefWrites(state, listOf(mbc.crossovers, mbc.thresholds))

        val crossoverWrites = writes.filter { it.pref === mbc.crossovers }
        assertEquals(listOf(0, 1, 2, 3), crossoverWrites.map { it.band })
        val thresholdWrites = writes.filter { it.pref === mbc.thresholds }
        assertEquals(listOf(-12, -18, -18, -18, -18), thresholdWrites.map { it.value })
    }
}

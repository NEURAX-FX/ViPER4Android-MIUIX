package com.llsl.viper4android.effect

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IemStateContractTest {
    @Test
    fun defaultsAndPreferenceRegistryMatchDriverContract() {
        val iem = EffectState().iem
        assertFalse(iem.general.enable)
        assertEquals(3, iem.general.order)
        assertEquals(0, iem.general.encoderMode)
        assertEquals(2, iem.general.renderMode)
        assertEquals(500, iem.halo.dialogAggressThousandths)
        assertEquals(800, iem.halo.spaceThousandths)
        assertTrue(iem.halo.lfeEnabled)
        assertEquals(750000, iem.halo.lfeFrequencyMillionths)
        assertEquals(0, iem.halo.lfeSplitMillionths)
        assertEquals(272727, iem.halo.lfeGainMillionths)
        assertEquals(6000, iem.stereo.widthCentidegrees)
        assertEquals(listOf(-3000, 3000), iem.multi.azimuthCentidegrees)
        assertEquals(5000, iem.granular.deltaTimeUs)
        assertEquals(250000, iem.granular.grainLengthUs)
        assertEquals(-1, iem.decoder.headphoneEq)
        assertEquals(-30, iem.output.limiterCeilingCentidb)
        assertEquals(67, EFFECT_GROUPS.first { it.effectKey == "iem" }.prefs.size)
    }

    @Test
    fun malformedMultiListsAreNormalizedAndFreezeIsNeverRestored() {
        val normalized =
            normalizeIemState(
                IemState(
                    multi = IemMultiState(
                        azimuthCentidegrees = listOf(100),
                        elevationCentidegrees = emptyList(),
                        gainDecidb = listOf(-10, 20, 30),
                        mute = listOf(true),
                    ),
                    general = IemGeneralState(encoderMode = 99, renderMode = -1),
                    halo = IemHaloState(
                        dialogIsolateThousandths = 1500,
                        spaceThousandths = -2,
                        lfeFrequencyMillionths = 1500000,
                        lfeSplitMillionths = -1,
                        lfeGainMillionths = 1200000,
                    ),
                    freeze = true,
                ),
            )
        assertEquals(listOf(100, 3000), normalized.multi.azimuthCentidegrees)
        assertEquals(listOf(0, 0), normalized.multi.elevationCentidegrees)
        assertEquals(listOf(-10, 20), normalized.multi.gainDecidb)
        assertEquals(listOf(true, false), normalized.multi.mute)
        assertEquals(3, normalized.general.encoderMode)
        assertEquals(0, normalized.general.renderMode)
        assertEquals(1000, normalized.halo.dialogIsolateThousandths)
        assertEquals(0, normalized.halo.spaceThousandths)
        assertEquals(1000000, normalized.halo.lfeFrequencyMillionths)
        assertEquals(0, normalized.halo.lfeSplitMillionths)
        assertEquals(1000000, normalized.halo.lfeGainMillionths)
        assertFalse(normalized.freeze)
    }

    @Test
    fun presetRoundTripPersistsIemControlsButExcludesFreeze() {
        val original =
            EffectState(
                iem =
                    IemState(
                        general = IemGeneralState(enable = true, encoderMode = 3, order = 2, renderMode = 1),
                        halo = IemHaloState(
                            spaceThousandths = 123,
                            backBoost = false,
                            lfeEnabled = false,
                            lfeFrequencyMillionths = 654321,
                            lfeSplitMillionths = 123456,
                            lfeGainMillionths = 456789,
                        ),
                        multi = IemMultiState(gainDecidb = listOf(-20, 30)),
                        rotation = IemRotationState(yawCentidegrees = 4500),
                        decoder = IemDecoderState(headphoneEq = 22),
                        freeze = true,
                    ),
            )
        val restored =
            deserializeEffectPrefs(
                JSONObject(serializeEffectPrefs(original).toString()),
                EffectState(),
            )
        assertTrue(restored.iem.general.enable)
        assertEquals(3, restored.iem.general.encoderMode)
        assertEquals(1, restored.iem.general.renderMode)
        assertEquals(123, restored.iem.halo.spaceThousandths)
        assertFalse(restored.iem.halo.backBoost)
        assertFalse(restored.iem.halo.lfeEnabled)
        assertEquals(654321, restored.iem.halo.lfeFrequencyMillionths)
        assertEquals(123456, restored.iem.halo.lfeSplitMillionths)
        assertEquals(456789, restored.iem.halo.lfeGainMillionths)
        assertEquals(listOf(-20, 30), restored.iem.multi.gainDecidb)
        assertEquals(4500, restored.iem.rotation.yawCentidegrees)
        assertEquals(22, restored.iem.decoder.headphoneEq)
        assertFalse(restored.iem.freeze)
    }
}

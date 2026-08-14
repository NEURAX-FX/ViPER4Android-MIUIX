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
        assertTrue(iem.decoder.downmix.delayEnabled)
        assertEquals(744444, iem.decoder.downmix.centerTrimMillionths)
        assertEquals(711111, iem.decoder.downmix.rearMidTrimMillionths)
        assertFalse(iem.decoder.downmix.lfeLpfEnabled)
        assertFalse(iem.decoder.downmix.outputHpfEnabled)
        assertEquals(-30, iem.output.limiterCeilingCentidb)
        assertEquals(96, EFFECT_GROUPS.first { it.effectKey == "iem" }.prefs.size)
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
                    decoder = IemDecoderState(
                        downmix = IemDownmixState(
                            lsDelayUs = -1,
                            rsDelayUs = 32001,
                            sideShelfFrequencyMillionths = -1,
                            outputRightTrimMillionths = 1000001,
                        ),
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
        assertEquals(0, normalized.decoder.downmix.lsDelayUs)
        assertEquals(32000, normalized.decoder.downmix.rsDelayUs)
        assertEquals(0, normalized.decoder.downmix.sideShelfFrequencyMillionths)
        assertEquals(1000000, normalized.decoder.downmix.outputRightTrimMillionths)
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
                        decoder = IemDecoderState(
                            headphoneEq = 22,
                            downmix = IemDownmixState(
                                delayEnabled = false,
                                lsDelayUs = 1000,
                                rsDelayUs = 2000,
                                lsrDelayUs = 3000,
                                rsrDelayUs = 4000,
                                sideShelfEnabled = true,
                                sideShelfFrequencyMillionths = 111111,
                                sideShelfGainMillionths = 222222,
                                rearShelfEnabled = true,
                                rearShelfFrequencyMillionths = 333333,
                                rearShelfGainMillionths = 444444,
                                panLeftMillionths = 555555,
                                panRightMillionths = 666666,
                                centerDivergenceMillionths = 777777,
                                frontMidTrimMillionths = 111112,
                                frontSideTrimMillionths = 222223,
                                centerTrimMillionths = 333334,
                                surroundMidTrimMillionths = 444445,
                                surroundSideTrimMillionths = 555556,
                                rearMidTrimMillionths = 666667,
                                rearSideTrimMillionths = 777778,
                                lfeTrimMillionths = 888889,
                                lfeLpfEnabled = true,
                                lfeLpfFrequencyMillionths = 123456,
                                scaleInputByOutputCount = true,
                                outputHpfEnabled = true,
                                outputHpfFrequencyMillionths = 234567,
                                outputLeftTrimMillionths = 345678,
                                outputRightTrimMillionths = 456789,
                            ),
                        ),
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
        assertEquals(original.iem.decoder.downmix, restored.iem.decoder.downmix)
        assertFalse(restored.iem.freeze)
    }
}

package com.llsl.viper4android.ui.screens.main

import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.IemGeneralState
import com.llsl.viper4android.effect.IemState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IemCardPolicyTest {
    @Test
    fun iemCardIsVisibleOnlyOnHidlPath() {
        assertTrue(shouldShowIemCard(aidlModeActive = false))
        assertFalse(shouldShowIemCard(aidlModeActive = true))
    }

    @Test
    fun iemCardFollowsConvolverAndPrecedesLegacySpatialEffects() {
        val order = effectSectionOrder()

        assertTrue(order.indexOf("convolver") < order.indexOf("iem"))
        assertTrue(order.indexOf("iem") < order.indexOf("fieldSurround"))
    }

    @Test
    fun defaultSummaryDescribesEncoderOrderAndDecoder() {
        assertEquals("Stereo · 3rd order · KU100", iemSummary(EffectState().iem))
        assertEquals(
            "Halo · 3rd order · Off",
            iemSummary(IemState(general = IemGeneralState(encoderMode = 3, renderMode = 0))),
        )
    }
}

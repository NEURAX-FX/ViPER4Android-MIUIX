package com.llsl.viper4android.service

import org.junit.Assert.assertEquals
import org.junit.Test

class Session0ModePlanTest {
    @Test
    fun legacyGlobalModeActivatesOnlyAfterFullState() {
        assertEquals(
            listOf(
                Session0ModeStep.DEACTIVATE,
                Session0ModeStep.DISPATCH_FULL_STATE,
                Session0ModeStep.ACTIVATE,
            ),
            session0ModePlan(true, false, false),
        )
    }

    @Test
    fun activeLegacyGlobalModeUpdatesWithoutToggling() {
        assertEquals(
            listOf(Session0ModeStep.DISPATCH_FULL_STATE),
            session0ModePlan(true, false, true),
        )
    }

    @Test
    fun dynamicAndAidlModesDeactivatePinnedLegacyPath() {
        assertEquals(
            listOf(Session0ModeStep.DEACTIVATE),
            session0ModePlan(false, false, true),
        )
        assertEquals(
            listOf(Session0ModeStep.DEACTIVATE),
            session0ModePlan(true, true, true),
        )
    }
}

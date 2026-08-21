package com.llsl.viper4android.daemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DaemonModePreferenceTest {
    @Test
    fun everyModeRoundTripsThroughItsToken() {
        DaemonModePreference.entries.forEach { mode ->
            assertEquals(mode, DaemonModePreference.fromToken(mode.token))
        }
    }

    @Test
    fun tokensAreUniqueAcrossModes() {
        val tokens = DaemonModePreference.entries.map { it.token }
        assertEquals(tokens.size, tokens.toSet().size)
    }

    @Test
    fun defaultIsAutoWhenNothingIsStored() {
        assertEquals(DaemonModePreference.Auto, DaemonModePreference.DEFAULT)
        assertEquals(DaemonModePreference.Auto, DaemonModePreference.fromToken(null))
        assertEquals(DaemonModePreference.Auto, DaemonModePreference.fromToken(""))
    }

    @Test
    fun unknownTokenFallsBackToDefaultInsteadOfThrowing() {
        assertEquals(DaemonModePreference.DEFAULT, DaemonModePreference.fromToken("daemon"))
        assertEquals(DaemonModePreference.DEFAULT, DaemonModePreference.fromToken("DaemonOnly"))
        assertEquals(DaemonModePreference.DEFAULT, DaemonModePreference.fromToken("  auto "))
    }

    /**
     * The persisted form must not be positional: reordering the enum would
     * otherwise silently repoint a user's saved mode at a different backend.
     */
    @Test
    fun persistedTokenIsNotAnOrdinal() {
        DaemonModePreference.entries.forEach { mode ->
            assertFalse(
                "token ${mode.token} must not be numeric",
                mode.token.toIntOrNull() != null,
            )
            assertNotEquals(mode.ordinal.toString(), mode.token)
        }
        assertEquals(DaemonModePreference.DEFAULT, DaemonModePreference.fromToken("0"))
        assertEquals(DaemonModePreference.DEFAULT, DaemonModePreference.fromToken("1"))
        assertEquals(DaemonModePreference.DEFAULT, DaemonModePreference.fromToken("2"))
    }

    /**
     * Pins the wire tokens. Changing one is a data migration, not a rename, so it
     * must not pass unnoticed.
     */
    @Test
    fun tokensAreStable() {
        assertEquals("auto", DaemonModePreference.Auto.token)
        assertEquals("daemon_only", DaemonModePreference.DaemonOnly.token)
        assertEquals("driver_only", DaemonModePreference.DriverOnly.token)
    }
}

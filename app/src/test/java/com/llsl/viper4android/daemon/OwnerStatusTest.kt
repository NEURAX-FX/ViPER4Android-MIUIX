package com.llsl.viper4android.daemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner state is what tells the App whether anything holds the session-0 effect
 * handle. "Daemon connected" says nothing about that, so the App must read the
 * owner fields rather than infer ownership from socket health.
 */
class OwnerStatusTest {
    @Test
    fun `owned state reports a live effect owner`() {
        val status =
            DaemonStatusReader.parse(
                """
                mode=route-restore
                driver_connected=1
                owner_enabled=1
                owner_listening=1
                owner_connected=1
                owner_state=owned
                owner_pid=4242
                owner_effect_id=5150
                owner_has_control=1
                owner_restarts=0
                tracked_sessions=2
                """.trimIndent(),
            )

        assertEquals(DaemonOwnerState.Owned, status.ownerState)
        assertEquals(4242, status.ownerPid)
        assertEquals(5150, status.ownerEffectId)
        assertTrue(status.ownerHasControl)
        assertEquals(2L, status.trackedSessions)
        // The whole point: with an owner holding the handle the App must not create
        // one of its own.
        assertTrue(status.ownerHoldsEffect)
    }

    @Test
    fun `failed owner does not count as holding the effect`() {
        val status =
            DaemonStatusReader.parse(
                """
                mode=route-restore
                owner_enabled=1
                owner_connected=1
                owner_state=failed
                owner_effect_id=0
                owner_failure_reason=2
                """.trimIndent(),
            )

        assertEquals(DaemonOwnerState.Failed, status.ownerState)
        assertEquals(2, status.ownerFailureReason)
        // A connected-but-failed owner is exactly the case where the App's fallback
        // has to take over; treating it as owned would leave audio unprocessed.
        assertFalse(status.ownerHoldsEffect)
    }

    @Test
    fun `starting owner is not yet holding the effect`() {
        val status =
            DaemonStatusReader.parse(
                """
                owner_enabled=1
                owner_connected=1
                owner_state=starting
                owner_effect_id=0
                """.trimIndent(),
            )

        assertEquals(DaemonOwnerState.Starting, status.ownerState)
        assertFalse(status.ownerHoldsEffect)
    }

    @Test
    fun `owner state claiming owned without an effect id is not trusted`() {
        val status =
            DaemonStatusReader.parse(
                """
                owner_enabled=1
                owner_state=owned
                owner_effect_id=0
                """.trimIndent(),
            )

        // AudioFlinger never issues effect id 0, so this is a daemon that lost the
        // handle without updating its state. Believing it would stop the App from
        // falling back and leave no one owning an effect.
        assertFalse(status.ownerHoldsEffect)
    }

    @Test
    fun `older daemon without owner fields reads as absent`() {
        val status =
            DaemonStatusReader.parse(
                """
                mode=route-restore
                driver_connected=1
                app_listening=1
                daemon_generation=7
                """.trimIndent(),
            )

        // Forward compatibility runs both ways: an older daemon must not look like
        // a broken newer one.
        assertEquals(DaemonOwnerState.Absent, status.ownerState)
        assertFalse(status.ownerEnabled)
        assertFalse(status.ownerHoldsEffect)
        assertEquals(7L, status.daemonGeneration)
    }

    @Test
    fun `unknown owner state token is treated as absent`() {
        val status =
            DaemonStatusReader.parse(
                """
                owner_enabled=1
                owner_state=some_future_state
                owner_effect_id=99
                """.trimIndent(),
            )

        // A state the App cannot interpret must not be read as ownership: guessing
        // would suppress the fallback on an unknown condition.
        assertEquals(DaemonOwnerState.Absent, status.ownerState)
        assertFalse(status.ownerHoldsEffect)
    }
}

package com.llsl.viper4android.daemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decides who owns the session-0 effect handle.
 *
 * Two owners at once is the failure this whole change exists to remove: the App
 * creating a handle while the daemon's owner already holds one produces two
 * AudioFlinger effect modules on the same session, and killing the App then looks
 * like it "fixed" nothing because the duplicate is still there.
 */
class OwnershipExclusionTest {
    private fun status(
        ownerState: DaemonOwnerState = DaemonOwnerState.Absent,
        ownerEffectId: Int = 0,
        ownerPid: Int = if (ownerState == DaemonOwnerState.Absent) 0 else 4242,
        ownerEnabled: Boolean = ownerState != DaemonOwnerState.Absent,
    ) = DaemonRuntimeStatus(
        mode = "route-restore",
        driverConnected = true,
        ownerEnabled = ownerEnabled,
        ownerConnected = ownerState != DaemonOwnerState.Absent,
        ownerState = ownerState,
        ownerPid = ownerPid,
        ownerEffectId = ownerEffectId,
    )

    @Test
    fun `driver only mode always keeps the App in charge`() {
        // The user asked for driver passthrough; owner state is irrelevant.
        var probed = false
        val decision =
            EffectOwnership.decide(
                mode = DaemonModePreference.DriverOnly,
                daemonAccepted = true,
                status = status(DaemonOwnerState.Owned, ownerEffectId = 42),
                ownerProcessAlive = { probed = true; true },
            )
        assertTrue(decision.appOwnsEffect)
        assertTrue(decision.appTracksSessions)
        // DriverOnly must not inspect owner-related state at all.
        assertFalse(probed)
    }

    @Test
    fun `no daemon backend leaves the App in charge`() {
        val decision =
            EffectOwnership.decide(
                mode = DaemonModePreference.Auto,
                daemonAccepted = false,
                status = null,
            )
        assertTrue(decision.appOwnsEffect)
        assertTrue(decision.appTracksSessions)
    }

    @Test
    fun `a live owner keeps the App out even when the daemon is unreachable`() {
        // The owner outlives its daemon: it keeps the effect handle across an init
        // respawn or a module update. So "daemon refused/unreachable" is not evidence
        // that no handle exists, and creating one here would produce the duplicate
        // session-0 module this object exists to prevent.
        val decision =
            EffectOwnership.decide(
                mode = DaemonModePreference.Auto,
                daemonAccepted = false,
                status = status(DaemonOwnerState.Owned, ownerEffectId = 4242, ownerPid = 31337),
                // Injected: the default probe reads /proc, and this pid does not exist
                // on the host JVM the unit tests run on.
                ownerProcessAlive = { it == 31337 },
            )
        assertFalse(decision.appOwnsEffect)
        assertFalse(decision.appTracksSessions)
        assertEquals(EffectOwnership.Reason.DaemonOwner, decision.reason)
    }

    @Test
    fun `an unreadable state file with no daemon still leaves the App in charge`() {
        // Nothing observable says a handle exists. Conceding here would risk silence,
        // which is worse than a duplicate.
        val decision =
            EffectOwnership.decide(
                mode = DaemonModePreference.Auto,
                daemonAccepted = false,
                status = null,
            )
        assertTrue(decision.appOwnsEffect)
        assertEquals(EffectOwnership.Reason.NoDaemon, decision.reason)
    }

    @Test
    fun `a stale owned claim whose process is gone does not block the App`() {
        // owner_pid names a dead process: the state file outlived both the daemon and
        // its owner. Trusting it would leave nothing owning an effect at all.
        val decision =
            EffectOwnership.decide(
                mode = DaemonModePreference.Auto,
                daemonAccepted = false,
                status = status(DaemonOwnerState.Owned, ownerEffectId = 4242, ownerPid = 31337),
                ownerProcessAlive = { false },
            )
        assertTrue(decision.appOwnsEffect)
        assertTrue(decision.appTracksSessions)
        assertEquals(EffectOwnership.Reason.NoOwner, decision.reason)
    }

    @Test
    fun `owner holding the effect stops the App from creating one`() {
        val decision =
            EffectOwnership.decide(
                mode = DaemonModePreference.Auto,
                daemonAccepted = true,
                status = status(DaemonOwnerState.Owned, ownerEffectId = 5150),
            )
        assertFalse(decision.appOwnsEffect)
        // Session tracking moves to the owner's privileged observer, so the App's
        // dumpsys-parsing monitor must not run in parallel.
        assertFalse(decision.appTracksSessions)
    }

    @Test
    fun `failed owner hands ownership back to the App`() {
        val decision =
            EffectOwnership.decide(
                mode = DaemonModePreference.Auto,
                daemonAccepted = true,
                status = status(DaemonOwnerState.Failed),
            )
        // A daemon that accepts snapshots but has no owner processes nothing: the
        // fallback has to create the handle or there is silence.
        assertTrue(decision.appOwnsEffect)
        assertTrue(decision.appTracksSessions)
    }

    @Test
    fun `starting owner keeps the App in charge until the handle exists`() {
        val decision =
            EffectOwnership.decide(
                mode = DaemonModePreference.Auto,
                daemonAccepted = true,
                status = status(DaemonOwnerState.Starting),
            )
        assertTrue(decision.appOwnsEffect)
    }

    @Test
    fun `owner claiming ownership without an effect id is not trusted`() {
        val decision =
            EffectOwnership.decide(
                mode = DaemonModePreference.Auto,
                daemonAccepted = true,
                status = status(DaemonOwnerState.Owned, ownerEffectId = 0),
            )
        assertTrue(decision.appOwnsEffect)
    }

    @Test
    fun `daemon only mode without an owner still lets the App own the effect`() {
        val decision =
            EffectOwnership.decide(
                mode = DaemonModePreference.DaemonOnly,
                daemonAccepted = true,
                status = status(DaemonOwnerState.Absent),
            )
        // DaemonOnly governs which backend applies parameters, not who creates the
        // AudioFlinger client. Without an owner someone still has to create it.
        assertTrue(decision.appOwnsEffect)
    }

    @Test
    fun `daemon only mode defers to a live owner`() {
        val decision =
            EffectOwnership.decide(
                mode = DaemonModePreference.DaemonOnly,
                daemonAccepted = true,
                status = status(DaemonOwnerState.Owned, ownerEffectId = 7),
            )
        assertFalse(decision.appOwnsEffect)
        assertFalse(decision.appTracksSessions)
    }

    @Test
    fun `owner reported by an unreadable state file is not assumed`() {
        // Root may be unavailable to the App even with a daemon running. Assuming an
        // owner exists would suppress the fallback with no evidence.
        val decision =
            EffectOwnership.decide(
                mode = DaemonModePreference.Auto,
                daemonAccepted = true,
                status = null,
            )
        assertTrue(decision.appOwnsEffect)
    }

    @Test
    fun `ownership reason is reported for diagnostics`() {
        assertEquals(
            EffectOwnership.Reason.DaemonOwner,
            EffectOwnership.decide(
                mode = DaemonModePreference.Auto,
                daemonAccepted = true,
                status = status(DaemonOwnerState.Owned, ownerEffectId = 1),
            ).reason,
        )
        assertEquals(
            EffectOwnership.Reason.DriverOnlyMode,
            EffectOwnership.decide(
                mode = DaemonModePreference.DriverOnly,
                daemonAccepted = false,
                status = null,
            ).reason,
        )
        assertEquals(
            EffectOwnership.Reason.OwnerFailed,
            EffectOwnership.decide(
                mode = DaemonModePreference.Auto,
                daemonAccepted = true,
                status = status(DaemonOwnerState.Failed),
            ).reason,
        )
        assertEquals(
            EffectOwnership.Reason.NoOwner,
            EffectOwnership.decide(
                mode = DaemonModePreference.Auto,
                daemonAccepted = true,
                status = status(DaemonOwnerState.Absent),
            ).reason,
        )
    }
}

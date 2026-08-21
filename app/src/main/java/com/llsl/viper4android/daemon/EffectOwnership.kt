package com.llsl.viper4android.daemon

/**
 * Decides whether the App or the daemon's owner process holds the session-0
 * `AudioEffect` handle.
 *
 * Both holding one at once is the defect this exists to prevent: AudioFlinger
 * would carry two effect modules on session 0, and killing the App would then
 * appear to change nothing because the duplicate survives.
 *
 * The decision is deliberately conservative. Ownership is conceded only on
 * positive evidence that a handle exists; every unknown, unreadable or degraded
 * condition leaves the App in charge, because the alternative is silence.
 */
object EffectOwnership {
    /** Why ownership landed where it did. Surfaced in diagnostics, not logic. */
    enum class Reason {
        /** User selected driver passthrough; the daemon is never consulted. */
        DriverOnlyMode,

        /** No daemon backend, or its state file could not be read. */
        NoDaemon,

        /** Daemon is reachable but nothing owns a handle yet. */
        NoOwner,

        /** Owner process exists but cannot hold a handle. */
        OwnerFailed,

        /** Owner holds a live handle; the App must not create one. */
        DaemonOwner,
    }

    data class Decision(
        val appOwnsEffect: Boolean,
        val appTracksSessions: Boolean,
        val reason: Reason,
    )

    /**
     * @param mode user's backend preference.
     * @param daemonAccepted whether the daemon accepted the last snapshot. False
     *   means the daemon is absent or refusing, so its *control plane* cannot be
     *   relied on. It is not evidence that no handle exists.
     * @param status daemon's published state, or null when unreadable. Root may be
     *   unavailable to the App even with a daemon running, and an unreadable file
     *   is not evidence of an owner.
     * @param ownerProcessAlive liveness probe for `status.ownerPid`. Consulted only
     *   when the daemon itself is unreachable, where the state file may have
     *   outlived both the daemon and its owner.
     */
    fun decide(
        mode: DaemonModePreference,
        daemonAccepted: Boolean,
        status: DaemonRuntimeStatus?,
        ownerProcessAlive: (Int) -> Boolean = ::isProcessAlive,
    ): Decision {
        if (mode == DaemonModePreference.DriverOnly) {
            return Decision(
                appOwnsEffect = true,
                appTracksSessions = true,
                reason = Reason.DriverOnlyMode,
            )
        }
        if (status == null) {
            return Decision(
                appOwnsEffect = true,
                appTracksSessions = true,
                reason = Reason.NoDaemon,
            )
        }
        // `ownerHoldsEffect` requires both an owned state and a non-zero effect id:
        // AudioFlinger never issues id 0, so a claim without one is a daemon that
        // lost the handle without updating its state.
        //
        // This is checked even when the daemon is unreachable. The owner process
        // keeps its handle across a daemon restart - that is the whole point of
        // running it outside the daemon - so treating "daemon refused" as "no
        // handle" would create a second session-0 module on every init respawn.
        if (status.ownerHoldsEffect) {
            // When the daemon can still be talked to, its state file is current by
            // construction. When it cannot, the file may have outlived the owner too,
            // so the claim is only trusted if that pid is still a live process.
            val ownerConfirmed = daemonAccepted || ownerProcessAlive(status.ownerPid)
            if (ownerConfirmed) {
                return Decision(
                    appOwnsEffect = false,
                    // The owner observes sessions from a Context holding
                    // MODIFY_AUDIO_ROUTING, so the App's dumpsys-parsing monitor is
                    // both redundant and less accurate here.
                    appTracksSessions = false,
                    reason = Reason.DaemonOwner,
                )
            }
        }
        if (!daemonAccepted) {
            return Decision(
                appOwnsEffect = true,
                appTracksSessions = true,
                reason =
                    if (status.ownerHoldsEffect) Reason.NoOwner else Reason.NoDaemon,
            )
        }
        return Decision(
            appOwnsEffect = true,
            appTracksSessions = true,
            reason =
                if (status.ownerState == DaemonOwnerState.Failed) {
                    Reason.OwnerFailed
                } else {
                    Reason.NoOwner
                },
        )
    }

    /**
     * True when `pid` names a live process.
     *
     * `/proc/<pid>` is world-readable, so this needs no root: the App can confirm a
     * root-owned owner is still running even when it cannot read the daemon's
     * state directory itself.
     */
    private fun isProcessAlive(pid: Int): Boolean =
        pid > 0 && java.io.File("/proc/$pid").exists()
}

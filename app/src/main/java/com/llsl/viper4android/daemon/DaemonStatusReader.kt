package com.llsl.viper4android.daemon

import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.utils.RootShell

/**
 * Owner process state as published by the daemon.
 *
 * An unrecognised token maps to [Absent] rather than to a guess: the App uses
 * this to decide whether to stop creating its own effect, and reading an unknown
 * state as ownership would suppress the fallback on a condition the App cannot
 * interpret.
 */
enum class DaemonOwnerState(
    val token: String,
) {
    Absent("absent"),
    Starting("starting"),
    Owned("owned"),
    Failed("failed"),
    ;

    companion object {
        fun fromToken(token: String?): DaemonOwnerState =
            entries.firstOrNull { it.token == token } ?: Absent
    }
}

/**
 * Snapshot of the daemon's published state file.
 *
 * Only the fields the App reconciles against are modelled; unknown keys are
 * ignored so a newer daemon can add diagnostics without breaking the App.
 */
data class DaemonRuntimeStatus(
    val mode: String = "",
    val driverConnected: Boolean = false,
    val routeKnown: Boolean = false,
    val routeKeyHash: String = "",
    val routeEpoch: Long = 0,
    val daemonGeneration: Long = 0,
    val restoresAccepted: Long = 0,
    val restoresRejected: Long = 0,
    val restoresBypassed: Long = 0,
    // App endpoint. `appListening` false means the daemon runs but never bound
    // @viper4android.app.v1, so the App can never reach it however healthy the
    // daemon otherwise looks.
    val appListening: Boolean = false,
    val appConnected: Boolean = false,
    val appRouteReports: Long = 0,
    val appSnapshotCommands: Long = 0,
    val appRejectedPeers: Long = 0,
    // True when the live route came from an App report rather than a sysfs probe.
    val routeFromApp: Boolean = false,
    // Owner process. Every field defaults to "no owner" so an older daemon's state
    // file, which has none of these keys, reads as absent rather than as broken.
    val ownerEnabled: Boolean = false,
    val ownerListening: Boolean = false,
    val ownerConnected: Boolean = false,
    val ownerState: DaemonOwnerState = DaemonOwnerState.Absent,
    val ownerPid: Int = 0,
    val ownerEffectId: Int = 0,
    val ownerHasControl: Boolean = false,
    val ownerRestarts: Long = 0,
    val ownerFailureReason: Int = 0,
    val trackedSessions: Long = 0,
) {
    val restoreEnabled: Boolean get() = mode == "route-restore"

    /**
     * True only when a real effect handle exists.
     *
     * Both conditions are required. AudioFlinger never issues effect id 0, so
     * `owner_state=owned` with id 0 is a daemon that lost the handle without
     * updating its state; trusting it would stop the App from falling back and
     * leave nothing owning an effect.
     */
    val ownerHoldsEffect: Boolean
        get() = ownerState == DaemonOwnerState.Owned && ownerEffectId != 0
}

/**
 * Reads `/data/adb/viper4android/daemon.state`.
 *
 * The daemon's generation is not carried on the driver event wire, so a
 * stale-generation refusal can only be reconciled by rereading what the daemon
 * itself published. The file lives under `/data/adb`, which is root-only, hence
 * the shell hop.
 */
class DaemonStatusReader(
    private val statePath: String = DEFAULT_STATE_PATH,
    private val reader: (String) -> String? = ::readWithRoot,
) {
    /** Returns the parsed status, or null when it cannot be read. */
    fun read(): DaemonRuntimeStatus? {
        val contents = reader(statePath) ?: return null
        return parse(contents)
    }

    companion object {
        const val DEFAULT_STATE_PATH = "/data/adb/viper4android/daemon.state"

        private fun readWithRoot(path: String): String? =
            try {
                val process = RootShell.exec("cat $path")
                val output = process.inputStream.bufferedReader().readText()
                if (process.exitValue() != 0 || output.isBlank()) null else output
            } catch (e: Exception) {
                FileLogger.d("Daemon", "daemon state unreadable: ${e.message}")
                null
            }

        /** Parses the daemon's `key=value` state file. */
        fun parse(contents: String): DaemonRuntimeStatus {
            val values = HashMap<String, String>()
            for (line in contents.lineSequence()) {
                val separator = line.indexOf('=')
                if (separator <= 0) continue
                values[line.substring(0, separator).trim()] = line.substring(separator + 1).trim()
            }
            return DaemonRuntimeStatus(
                mode = values["mode"].orEmpty(),
                driverConnected = values["driver_connected"] == "1",
                routeKnown = values["route_known"] == "1",
                routeKeyHash = values["route_key_hash"].orEmpty(),
                routeEpoch = values["route_epoch"]?.toLongOrNull() ?: 0,
                daemonGeneration = values["daemon_generation"]?.toLongOrNull() ?: 0,
                restoresAccepted = values["restores_accepted"]?.toLongOrNull() ?: 0,
                restoresRejected = values["restores_rejected"]?.toLongOrNull() ?: 0,
                restoresBypassed = values["restores_bypassed"]?.toLongOrNull() ?: 0,
                appListening = values["app_listening"] == "1",
                appConnected = values["app_connected"] == "1",
                appRouteReports = values["app_route_reports"]?.toLongOrNull() ?: 0,
                appSnapshotCommands = values["app_snapshot_commands"]?.toLongOrNull() ?: 0,
                appRejectedPeers = values["app_rejected_peers"]?.toLongOrNull() ?: 0,
                routeFromApp = values["route_from_app"] == "1",
                // Absent keys keep their "no owner" defaults, so an older daemon's
                // state file stays valid instead of reading as a broken newer one.
                ownerEnabled = values["owner_enabled"] == "1",
                ownerListening = values["owner_listening"] == "1",
                ownerConnected = values["owner_connected"] == "1",
                ownerState = DaemonOwnerState.fromToken(values["owner_state"]),
                ownerPid = values["owner_pid"]?.toIntOrNull() ?: 0,
                ownerEffectId = values["owner_effect_id"]?.toIntOrNull() ?: 0,
                ownerHasControl = values["owner_has_control"] == "1",
                ownerRestarts = values["owner_restarts"]?.toLongOrNull() ?: 0,
                ownerFailureReason = values["owner_failure_reason"]?.toIntOrNull() ?: 0,
                trackedSessions = values["tracked_sessions"]?.toLongOrNull() ?: 0,
            )
        }
    }
}

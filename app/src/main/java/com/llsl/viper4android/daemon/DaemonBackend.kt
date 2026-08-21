package com.llsl.viper4android.daemon

import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.utils.FileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Why the daemon backend is not in charge of applying state. */
enum class DaemonBackendStatus {
    /** No apply attempted yet.  */
    Unknown,

    /** Daemon accepted the last snapshot; it owns state application. */
    Active,

    /**
     * Daemon unreachable, untrusted, or refusing snapshots. The caller must keep
     * using the legacy direct-to-driver path.
     */
    Fallback,
}

/**
 * Chooses between the daemon backend and the legacy `AudioEffect`/`ConfigChannel`
 * path.
 *
 * The daemon is used only after it has actually accepted a snapshot. Anything
 * else - missing daemon, protocol mismatch, NACK, transport failure - reports
 * [DaemonBackendStatus.Fallback] so the caller keeps the legacy backend in
 * charge. Silently doing nothing would leave the user with no audio processing.
 */
class DaemonBackend(
    private val client: DaemonClient,
    private val bootIdProvider: () -> Long = { DEFAULT_BOOT_ID },
    private val clock: () -> Long = System::currentTimeMillis,
    private val statusReader: DaemonStatusReader = DaemonStatusReader(),
) {
    private val statusFlow = MutableStateFlow(DaemonBackendStatus.Unknown)
    val status: StateFlow<DaemonBackendStatus> = statusFlow.asStateFlow()

    val connectionState: StateFlow<DaemonConnectionState> get() = client.state

    /** Monotonic App generation; the daemon rejects a stale one. */
    private val appGeneration = AtomicLong(0)

    /** Daemon generation observed from the last accepted apply. */
    @Volatile
    private var daemonGeneration: Long = 1

    /** Route epoch last read from the daemon's state file. */
    @Volatile
    private var lastRouteEpoch: Long = 0

    @Volatile
    var lastError: DaemonApplyError = DaemonApplyError.NONE
        private set

    val isActive: Boolean get() = statusFlow.value == DaemonBackendStatus.Active

    /** Generation the next snapshot will be based on; visible for diagnostics. */
    val currentDaemonGeneration: Long get() = daemonGeneration

    /** Route epoch adopted from the daemon, or 0 when never read. */
    val currentRouteEpoch: Long get() = lastRouteEpoch

    /** Reads the daemon's published state file, or null when unreadable. */
    fun readStatus(): DaemonRuntimeStatus? =
        statusReader.read()?.also { lastRouteEpoch = it.routeEpoch }

    /**
     * Applies `state` for `identity` through the daemon.
     *
     * Returns true only when the driver acknowledged the apply. A false return
     * means the caller must apply the state through the legacy backend.
     *
     * A stale-generation refusal is retried once: it means the daemon restored a
     * route snapshot under a newer generation than the App knew about, which is the
     * normal race right after a route change. Failing outright there would leave the
     * user's latest edits unapplied until the next unrelated change.
     */
    suspend fun applyState(
        state: EffectState,
        identity: DaemonProtocol.DeviceIdentity,
        globalMode: Boolean = false,
    ): Boolean {
        // The daemon cannot see the live mixer, so it only knows this route because
        // the App names it. Reporting before applying also means the snapshot's key
        // matches the daemon's current route instead of being refused as foreign.
        reportRoute(identity)
        if (attemptApply(state, identity, globalMode)) return true
        if (lastError != DaemonApplyError.STALE_GENERATION) return false
        if (!reconcileGeneration()) return false
        FileLogger.i("Daemon", "retrying apply at daemon generation $daemonGeneration")
        return attemptApply(state, identity, globalMode)
    }

    /**
     * Tells the daemon which route is live, and adopts the generation it reports.
     *
     * Returns true when the daemon accepted the route. A refusal is not fatal: the
     * apply below will be refused too and the caller falls back, but adopting the
     * daemon's generation here avoids a guaranteed stale-generation round trip.
     */
    suspend fun reportRoute(identity: DaemonProtocol.DeviceIdentity): Boolean {
        val ack =
            client.reportRoute(
                AppProtocol.AppRouteReport(
                    routeType = identity.routeType,
                    stableAddressOrPort = identity.stableAddressOrPort,
                    productName = identity.productName,
                    encoding = identity.encoding,
                    sampleRate = identity.sampleRate,
                    channelMask = identity.channelMask,
                    outputFlags = identity.outputFlags,
                ),
            ) ?: return false

        if (ack.daemonGeneration > daemonGeneration) daemonGeneration = ack.daemonGeneration
        lastRouteEpoch = ack.routeEpoch
        if (!ack.accepted) {
            FileLogger.w("Daemon", "daemon refused route ${identity.routeType}")
        }
        return ack.accepted
    }

    private suspend fun attemptApply(
        state: EffectState,
        identity: DaemonProtocol.DeviceIdentity,
        globalMode: Boolean,
    ): Boolean {
        val snapshot =
            try {
                DaemonSnapshotMapper.buildSnapshot(
                    DaemonSnapshotMapper.SnapshotInputs(
                        state = state,
                        identity = identity,
                        bootId = bootIdProvider(),
                        appGeneration = appGeneration.incrementAndGet(),
                        daemonGeneration = daemonGeneration,
                        createdAtMillis = clock(),
                        globalMode = globalMode,
                    ),
                )
            } catch (e: DaemonSnapshotCodec.CodecException) {
                // An unusable route or state is not a daemon fault, but it still means
                // the daemon cannot own this apply.
                FileLogger.w("Daemon", "snapshot not buildable: ${e.message}")
                lastError = DaemonApplyError.BAD_METADATA
                statusFlow.value = DaemonBackendStatus.Fallback
                return false
            }

        val result = client.syncState(snapshot)
        lastError = result.error
        if (result.accepted) {
            // Track the daemon's generation so the next apply is not stale.
            if (result.daemonGeneration != 0L) daemonGeneration = result.daemonGeneration
            statusFlow.value = DaemonBackendStatus.Active
            return true
        }

        FileLogger.w("Daemon", "daemon apply refused (${result.error}); using legacy backend")
        statusFlow.value = DaemonBackendStatus.Fallback
        return false
    }

    /**
     * Adopts the daemon's current generation after a stale refusal.
     *
     * The driver event wire carries no daemon generation, so the only authoritative
     * source is the state file the daemon publishes. Returns false when it cannot be
     * read, in which case a blind retry would just be stale again.
     */
    private fun reconcileGeneration(): Boolean {
        val status = statusReader.read()
        if (status == null) {
            FileLogger.w("Daemon", "stale apply and daemon state unreadable; no retry")
            return false
        }
        if (status.daemonGeneration <= daemonGeneration) {
            // Nothing newer to adopt: retrying would repeat the same refusal.
            FileLogger.w(
                "Daemon",
                "stale apply but daemon generation ${status.daemonGeneration} is not newer",
            )
            return false
        }
        daemonGeneration = status.daemonGeneration
        lastRouteEpoch = status.routeEpoch
        return true
    }

    /**
     * Connects and handshakes, reporting whether the daemon is usable.
     *
     * The hello is part of the probe rather than a later step: adopting the
     * daemon's generation up front avoids a guaranteed stale-generation refusal on
     * the very first apply after the daemon has already restored a route snapshot.
     */
    suspend fun probe(): Boolean {
        val connected = client.connect()
        if (!connected) {
            lastError = DaemonApplyError.NOT_STAGING
            statusFlow.value = DaemonBackendStatus.Fallback
            return false
        }

        val ack = client.hello(appGeneration.get())
        if (ack == null) {
            // Reachable but not speaking the app protocol: treat as unusable rather
            // than assuming an apply would land.
            lastError = DaemonApplyError.DECODE_FAILED
            statusFlow.value = DaemonBackendStatus.Fallback
            return false
        }

        if (ack.daemonGeneration > daemonGeneration) daemonGeneration = ack.daemonGeneration
        lastRouteEpoch = ack.routeEpoch
        FileLogger.i(
            "Daemon",
            "daemon hello: generation=${ack.daemonGeneration} epoch=${ack.routeEpoch} " +
                "restore=${ack.restoreEnabled} driver=${ack.driverConnected} route=${ack.routeKnown}",
        )
        return true
    }

    suspend fun shutdown() {
        client.disconnect()
        statusFlow.value = DaemonBackendStatus.Unknown
    }

    private companion object {
        // Non-zero because the schema rejects a zero boot id. Replaced by the real
        // boot id once ViperService can read it.
        const val DEFAULT_BOOT_ID = 1L
    }
}

package com.llsl.viper4android.daemon

import android.net.LocalSocket
import android.net.LocalSocketAddress
import com.llsl.viper4android.utils.FileLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Observable connection state of the App-to-daemon link. */
enum class DaemonConnectionState {
    Disconnected,
    Connecting,
    Connected,

    /** A snapshot transfer is in flight. */
    Syncing,

    /** Handshake and last apply both succeeded. */
    Ready,

    /**
     * Reachable but not trusted for state application, or unreachable entirely.
     * The legacy direct-to-driver backend stays in charge.
     */
    Degraded,
}

/**
 * Result of one snapshot apply attempt.
 *
 * `errorCode` mirrors the native `ApplyError`, so a caller can distinguish a
 * stale generation (retry with fresh generations) from a device mismatch (the
 * route moved) without parsing strings.
 */
data class DaemonApplyResult(
    val accepted: Boolean,
    val errorCode: Int = 0,
    val appGeneration: Long = 0,
    val daemonGeneration: Long = 0,
    val resourceGeneration: Long = 0,
    val graphGeneration: Long = 0,
) {
    val error: DaemonApplyError get() = DaemonApplyError.fromCode(errorCode)
}

/** Mirrors `viper::audio::ApplyError` in src/SnapshotApplyController.h. */
enum class DaemonApplyError(
    val code: Int,
) {
    NONE(0),
    NOT_STAGING(1),
    ALREADY_STAGING(2),
    BAD_METADATA(3),
    CHUNK_OUT_OF_ORDER(4),
    CHUNK_RANGE(5),
    SIZE_MISMATCH(6),
    CRC_MISMATCH(7),
    DECODE_FAILED(8),
    DEVICE_MISMATCH(9),
    STALE_GENERATION(10),
    GRAPH_PREPARE_FAILED(11),
    ABORTED(12),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromCode(code: Int): DaemonApplyError = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/**
 * Transport abstraction over the abstract-namespace socket.
 *
 * Exists so the client's framing, sequencing and reconnect logic can be tested
 * on the JVM: `LocalSocket` is an Android class with no unit-test implementation.
 */
interface DaemonTransport {
    /** Opens a connection, or throws [IOException] when the daemon is absent. */
    fun connect(socketName: String): Connection

    interface Connection : AutoCloseable {
        fun write(bytes: ByteArray)

        /** Reads exactly one frame, or null at end of stream. */
        fun readFrame(): ByteArray?
    }
}

/** Real transport: abstract `LocalSocket`, matching the native bind. */
class LocalSocketTransport : DaemonTransport {
    override fun connect(socketName: String): DaemonTransport.Connection {
        val socket = LocalSocket(LocalSocket.SOCKET_SEQPACKET)
        socket.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
        return SocketConnection(socket)
    }

    private class SocketConnection(
        private val socket: LocalSocket,
    ) : DaemonTransport.Connection {
        private val output: OutputStream = socket.outputStream
        private val input: InputStream = socket.inputStream
        private val buffer = ByteArray(DaemonProtocol.MAX_FRAME_SIZE)

        override fun write(bytes: ByteArray) {
            output.write(bytes)
            output.flush()
        }

        override fun readFrame(): ByteArray? {
            // SOCK_SEQPACKET preserves message boundaries, so one read is one frame.
            val read = input.read(buffer)
            if (read <= 0) return null
            return buffer.copyOfRange(0, read)
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }
}

/**
 * App-side client for the root daemon.
 *
 * All socket IO runs on [ioDispatcher]; nothing here touches the main thread. A
 * single [requestMutex] serializes transfers because the snapshot protocol is
 * stateful: BEGIN, sequential CHUNKs, then COMMIT.
 */
class DaemonClient(
    // The driver endpoint admits only root/audioserver peers, because a driver
    // event is trusted lifecycle data. The App is an ordinary uid, so it gets its
    // own endpoint; pointing here at DRIVER_SOCKET_NAME makes every connection
    // refused by peer-credential checks.
    private val socketName: String = DaemonProtocol.APP_SOCKET_NAME,
    private val transport: DaemonTransport = LocalSocketTransport(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val chunkSize: Int = DaemonProtocol.MAX_SNAPSHOT_CHUNK_BYTES,
    private val maxReconnectAttempts: Int = 3,
    private val reconnectDelayMillis: Long = 100,
) {
    private val stateFlow = MutableStateFlow(DaemonConnectionState.Disconnected)
    val state: StateFlow<DaemonConnectionState> = stateFlow.asStateFlow()

    private val requestMutex = Mutex()
    private var connection: DaemonTransport.Connection? = null
    private var nextRequestId: Long = 1

    /** Last apply outcome, so a caller can inspect why the backend is degraded. */
    @Volatile
    var lastResult: DaemonApplyResult? = null
        private set

    fun observe(): StateFlow<DaemonConnectionState> = state

    suspend fun connect(): Boolean =
        requestMutex.withLock {
            connectLocked()
        }

    private suspend fun connectLocked(): Boolean {
        if (connection != null) return true
        stateFlow.value = DaemonConnectionState.Connecting
        return withContext(ioDispatcher) {
            var attempt = 0
            while (attempt < maxReconnectAttempts) {
                try {
                    connection = transport.connect(socketName)
                    stateFlow.value = DaemonConnectionState.Connected
                    return@withContext true
                } catch (e: IOException) {
                    attempt++
                    FileLogger.d("Daemon", "connect attempt $attempt failed: ${e.message}")
                    if (attempt < maxReconnectAttempts) {
                        // Bounded backoff: the daemon may be starting, but the App must
                        // not stall indefinitely waiting for it.
                        delay(reconnectDelayMillis * attempt)
                    }
                }
            }
            stateFlow.value = DaemonConnectionState.Degraded
            false
        }
    }

    /**
     * Performs the app-endpoint handshake.
     *
     * Returns the daemon's ack, or null when the daemon is unreachable or answers
     * something unusable. The ack carries the daemon's generation and current
     * route, which is what lets the App start from the daemon's view instead of
     * guessing and being refused as stale.
     */
    suspend fun hello(appGeneration: Long): AppProtocol.AppHelloAck? =
        requestMutex.withLock {
            if (!connectLocked()) return@withLock null
            val active = connection ?: return@withLock null
            try {
                withContext(ioDispatcher) {
                    sendCommand(
                        active,
                        AppProtocol.MSG_APP_HELLO,
                        AppProtocol.encodeAppHello(AppProtocol.AppHello(appGeneration = appGeneration)),
                        nextRequestId++,
                    )
                    val payload = awaitPayload(active, AppProtocol.MSG_APP_HELLO_ACK)
                    AppProtocol.decodeAppHelloAck(payload)
                }
            } catch (e: IOException) {
                FileLogger.d("Daemon", "hello failed: ${e.message}")
                withContext(ioDispatcher) { dropConnectionLocked() }
                stateFlow.value = DaemonConnectionState.Degraded
                null
            } catch (e: DaemonSnapshotCodec.CodecException) {
                FileLogger.e("Daemon", "daemon sent an invalid hello ack: ${e.message}")
                withContext(ioDispatcher) { dropConnectionLocked() }
                stateFlow.value = DaemonConnectionState.Degraded
                null
            } catch (e: DaemonProtocol.FrameException) {
                FileLogger.e("Daemon", "daemon sent an invalid frame: ${e.error}")
                withContext(ioDispatcher) { dropConnectionLocked() }
                stateFlow.value = DaemonConnectionState.Degraded
                null
            }
        }

    /**
     * Reports the App's current output route to the daemon.
     *
     * The daemon cannot see the live mixer, so this is the only way it learns the
     * route it must key snapshots by. Returns the daemon's ack, or null on failure.
     */
    suspend fun reportRoute(report: AppProtocol.AppRouteReport): AppProtocol.AppRouteAck? =
        requestMutex.withLock {
            if (!connectLocked()) return@withLock null
            val active = connection ?: return@withLock null
            val payload =
                try {
                    AppProtocol.encodeAppRouteReport(report)
                } catch (e: DaemonSnapshotCodec.CodecException) {
                    // An unusable route must not be sent: the daemon would refuse it and
                    // the failure would look like a transport problem.
                    FileLogger.w("Daemon", "route report not encodable: ${e.message}")
                    return@withLock null
                }
            try {
                withContext(ioDispatcher) {
                    sendCommand(active, AppProtocol.MSG_APP_ROUTE_REPORT, payload, nextRequestId++)
                    AppProtocol.decodeAppRouteAck(awaitPayload(active, AppProtocol.MSG_APP_ROUTE_ACK))
                }
            } catch (e: IOException) {
                FileLogger.w("Daemon", "route report failed: ${e.message}")
                withContext(ioDispatcher) { dropConnectionLocked() }
                stateFlow.value = DaemonConnectionState.Degraded
                null
            } catch (e: DaemonSnapshotCodec.CodecException) {
                FileLogger.e("Daemon", "daemon sent an invalid route ack: ${e.message}")
                withContext(ioDispatcher) { dropConnectionLocked() }
                stateFlow.value = DaemonConnectionState.Degraded
                null
            } catch (e: DaemonProtocol.FrameException) {
                FileLogger.e("Daemon", "daemon sent an invalid frame: ${e.error}")
                withContext(ioDispatcher) { dropConnectionLocked() }
                stateFlow.value = DaemonConnectionState.Degraded
                null
            }
        }

    suspend fun disconnect() {
        requestMutex.withLock {
            withContext(ioDispatcher) {
                connection?.let { runCatching { it.close() } }
                connection = null
            }
            stateFlow.value = DaemonConnectionState.Disconnected
        }
    }

    private fun dropConnectionLocked() {
        connection?.let { runCatching { it.close() } }
        connection = null
    }

    /**
     * Streams `snapshot` to the daemon and returns the driver's apply result.
     *
     * On a transport failure the connection is dropped and the state becomes
     * [DaemonConnectionState.Degraded], so the caller falls back to the legacy
     * backend rather than assuming the state landed.
     */
    suspend fun syncState(snapshot: DaemonSnapshotCodec.Snapshot): DaemonApplyResult =
        requestMutex.withLock {
            if (!connectLocked()) {
                return@withLock failed(DaemonApplyError.NOT_STAGING)
            }
            val active = connection ?: return@withLock failed(DaemonApplyError.NOT_STAGING)

            val bytes =
                try {
                    DaemonSnapshotCodec.encodeSnapshot(snapshot)
                } catch (e: DaemonSnapshotCodec.CodecException) {
                    // A snapshot we cannot encode must never be reported as applied.
                    FileLogger.e("Daemon", "snapshot encode failed: ${e.message}")
                    return@withLock failed(DaemonApplyError.BAD_METADATA)
                }

            stateFlow.value = DaemonConnectionState.Syncing
            val requestId = nextRequestId++
            try {
                withContext(ioDispatcher) {
                    sendCommand(active, DaemonProtocol.CMD_SNAPSHOT_BEGIN, beginPayload(snapshot, bytes), requestId)
                    val beginResult = awaitApplyResult(active)
                    if (!beginResult.accepted) return@withContext beginResult

                    for (chunk in DaemonSnapshotCodec.chunkSnapshot(bytes, chunkSize)) {
                        sendCommand(active, DaemonProtocol.CMD_SNAPSHOT_CHUNK, chunk, requestId)
                    }

                    sendCommand(
                        active,
                        DaemonProtocol.CMD_SNAPSHOT_COMMIT,
                        DaemonSnapshotCodec.encodeSnapshotCommit(snapshot.appGeneration, snapshot.daemonGeneration),
                        requestId,
                    )
                    awaitApplyResult(active)
                }.also { result ->
                    lastResult = result
                    stateFlow.value =
                        if (result.accepted) DaemonConnectionState.Ready else DaemonConnectionState.Degraded
                }
            } catch (e: IOException) {
                FileLogger.w("Daemon", "snapshot sync failed: ${e.message}")
                withContext(ioDispatcher) { dropConnectionLocked() }
                stateFlow.value = DaemonConnectionState.Degraded
                failed(DaemonApplyError.GRAPH_PREPARE_FAILED)
            } catch (e: DaemonProtocol.FrameException) {
                // A malformed frame from a root daemon is not something to retry blindly.
                FileLogger.e("Daemon", "daemon sent an invalid frame: ${e.error}")
                withContext(ioDispatcher) { dropConnectionLocked() }
                stateFlow.value = DaemonConnectionState.Degraded
                failed(DaemonApplyError.DECODE_FAILED)
            }
        }

    /** Abandons an in-flight transfer; safe to call when nothing is staged. */
    suspend fun abortSync(reason: Int = DaemonApplyError.ABORTED.code) {
        requestMutex.withLock {
            val active = connection ?: return@withLock
            try {
                withContext(ioDispatcher) {
                    sendCommand(
                        active,
                        DaemonProtocol.CMD_SNAPSHOT_ABORT,
                        DaemonSnapshotCodec.encodeSnapshotAbort(reason),
                        nextRequestId++,
                    )
                    awaitApplyResult(active)
                }
            } catch (e: IOException) {
                withContext(ioDispatcher) { dropConnectionLocked() }
                stateFlow.value = DaemonConnectionState.Degraded
            }
        }
    }

    private fun beginPayload(
        snapshot: DaemonSnapshotCodec.Snapshot,
        bytes: ByteArray,
    ): ByteArray =
        DaemonSnapshotCodec.encodeSnapshotBegin(
            DaemonSnapshotCodec.SnapshotBegin(
                appGeneration = snapshot.appGeneration,
                daemonGeneration = snapshot.daemonGeneration,
                totalSize = bytes.size,
                crc32 = DaemonProtocol.crc32(bytes),
                deviceKeyHash = snapshot.deviceKeyHash,
            ),
        )

    private fun sendCommand(
        connection: DaemonTransport.Connection,
        messageType: Int,
        payload: ByteArray,
        requestId: Long,
    ) {
        connection.write(
            DaemonProtocol.encodeFrame(
                DaemonProtocol.FrameHeader(messageType = messageType, requestId = requestId),
                payload,
            ),
        )
    }

    /**
     * Reads until a frame of `messageType` arrives.
     *
     * Other frames are skipped rather than misparsed with the wrong codec, but the
     * skip is bounded so a chatty or looping daemon cannot hang the caller.
     */
    private fun awaitPayload(
        connection: DaemonTransport.Connection,
        messageType: Int,
    ): ByteArray {
        for (attempt in 0 until MAX_FRAMES_PER_RESULT) {
            val raw = connection.readFrame() ?: throw IOException("daemon closed the connection")
            val frame = DaemonProtocol.decodeFrame(raw)
            if (frame.header.messageType == messageType) return frame.payload
        }
        throw IOException("no reply of type $messageType within $MAX_FRAMES_PER_RESULT frames")
    }

    /**
     * Reads until an apply result arrives.
     *
     * The daemon may interleave other frames, so unrelated message types are
     * skipped rather than mistaken for a result. Two shapes are accepted because
     * the same client code serves both endpoints: `APP_APPLY_RESULT` on
     * `@viper4android.app.v1`, and the driver's raw `SNAPSHOT_APPLIED_ACK`/`NACK`
     * event when talking straight to the driver endpoint.
     */
    private fun awaitApplyResult(connection: DaemonTransport.Connection): DaemonApplyResult {
        for (attempt in 0 until MAX_FRAMES_PER_RESULT) {
            val raw = connection.readFrame() ?: throw IOException("daemon closed the connection")
            val frame = DaemonProtocol.decodeFrame(raw)
            when (frame.header.messageType) {
                AppProtocol.MSG_APP_APPLY_RESULT ->
                    return decodeAppApplyResult(frame.payload)
                DaemonProtocol.EVENT_SNAPSHOT_APPLIED_ACK ->
                    return decodeApplyEvent(frame.payload, accepted = true)
                DaemonProtocol.EVENT_SNAPSHOT_APPLIED_NACK ->
                    return decodeApplyEvent(frame.payload, accepted = false)
                else -> continue
            }
        }
        throw IOException("no apply result within $MAX_FRAMES_PER_RESULT frames")
    }

    private fun decodeAppApplyResult(payload: ByteArray): DaemonApplyResult {
        val result = AppProtocol.decodeAppApplyResult(payload)
        return DaemonApplyResult(
            accepted = result.accepted,
            errorCode = result.errorCode,
            appGeneration = result.appGeneration,
            // Unlike the driver event, the app endpoint reports the daemon's own
            // generation, which is what makes stale-generation reconciliation work
            // without rereading the daemon's state file.
            daemonGeneration = result.daemonGeneration,
            resourceGeneration = result.resourceGeneration,
            graphGeneration = result.graphGeneration,
        )
    }

    private fun decodeApplyEvent(
        payload: ByteArray,
        accepted: Boolean,
    ): DaemonApplyResult {
        val event = DaemonDriverEvent.decode(payload)
        return DaemonApplyResult(
            accepted = accepted,
            errorCode = event.bypassReason,
            appGeneration = event.sessionGeneration,
            daemonGeneration = 0,
            resourceGeneration = event.resourceGeneration,
            graphGeneration = event.graphGeneration,
        )
    }

    private fun failed(error: DaemonApplyError): DaemonApplyResult {
        val result = DaemonApplyResult(accepted = false, errorCode = error.code)
        lastResult = result
        return result
    }

    private companion object {
        // Bounds the lifecycle-event skip loop so a chatty driver cannot hang a sync.
        const val MAX_FRAMES_PER_RESULT = 64
    }
}

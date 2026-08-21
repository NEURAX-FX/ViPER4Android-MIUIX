package com.llsl.viper4android.daemon

import com.llsl.viper4android.effect.BassState
import com.llsl.viper4android.effect.EffectState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DaemonBackendTest {
    private val identity =
        DaemonProtocol.DeviceIdentity(
            routeType = "speaker",
            stableAddressOrPort = "builtin",
            productName = "internal",
            sampleRate = 48000,
            channelMask = 3,
            encoding = "pcm_16",
        )

    private val state = EffectState(masterEnable = true, bass = BassState(enable = true, frequency = 60))

    private fun applyEvent(
        accepted: Boolean,
        errorCode: Int = 0,
        appGeneration: Long = 0,
    ): ByteArray {
        val payload = ByteBuffer.allocate(DaemonDriverEvent.WIRE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val type =
            if (accepted) DaemonProtocol.EVENT_SNAPSHOT_APPLIED_ACK else DaemonProtocol.EVENT_SNAPSHOT_APPLIED_NACK
        payload.putShort(type.toShort())
        payload.putShort(0)
        payload.putLong(0)
        payload.putLong(1)
        payload.putLong(1)
        payload.putInt(0)
        payload.putInt(0)
        payload.putInt(0)
        payload.putInt(0)
        payload.putLong(appGeneration)
        payload.putLong(11)
        payload.putLong(12)
        payload.putInt(errorCode)
        return DaemonProtocol.encodeFrame(
            DaemonProtocol.FrameHeader(messageType = type),
            payload.array(),
        )
    }

    /**
     * Stands in for the daemon on `@viper4android.app.v1`.
     *
     * Answers the handshake and route report itself, because those are part of the
     * protocol rather than test scaffolding: a blind reply queue would hand a
     * snapshot ACK to the hello and every later reply would be off by one. Only
     * snapshot results come from `enqueue`, which is what a test actually varies.
     */
    private class FakeTransport(
        private val failConnect: Boolean = false,
        private val helloAck: ByteArray? = null,
        private val routeAck: ByteArray? = null,
    ) : DaemonTransport {
        private val replies = ArrayDeque<ByteArray>()
        private val pending = ArrayDeque<ByteArray>()
        val written = ArrayList<ByteArray>()

        fun enqueue(vararg frames: ByteArray) {
            frames.forEach { replies.addLast(it) }
        }

        override fun connect(socketName: String): DaemonTransport.Connection {
            if (failConnect) throw IOException("daemon absent")
            return object : DaemonTransport.Connection {
                override fun write(bytes: ByteArray) {
                    written.add(bytes)
                    when (DaemonProtocol.decodeFrame(bytes).header.messageType) {
                        AppProtocol.MSG_APP_HELLO -> pending.addLast(helloAck ?: defaultHelloAck())
                        AppProtocol.MSG_APP_ROUTE_REPORT -> pending.addLast(routeAck ?: defaultRouteAck())
                        // A chunk is never individually acknowledged, so it must not
                        // consume a queued result.
                        DaemonProtocol.CMD_SNAPSHOT_CHUNK -> Unit
                        else -> replies.removeFirstOrNull()?.let { pending.addLast(it) }
                    }
                }

                override fun readFrame(): ByteArray? = pending.removeFirstOrNull()

                override fun close() = Unit
            }
        }

        fun beginPayloads(): List<DaemonSnapshotCodec.SnapshotBegin> =
            written
                .map { DaemonProtocol.decodeFrame(it) }
                .filter { it.header.messageType == DaemonProtocol.CMD_SNAPSHOT_BEGIN }
                .map { frame ->
                    val buffer = ByteBuffer.wrap(frame.payload).order(ByteOrder.LITTLE_ENDIAN)
                    val version = buffer.short.toInt()
                    buffer.short
                    val totalSize = buffer.int
                    val appGeneration = buffer.long
                    val daemonGeneration = buffer.long
                    val crc = buffer.int
                    buffer.int
                    val hash = ByteArray(64).also { buffer.get(it) }.decodeToString()
                    DaemonSnapshotCodec.SnapshotBegin(
                        appGeneration = appGeneration,
                        daemonGeneration = daemonGeneration,
                        totalSize = totalSize,
                        crc32 = crc,
                        deviceKeyHash = hash,
                        version = version,
                    )
                }

        private companion object {
            // The hash the daemon reports back for the route these tests use, so an
            // accepted route ack names the same route the snapshot is keyed by.
            val SPEAKER_ROUTE_HASH: String =
                DaemonProtocol.hashDeviceKey(
                    "speaker|builtin|internal|48000|3|pcm_16|0",
                )

            // A daemon that is up, has a driver and knows no route yet: the state a
            // freshly started daemon is actually in before the App reports a route.
            fun defaultHelloAck(): ByteArray =
                DaemonProtocol.encodeFrame(
                    DaemonProtocol.FrameHeader(messageType = AppProtocol.MSG_APP_HELLO_ACK),
                    AppProtocol.encodeAppHelloAck(
                        AppProtocol.AppHelloAck(
                            flags = AppProtocol.FLAG_RESTORE_ENABLED or AppProtocol.FLAG_DRIVER_CONNECTED,
                            daemonGeneration = 1,
                            routeEpoch = 0,
                            // No route yet: the daemon has nothing to name until the
                            // App reports one.
                            routeKeyHash = "",
                        ),
                    ),
                )

            fun defaultRouteAck(): ByteArray =
                DaemonProtocol.encodeFrame(
                    DaemonProtocol.FrameHeader(messageType = AppProtocol.MSG_APP_ROUTE_ACK),
                    AppProtocol.encodeAppRouteAck(
                        AppProtocol.AppRouteAck(
                            accepted = true,
                            daemonGeneration = 1,
                            routeEpoch = 1,
                            routeKeyHash = SPEAKER_ROUTE_HASH,
                        ),
                    ),
                )
        }
    }

    /**
     * Builds a backend whose daemon state file is supplied by the test.
     *
     * `stateContents` defaults to null, meaning "no readable daemon state". Without
     * this seam `DaemonBackend` falls back to its production reader, which shells
     * out to `su cat /data/adb/viper4android/daemon.state`; on a rooted device the
     * unit test then reconciles against whatever generation the live daemon happens
     * to publish, so the same test passes or fails depending on machine state.
     */
    private fun backend(
        transport: FakeTransport,
        stateContents: String? = null,
    ): DaemonBackend =
        DaemonBackend(
            client =
                DaemonClient(
                    socketName = "viper4android.test",
                    transport = transport,
                    ioDispatcher = Dispatchers.Unconfined,
                    maxReconnectAttempts = 1,
                    reconnectDelayMillis = 0,
                ),
            bootIdProvider = { 0x1122334455667788L },
            clock = { 1700000000000L },
            statusReader = DaemonStatusReader(reader = { stateContents }),
        )

    @Test
    fun `accepted apply activates the daemon backend`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(applyEvent(accepted = true), applyEvent(accepted = true))
            val backend = backend(transport)

            assertTrue(backend.applyState(state, identity))
            assertTrue(backend.isActive)
            assertEquals(DaemonBackendStatus.Active, backend.status.value)
            assertEquals(DaemonApplyError.NONE, backend.lastError)
        }

    @Test
    fun `missing daemon falls back instead of reporting success`() =
        runTest {
            val backend = backend(FakeTransport(failConnect = true))

            // A false return is what keeps the legacy AudioEffect path in charge.
            assertFalse(backend.applyState(state, identity))
            assertFalse(backend.isActive)
            assertEquals(DaemonBackendStatus.Fallback, backend.status.value)
        }

    @Test
    fun `probe reports reachability without applying state`() =
        runTest {
            val reachable = FakeTransport()
            assertTrue(backend(reachable).probe())
            // Probing handshakes, which is how the daemon's generation is adopted, but
            // it must not stream a snapshot: nothing has been applied yet.
            assertTrue(reachable.beginPayloads().isEmpty())
            assertTrue(
                reachable.written.none {
                    DaemonProtocol.decodeFrame(it).header.messageType == DaemonProtocol.CMD_SNAPSHOT_COMMIT
                },
            )

            val absent = FakeTransport(failConnect = true)
            val backend = backend(absent)
            assertFalse(backend.probe())
            assertEquals(DaemonBackendStatus.Fallback, backend.status.value)
        }

    @Test
    fun `nack falls back and records the driver error`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(applyEvent(accepted = false, errorCode = DaemonApplyError.DEVICE_MISMATCH.code))
            val backend = backend(transport)

            assertFalse(backend.applyState(state, identity))
            assertEquals(DaemonBackendStatus.Fallback, backend.status.value)
            // The specific cause must survive, so the caller can log or retry sensibly.
            assertEquals(DaemonApplyError.DEVICE_MISMATCH, backend.lastError)
        }

    @Test
    fun `unusable route falls back without touching the socket`() =
        runTest {
            val transport = FakeTransport()
            val backend = backend(transport)

            // sampleRate 0 cannot produce a device key, so no snapshot exists to send.
            assertFalse(backend.applyState(state, identity.copy(sampleRate = 0)))
            assertEquals(DaemonBackendStatus.Fallback, backend.status.value)
            assertEquals(DaemonApplyError.BAD_METADATA, backend.lastError)
            assertTrue(transport.written.isEmpty())
        }

    @Test
    fun `app generation increases monotonically across applies`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(
                applyEvent(accepted = true),
                applyEvent(accepted = true),
                applyEvent(accepted = true),
                applyEvent(accepted = true),
            )
            val backend = backend(transport)

            assertTrue(backend.applyState(state, identity))
            assertTrue(backend.applyState(state, identity))

            val generations = transport.beginPayloads().map { it.appGeneration }
            assertEquals(listOf(1L, 2L), generations)
            // A repeated generation would be rejected as idempotent or stale.
            assertEquals(generations.distinct().size, generations.size)
        }

    @Test
    fun `stale generation is adopted so the retry is not stale again`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(
                // The daemon refuses BEGIN because it moved ahead after a route restore.
                applyEvent(accepted = false, errorCode = DaemonApplyError.STALE_GENERATION.code),
                applyEvent(accepted = true),
                applyEvent(accepted = true),
            )
            // The daemon's own state file is the only place the newer generation is
            // published, so the retry can only be non-stale by reading it.
            val backend = backend(transport, stateContents = "daemon_generation=7\nroute_epoch=4\n")

            // One call: the refusal is reconciled and retried internally.
            assertTrue(backend.applyState(state, identity))
            assertEquals(DaemonBackendStatus.Active, backend.status.value)
            assertEquals(7L, backend.currentDaemonGeneration)
            assertEquals(4L, backend.currentRouteEpoch)

            // The retry must be based on the adopted generation, not the stale one.
            assertEquals(listOf(1L, 7L), transport.beginPayloads().map { it.daemonGeneration })
        }

    @Test
    fun `stale refusal without readable daemon state is not retried`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(
                applyEvent(accepted = false, errorCode = DaemonApplyError.STALE_GENERATION.code),
                applyEvent(accepted = true),
                applyEvent(accepted = true),
            )
            // No readable state file: a blind retry would carry the same stale
            // generation and be refused again.
            val backend = backend(transport, stateContents = null)

            assertFalse(backend.applyState(state, identity))
            assertEquals(DaemonApplyError.STALE_GENERATION, backend.lastError)
            assertEquals(DaemonBackendStatus.Fallback, backend.status.value)
            assertEquals(1, transport.beginPayloads().size)
        }

    @Test
    fun `stale refusal is not retried when the daemon generation is not newer`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(
                applyEvent(accepted = false, errorCode = DaemonApplyError.STALE_GENERATION.code),
                applyEvent(accepted = true),
                applyEvent(accepted = true),
            )
            // Generation 1 is what the handshake already adopted; retrying at the same
            // generation would just repeat the refusal.
            val backend = backend(transport, stateContents = "daemon_generation=1\n")

            assertFalse(backend.applyState(state, identity))
            assertEquals(DaemonApplyError.STALE_GENERATION, backend.lastError)
            assertEquals(1, transport.beginPayloads().size)
        }

    @Test
    fun `snapshot carries the route hash the daemon will match on`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(applyEvent(accepted = true), applyEvent(accepted = true))
            val backend = backend(transport)

            backend.applyState(state, identity)

            val expected =
                DaemonProtocol.hashDeviceKey(requireNotNull(DaemonProtocol.normalizeDeviceKey(identity)))
            assertEquals(expected, transport.beginPayloads().single().deviceKeyHash)
        }

    @Test
    fun `shutdown resets status and disconnects`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(applyEvent(accepted = true), applyEvent(accepted = true))
            val backend = backend(transport)

            backend.applyState(state, identity)
            assertEquals(DaemonBackendStatus.Active, backend.status.value)

            backend.shutdown()
            assertEquals(DaemonBackendStatus.Unknown, backend.status.value)
            assertEquals(DaemonConnectionState.Disconnected, backend.connectionState.value)
        }

    @Test
    fun `initial status is unknown until an apply is attempted`() {
        val backend = backend(FakeTransport())
        // Unknown, not Active: nothing has been proven yet.
        assertEquals(DaemonBackendStatus.Unknown, backend.status.value)
        assertFalse(backend.isActive)
    }
}

package com.llsl.viper4android.daemon

import com.llsl.viper4android.effect.BassState
import com.llsl.viper4android.effect.EffectState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * App-side reconciliation after a daemon route restore.
 *
 * The daemon bumps its generation on every restore, so an App apply built on an
 * older generation is refused as `STALE_GENERATION`. The App must adopt the
 * daemon's published generation and resend, otherwise the user's latest edits stay
 * unapplied until some unrelated change happens.
 */
class DaemonReconciliationTest {
    private val identity =
        DaemonProtocol.DeviceIdentity(
            routeType = "speaker",
            stableAddressOrPort = "builtin",
            productName = "speaker",
            sampleRate = 48000,
            channelMask = 3,
            encoding = "pcm_16",
        )

    private val state = EffectState(masterEnable = true, bass = BassState(enable = true, frequency = 60))

    private fun applyEvent(
        accepted: Boolean,
        errorCode: Int = 0,
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
        payload.putLong(0) // session generation (App generation echo)
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
     * The handshake and route report are answered here rather than queued, because
     * they are protocol steps: a blind reply queue would hand a snapshot result to
     * the hello and every later reply would be off by one. `enqueue` therefore only
     * carries snapshot apply results, which is what these tests actually vary.
     */
    private class FakeTransport : DaemonTransport {
        private val replies = ArrayDeque<ByteArray>()
        private val pending = ArrayDeque<ByteArray>()
        val written = ArrayList<ByteArray>()

        fun enqueue(vararg frames: ByteArray) {
            frames.forEach { replies.addLast(it) }
        }

        override fun connect(socketName: String): DaemonTransport.Connection =
            object : DaemonTransport.Connection {
                override fun write(bytes: ByteArray) {
                    written.add(bytes)
                    when (DaemonProtocol.decodeFrame(bytes).header.messageType) {
                        AppProtocol.MSG_APP_HELLO -> pending.addLast(helloAck())
                        AppProtocol.MSG_APP_ROUTE_REPORT -> pending.addLast(routeAck())
                        // Accepted chunks are never individually acknowledged, so they
                        // must not consume a queued apply result.
                        DaemonProtocol.CMD_SNAPSHOT_CHUNK -> Unit
                        else -> replies.removeFirstOrNull()?.let { pending.addLast(it) }
                    }
                }

                override fun readFrame(): ByteArray? = pending.removeFirstOrNull()

                override fun close() = Unit
            }

        /** Daemon generations carried by each BEGIN command that was sent. */
        fun beginGenerations(): List<Long> =
            written
                .map { DaemonProtocol.decodeFrame(it) }
                .filter { it.header.messageType == DaemonProtocol.CMD_SNAPSHOT_BEGIN }
                .map { frame ->
                    val buffer = ByteBuffer.wrap(frame.payload).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(16) // version, reserved, total_size, app_generation
                    buffer.long
                }

        fun beginCount(): Int =
            written.count {
                DaemonProtocol.decodeFrame(it).header.messageType == DaemonProtocol.CMD_SNAPSHOT_BEGIN
            }

        private companion object {
            // The daemon these tests model is up with a driver attached; the route the
            // App reports is always accepted, so a refusal cannot be mistaken for the
            // stale-generation behaviour under test.
            fun helloAck(): ByteArray =
                DaemonProtocol.encodeFrame(
                    DaemonProtocol.FrameHeader(messageType = AppProtocol.MSG_APP_HELLO_ACK),
                    AppProtocol.encodeAppHelloAck(
                        AppProtocol.AppHelloAck(
                            flags = AppProtocol.FLAG_RESTORE_ENABLED or AppProtocol.FLAG_DRIVER_CONNECTED,
                            daemonGeneration = 1,
                            routeEpoch = 0,
                            routeKeyHash = "",
                        ),
                    ),
                )

            fun routeAck(): ByteArray =
                DaemonProtocol.encodeFrame(
                    DaemonProtocol.FrameHeader(messageType = AppProtocol.MSG_APP_ROUTE_ACK),
                    AppProtocol.encodeAppRouteAck(
                        AppProtocol.AppRouteAck(
                            accepted = true,
                            daemonGeneration = 1,
                            routeEpoch = 1,
                            routeKeyHash = "",
                        ),
                    ),
                )
        }
    }

    private fun stateFile(
        daemonGeneration: Long,
        routeEpoch: Long = 3,
        mode: String = "route-restore",
    ): String =
        """
        mode=$mode
        driver_connected=1
        route_known=1
        route_key_hash=${DaemonProtocol.hashDeviceKey(requireNotNull(DaemonProtocol.normalizeDeviceKey(identity)))}
        route_epoch=$routeEpoch
        daemon_generation=$daemonGeneration
        live_contexts=1
        applied_events=4
        restores_accepted=1
        restores_rejected=0
        """.trimIndent()

    private fun backend(
        transport: FakeTransport,
        stateContents: String?,
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
    fun `stale apply adopts the daemon generation and resends`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(
                // First BEGIN is refused: the daemon restored a route snapshot under a
                // newer generation than the App knew.
                applyEvent(accepted = false, errorCode = DaemonApplyError.STALE_GENERATION.code),
                // Retry succeeds.
                applyEvent(accepted = true),
                applyEvent(accepted = true),
            )
            val backend = backend(transport, stateFile(daemonGeneration = 9))

            assertTrue(backend.applyState(state, identity))
            assertEquals(DaemonBackendStatus.Active, backend.status.value)

            val generations = transport.beginGenerations()
            assertEquals(2, generations.size)
            // The first attempt used the App's stale value, the retry the daemon's.
            assertEquals(1L, generations[0])
            assertEquals(9L, generations[1])
            assertEquals(9L, backend.currentDaemonGeneration)
            // The route epoch is adopted too, so the App knows which restore it raced.
            assertEquals(3L, backend.currentRouteEpoch)
        }

    @Test
    fun `unreadable daemon state means no blind retry`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(
                applyEvent(accepted = false, errorCode = DaemonApplyError.STALE_GENERATION.code),
            )
            val backend = backend(transport, null)

            assertFalse(backend.applyState(state, identity))
            // Exactly one attempt: resending at the same generation would be refused
            // again, and the legacy backend is already covering the user.
            assertEquals(1, transport.beginCount())
            assertEquals(DaemonBackendStatus.Fallback, backend.status.value)
            assertEquals(DaemonApplyError.STALE_GENERATION, backend.lastError)
        }

    @Test
    fun `daemon generation that is not newer is not retried`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(
                applyEvent(accepted = false, errorCode = DaemonApplyError.STALE_GENERATION.code),
            )
            // The state file reports the generation the App already used.
            val backend = backend(transport, stateFile(daemonGeneration = 1))

            assertFalse(backend.applyState(state, identity))
            assertEquals(1, transport.beginCount())
            assertEquals(1L, backend.currentDaemonGeneration)
        }

    @Test
    fun `non stale refusals are not retried`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(
                applyEvent(accepted = false, errorCode = DaemonApplyError.DEVICE_MISMATCH.code),
            )
            val backend = backend(transport, stateFile(daemonGeneration = 99))

            assertFalse(backend.applyState(state, identity))
            // A device mismatch is not fixed by a newer generation; retrying would just
            // send the same wrong-route snapshot again.
            assertEquals(1, transport.beginCount())
            assertEquals(DaemonApplyError.DEVICE_MISMATCH, backend.lastError)
        }

    @Test
    fun `accepted apply never rereads the state file`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(applyEvent(accepted = true), applyEvent(accepted = true))
            var reads = 0
            val backend =
                DaemonBackend(
                    client =
                        DaemonClient(
                            socketName = "viper4android.test",
                            transport = transport,
                            ioDispatcher = Dispatchers.Unconfined,
                            maxReconnectAttempts = 1,
                            reconnectDelayMillis = 0,
                        ),
                    bootIdProvider = { 1L },
                    clock = { 1700000000000L },
                    statusReader =
                        DaemonStatusReader(reader = {
                            reads++
                            stateFile(daemonGeneration = 5)
                        }),
                )

            assertTrue(backend.applyState(state, identity))
            // The happy path must not pay for a root shell hop.
            assertEquals(0, reads)
        }

    @Test
    fun `readStatus exposes the daemon's published state`() {
        val transport = FakeTransport()
        val backend = backend(transport, stateFile(daemonGeneration = 7, routeEpoch = 12))

        val status = requireNotNull(backend.readStatus())
        assertTrue(status.restoreEnabled)
        assertTrue(status.driverConnected)
        assertTrue(status.routeKnown)
        assertEquals(7L, status.daemonGeneration)
        assertEquals(12L, status.routeEpoch)
        assertEquals(1L, status.restoresAccepted)
        assertEquals(12L, backend.currentRouteEpoch)
    }

    @Test
    fun `observe only daemon is reported as restore disabled`() {
        val backend =
            backend(FakeTransport(), stateFile(daemonGeneration = 1, mode = "observe-only"))
        val status = requireNotNull(backend.readStatus())
        assertFalse(status.restoreEnabled)
    }

    @Test
    fun `state parser ignores unknown keys and malformed lines`() {
        val parsed =
            DaemonStatusReader.parse(
                """
                mode=route-restore
                this line has no separator
                =leading separator
                daemon_generation=42
                future_diagnostic_field=whatever
                route_epoch=not-a-number
                """.trimIndent(),
            )
        // A newer daemon may add fields; that must not break the App.
        assertEquals("route-restore", parsed.mode)
        assertEquals(42L, parsed.daemonGeneration)
        // An unparsable number falls back to 0 rather than throwing.
        assertEquals(0L, parsed.routeEpoch)
    }

    @Test
    fun `missing state file yields no status`() {
        val reader = DaemonStatusReader(reader = { null })
        assertNull(reader.read())
    }
}

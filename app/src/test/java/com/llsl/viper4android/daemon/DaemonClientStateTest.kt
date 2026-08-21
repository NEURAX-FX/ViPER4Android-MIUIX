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
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DaemonClientStateTest {
    private val identity =
        DaemonProtocol.DeviceIdentity(
            routeType = "speaker",
            stableAddressOrPort = "builtin",
            productName = "internal",
            sampleRate = 48000,
            channelMask = 3,
            encoding = "pcm_16",
        )

    private fun snapshot(
        appGeneration: Long = 5,
        daemonGeneration: Long = 7,
    ) = DaemonSnapshotMapper.buildSnapshot(
        DaemonSnapshotMapper.SnapshotInputs(
            state = EffectState(masterEnable = true, bass = BassState(enable = true, frequency = 60)),
            identity = identity,
            bootId = 0x1122334455667788L,
            appGeneration = appGeneration,
            daemonGeneration = daemonGeneration,
            createdAtMillis = 1700000000000L,
        ),
    )

    /** Encodes a driver ACK/NACK exactly as the native bridge does. */
    private fun applyEvent(
        accepted: Boolean,
        errorCode: Int = 0,
        appGeneration: Long = 5,
        resourceGeneration: Long = 11,
        graphGeneration: Long = 12,
        requestId: Long = 1,
    ): ByteArray {
        val payload = ByteBuffer.allocate(DaemonDriverEvent.WIRE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        val type =
            if (accepted) DaemonProtocol.EVENT_SNAPSHOT_APPLIED_ACK else DaemonProtocol.EVENT_SNAPSHOT_APPLIED_NACK
        payload.putShort(type.toShort())
        payload.putShort(0) // enabled
        payload.putLong(0) // boot id
        payload.putLong(1) // event sequence
        payload.putLong(requestId) // context instance id
        payload.putInt(0) // audio session id
        payload.putInt(0) // io id
        payload.putInt(0) // sample rate
        payload.putInt(0) // channel mask
        payload.putLong(appGeneration) // session generation
        payload.putLong(resourceGeneration)
        payload.putLong(graphGeneration)
        payload.putInt(errorCode)
        return DaemonProtocol.encodeFrame(
            DaemonProtocol.FrameHeader(messageType = type, requestId = requestId),
            payload.array(),
        )
    }

    /** A lifecycle event the client must skip while waiting for an apply result. */
    private fun lifecycleEvent(): ByteArray =
        DaemonProtocol.encodeFrame(
            DaemonProtocol.FrameHeader(messageType = 2 /* CONTEXT_CREATED */),
            ByteArray(DaemonDriverEvent.WIRE_SIZE),
        )

    /** Scriptable transport: queued replies out, written frames recorded. */
    private class FakeTransport(
        private val replies: ArrayDeque<ByteArray> = ArrayDeque(),
        private val failConnectTimes: Int = 0,
    ) : DaemonTransport {
        val written = ArrayList<ByteArray>()
        var connectAttempts = 0
            private set
        var closeCount = 0
            private set
        var failWriteAfter: Int = Int.MAX_VALUE

        fun enqueue(vararg frames: ByteArray) {
            frames.forEach { replies.addLast(it) }
        }

        override fun connect(socketName: String): DaemonTransport.Connection {
            connectAttempts++
            if (connectAttempts <= failConnectTimes) throw IOException("daemon absent")
            return object : DaemonTransport.Connection {
                override fun write(bytes: ByteArray) {
                    if (written.size >= failWriteAfter) throw IOException("socket closed")
                    written.add(bytes)
                }

                override fun readFrame(): ByteArray? = replies.removeFirstOrNull()

                override fun close() {
                    closeCount++
                }
            }
        }

        fun writtenTypes(): List<Int> = written.map { DaemonProtocol.decodeFrame(it).header.messageType }
    }

    private fun client(
        transport: FakeTransport,
        chunkSize: Int = DaemonProtocol.MAX_SNAPSHOT_CHUNK_BYTES,
        maxReconnectAttempts: Int = 3,
    ) = DaemonClient(
        socketName = "viper4android.test",
        transport = transport,
        ioDispatcher = Dispatchers.Unconfined,
        chunkSize = chunkSize,
        maxReconnectAttempts = maxReconnectAttempts,
        reconnectDelayMillis = 0,
    )

    @Test
    fun `initial state is disconnected`() {
        val client = client(FakeTransport())
        assertEquals(DaemonConnectionState.Disconnected, client.state.value)
        assertNull(client.lastResult)
    }

    @Test
    fun `successful sync walks begin chunk commit and reports ready`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(applyEvent(accepted = true), applyEvent(accepted = true))
            val client = client(transport)

            val result = client.syncState(snapshot())

            assertTrue(result.accepted)
            assertEquals(DaemonApplyError.NONE, result.error)
            assertEquals(11L, result.resourceGeneration)
            assertEquals(12L, result.graphGeneration)
            assertEquals(DaemonConnectionState.Ready, client.state.value)

            // Exactly one BEGIN, at least one CHUNK, exactly one COMMIT, in order.
            val types = transport.writtenTypes()
            assertEquals(DaemonProtocol.CMD_SNAPSHOT_BEGIN, types.first())
            assertEquals(DaemonProtocol.CMD_SNAPSHOT_COMMIT, types.last())
            assertEquals(1, types.count { it == DaemonProtocol.CMD_SNAPSHOT_BEGIN })
            assertEquals(1, types.count { it == DaemonProtocol.CMD_SNAPSHOT_COMMIT })
            assertTrue(types.count { it == DaemonProtocol.CMD_SNAPSHOT_CHUNK } >= 1)
        }

    @Test
    fun `all frames of one transfer share a request id`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(applyEvent(accepted = true), applyEvent(accepted = true))
            val client = client(transport)

            client.syncState(snapshot())

            // The daemon correlates its ACK by request id; a drifting id breaks that.
            val ids = transport.written.map { DaemonProtocol.decodeFrame(it).header.requestId }.distinct()
            assertEquals(1, ids.size)
        }

    @Test
    fun `chunks cover the snapshot sequentially`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(applyEvent(accepted = true), applyEvent(accepted = true))
            val client = client(transport, chunkSize = 64)

            val snap = snapshot()
            client.syncState(snap)

            val expected = DaemonSnapshotCodec.encodeSnapshot(snap)
            var offset = 0
            val reassembled = ByteArray(expected.size)
            for (frame in transport.written) {
                val decoded = DaemonProtocol.decodeFrame(frame)
                if (decoded.header.messageType != DaemonProtocol.CMD_SNAPSHOT_CHUNK) continue
                val buffer = ByteBuffer.wrap(decoded.payload).order(ByteOrder.LITTLE_ENDIAN)
                val chunkOffset = buffer.int
                val length = buffer.int
                buffer.long // reserved
                // The driver rejects a gap or a rewind, so offsets must be sequential.
                assertEquals(offset, chunkOffset)
                buffer.get(reassembled, chunkOffset, length)
                offset += length
            }
            assertEquals(expected.size, offset)
            assertEquals(expected.toList(), reassembled.toList())
        }

    @Test
    fun `begin nack stops the transfer before any chunk`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(applyEvent(accepted = false, errorCode = DaemonApplyError.DEVICE_MISMATCH.code))
            val client = client(transport)

            val result = client.syncState(snapshot())

            assertFalse(result.accepted)
            assertEquals(DaemonApplyError.DEVICE_MISMATCH, result.error)
            assertEquals(DaemonConnectionState.Degraded, client.state.value)
            // Streaming a snapshot the daemon already refused would be wasted work.
            assertEquals(listOf(DaemonProtocol.CMD_SNAPSHOT_BEGIN), transport.writtenTypes())
        }

    @Test
    fun `commit nack maps the driver error code`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(
                applyEvent(accepted = true),
                applyEvent(accepted = false, errorCode = DaemonApplyError.STALE_GENERATION.code),
            )
            val client = client(transport)

            val result = client.syncState(snapshot())

            assertFalse(result.accepted)
            // A stale generation is retryable; a caller must be able to tell.
            assertEquals(DaemonApplyError.STALE_GENERATION, result.error)
            assertEquals(DaemonConnectionState.Degraded, client.state.value)
            assertEquals(result, client.lastResult)
        }

    @Test
    fun `lifecycle events are skipped while waiting for an apply result`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(
                lifecycleEvent(),
                lifecycleEvent(),
                applyEvent(accepted = true),
                lifecycleEvent(),
                applyEvent(accepted = true),
            )
            val client = client(transport)

            val result = client.syncState(snapshot())

            // The driver publishes context events on the same socket.
            assertTrue(result.accepted)
            assertEquals(DaemonConnectionState.Ready, client.state.value)
        }

    @Test
    fun `absent daemon degrades instead of throwing`() =
        runTest {
            val transport = FakeTransport(failConnectTimes = Int.MAX_VALUE)
            val client = client(transport, maxReconnectAttempts = 3)

            assertFalse(client.connect())
            assertEquals(DaemonConnectionState.Degraded, client.state.value)
            // Bounded retries: the App must not stall waiting for a missing daemon.
            assertEquals(3, transport.connectAttempts)

            val result = client.syncState(snapshot())
            assertFalse(result.accepted)
        }

    @Test
    fun `connect retries and succeeds within the attempt budget`() =
        runTest {
            val transport = FakeTransport(failConnectTimes = 2)
            transport.enqueue(applyEvent(accepted = true), applyEvent(accepted = true))
            val client = client(transport, maxReconnectAttempts = 3)

            assertTrue(client.connect())
            assertEquals(DaemonConnectionState.Connected, client.state.value)
            assertEquals(3, transport.connectAttempts)
        }

    @Test
    fun `closed connection mid transfer degrades and drops the socket`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(applyEvent(accepted = true))
            // Fail after BEGIN so the failure lands mid-transfer.
            transport.failWriteAfter = 2
            val client = client(transport)

            val result = client.syncState(snapshot())

            assertFalse(result.accepted)
            assertEquals(DaemonConnectionState.Degraded, client.state.value)
            // The dead socket must be closed, otherwise the next sync reuses it.
            assertTrue(transport.closeCount >= 1)
        }

    @Test
    fun `end of stream during handshake is reported as failure`() =
        runTest {
            // No replies queued: readFrame returns null immediately.
            val transport = FakeTransport()
            val client = client(transport)

            val result = client.syncState(snapshot())

            assertFalse(result.accepted)
            assertEquals(DaemonConnectionState.Degraded, client.state.value)
        }

    @Test
    fun `malformed daemon frame is rejected rather than trusted`() =
        runTest {
            val transport = FakeTransport()
            val corrupt = applyEvent(accepted = true)
            // Flip a payload byte, leaving the frame CRC stale.
            corrupt[DaemonProtocol.FRAME_HEADER_SIZE + 4] = (corrupt[DaemonProtocol.FRAME_HEADER_SIZE + 4] + 1).toByte()
            transport.enqueue(corrupt)
            val client = client(transport)

            val result = client.syncState(snapshot())

            assertFalse(result.accepted)
            assertEquals(DaemonApplyError.DECODE_FAILED, result.error)
            assertEquals(DaemonConnectionState.Degraded, client.state.value)
        }

    @Test
    fun `disconnect closes the socket and resets state`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(applyEvent(accepted = true), applyEvent(accepted = true))
            val client = client(transport)

            client.syncState(snapshot())
            assertEquals(DaemonConnectionState.Ready, client.state.value)

            client.disconnect()
            assertEquals(DaemonConnectionState.Disconnected, client.state.value)
            assertEquals(1, transport.closeCount)
        }

    @Test
    fun `consecutive syncs reuse one connection with distinct request ids`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(
                applyEvent(accepted = true),
                applyEvent(accepted = true),
                applyEvent(accepted = true),
                applyEvent(accepted = true),
            )
            val client = client(transport)

            assertTrue(client.syncState(snapshot(appGeneration = 5)).accepted)
            assertTrue(client.syncState(snapshot(appGeneration = 6)).accepted)

            // One connection for both transfers.
            assertEquals(1, transport.connectAttempts)
            val ids = transport.written.map { DaemonProtocol.decodeFrame(it).header.requestId }.distinct()
            // Distinct ids, so the daemon can tell the two transfers apart.
            assertEquals(2, ids.size)
        }

    @Test
    fun `abort is sent as its own command`() =
        runTest {
            val transport = FakeTransport()
            transport.enqueue(applyEvent(accepted = true))
            val client = client(transport)
            client.connect()

            client.abortSync()

            assertEquals(listOf(DaemonProtocol.CMD_SNAPSHOT_ABORT), transport.writtenTypes())
        }

    @Test
    fun `apply error codes match the native contract`() {
        // These integers cross the socket, so they cannot drift from the driver.
        assertEquals(0, DaemonApplyError.NONE.code)
        assertEquals(9, DaemonApplyError.DEVICE_MISMATCH.code)
        assertEquals(10, DaemonApplyError.STALE_GENERATION.code)
        assertEquals(11, DaemonApplyError.GRAPH_PREPARE_FAILED.code)
        assertEquals(12, DaemonApplyError.ABORTED.code)
        assertEquals(DaemonApplyError.CRC_MISMATCH, DaemonApplyError.fromCode(7))
        // An unknown code must not be silently treated as success.
        assertEquals(DaemonApplyError.UNKNOWN, DaemonApplyError.fromCode(999))
    }

    @Test
    fun `driver event decoder rejects a wrong sized payload`() {
        val failed =
            try {
                DaemonDriverEvent.decode(ByteArray(DaemonDriverEvent.WIRE_SIZE - 1))
                false
            } catch (e: DaemonSnapshotCodec.CodecException) {
                true
            }
        assertTrue("a short driver event must be refused", failed)
    }
}

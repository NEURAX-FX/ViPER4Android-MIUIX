package com.llsl.viper4android.daemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Contract tests for the `@viper4android.app.v1` payload codecs.
 *
 * Every rejection asserted here is one the native decoder in
 * `protocol/AppCommand.cpp` also performs. A field the App accepts but the root
 * daemon refuses (or the reverse) would desync the on-disk route cache from what
 * the App believes it reported.
 */
class AppProtocolTest {
    private val hash = "d89b5389890f9f82672db213bc7a1d23ba64e8066986484c66ab46c4364391d8"

    private fun report() =
        AppProtocol.AppRouteReport(
            routeType = "bluetooth_a2dp",
            stableAddressOrPort = "ac:12:2f:00:11:22",
            productName = "WH-1000XM4",
            encoding = "pcm_16",
            sampleRate = 48000,
            channelMask = 3,
            outputFlags = 6,
        )

    private fun corrupt(
        bytes: ByteArray,
        offset: Int,
        vararg replacement: Int,
    ): ByteArray {
        val copy = bytes.copyOf()
        for (index in replacement.indices) {
            copy[offset + index] = replacement[index].toByte()
        }
        return copy
    }

    @Test
    fun `app hello round trips`() {
        val hello = AppProtocol.AppHello(appGeneration = 0x0102030405060708L)
        val encoded = AppProtocol.encodeAppHello(hello)
        assertEquals(AppProtocol.APP_HELLO_WIRE_SIZE, encoded.size)
        assertEquals(hello, AppProtocol.decodeAppHello(encoded))
    }

    @Test
    fun `app hello ack round trips with a hash`() {
        val ack =
            AppProtocol.AppHelloAck(
                flags = AppProtocol.FLAG_RESTORE_ENABLED or AppProtocol.FLAG_ROUTE_KNOWN,
                daemonGeneration = 7,
                routeEpoch = 9,
                routeKeyHash = hash,
            )
        val encoded = AppProtocol.encodeAppHelloAck(ack)
        assertEquals(AppProtocol.APP_HELLO_ACK_WIRE_SIZE, encoded.size)
        assertEquals(ack, AppProtocol.decodeAppHelloAck(encoded))
    }

    @Test
    fun `app route report round trips`() {
        val encoded = AppProtocol.encodeAppRouteReport(report())
        val expectedSize =
            AppProtocol.APP_ROUTE_REPORT_HEADER_SIZE + "bluetooth_a2dp".length +
                "ac:12:2f:00:11:22".length + "WH-1000XM4".length + "pcm_16".length
        assertEquals(expectedSize, encoded.size)
        assertEquals(report(), AppProtocol.decodeAppRouteReport(encoded))
    }

    @Test
    fun `app route ack round trips`() {
        val ack =
            AppProtocol.AppRouteAck(
                accepted = true,
                daemonGeneration = 7,
                routeEpoch = 10,
                routeKeyHash = hash,
            )
        val encoded = AppProtocol.encodeAppRouteAck(ack)
        assertEquals(AppProtocol.APP_ROUTE_ACK_WIRE_SIZE, encoded.size)
        assertEquals(ack, AppProtocol.decodeAppRouteAck(encoded))
    }

    @Test
    fun `app apply result round trips`() {
        val result =
            AppProtocol.AppApplyResult(
                accepted = false,
                errorCode = DaemonApplyError.STALE_GENERATION.code,
                appGeneration = 5,
                daemonGeneration = 7,
                resourceGeneration = 11,
                graphGeneration = 12,
            )
        val encoded = AppProtocol.encodeAppApplyResult(result)
        assertEquals(AppProtocol.APP_APPLY_RESULT_WIRE_SIZE, encoded.size)
        assertEquals(result, AppProtocol.decodeAppApplyResult(encoded))
    }

    @Test
    fun `an empty route hash stays empty across the wire`() {
        // "No route yet" must not decode as a 64-character hash of NUL bytes:
        // the App would otherwise cache an unusable key as if it were valid.
        val ack =
            AppProtocol.AppHelloAck(
                flags = 0,
                daemonGeneration = 3,
                routeEpoch = 0,
                routeKeyHash = "",
            )
        val encoded = AppProtocol.encodeAppHelloAck(ack)
        assertEquals(AppProtocol.APP_HELLO_ACK_WIRE_SIZE, encoded.size)
        val hashField = encoded.copyOfRange(32, 96)
        assertTrue(hashField.all { it == 0.toByte() })
        assertEquals("", AppProtocol.decodeAppHelloAck(encoded).routeKeyHash)

        val routeAck =
            AppProtocol.AppRouteAck(
                accepted = false,
                daemonGeneration = 3,
                routeEpoch = 0,
                routeKeyHash = "",
            )
        assertEquals(
            "",
            AppProtocol.decodeAppRouteAck(AppProtocol.encodeAppRouteAck(routeAck)).routeKeyHash,
        )
    }

    @Test
    fun `hello ack flag bits decode independently`() {
        fun decodeFlags(flags: Int): AppProtocol.AppHelloAck =
            AppProtocol.decodeAppHelloAck(
                AppProtocol.encodeAppHelloAck(
                    AppProtocol.AppHelloAck(
                        flags = flags,
                        daemonGeneration = 1,
                        routeEpoch = 1,
                        routeKeyHash = hash,
                    ),
                ),
            )

        val none = decodeFlags(0)
        assertFalse(none.restoreEnabled)
        assertFalse(none.driverConnected)
        assertFalse(none.routeKnown)

        val restore = decodeFlags(AppProtocol.FLAG_RESTORE_ENABLED)
        assertTrue(restore.restoreEnabled)
        assertFalse(restore.driverConnected)
        assertFalse(restore.routeKnown)

        val driver = decodeFlags(AppProtocol.FLAG_DRIVER_CONNECTED)
        assertFalse(driver.restoreEnabled)
        assertTrue(driver.driverConnected)
        assertFalse(driver.routeKnown)

        val route = decodeFlags(AppProtocol.FLAG_ROUTE_KNOWN)
        assertFalse(route.restoreEnabled)
        assertFalse(route.driverConnected)
        assertTrue(route.routeKnown)

        val all =
            decodeFlags(
                AppProtocol.FLAG_RESTORE_ENABLED or AppProtocol.FLAG_DRIVER_CONNECTED or
                    AppProtocol.FLAG_ROUTE_KNOWN,
            )
        assertTrue(all.restoreEnabled)
        assertTrue(all.driverConnected)
        assertTrue(all.routeKnown)
    }

    @Test
    fun `message type range covers exactly the app control messages`() {
        assertFalse(AppProtocol.isAppMessageType(DaemonProtocol.CMD_SNAPSHOT_ABORT))
        assertFalse(AppProtocol.isAppMessageType(AppProtocol.MSG_APP_HELLO - 1))
        assertTrue(AppProtocol.isAppMessageType(AppProtocol.MSG_APP_HELLO))
        assertTrue(AppProtocol.isAppMessageType(AppProtocol.MSG_APP_ROUTE_REPORT))
        assertTrue(AppProtocol.isAppMessageType(AppProtocol.MSG_APP_APPLY_RESULT))
        assertFalse(AppProtocol.isAppMessageType(AppProtocol.MSG_APP_APPLY_RESULT + 1))
    }

    @Test
    fun `decoders reject a wrong payload size`() {
        val hello = AppProtocol.encodeAppHello(AppProtocol.AppHello(appGeneration = 1))
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppHello(hello.copyOf(hello.size - 1))
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppHelloAck(ByteArray(AppProtocol.APP_HELLO_ACK_WIRE_SIZE + 1))
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteAck(ByteArray(AppProtocol.APP_ROUTE_ACK_WIRE_SIZE - 1))
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppApplyResult(ByteArray(AppProtocol.APP_APPLY_RESULT_WIRE_SIZE + 8))
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteReport(
                ByteArray(AppProtocol.APP_ROUTE_REPORT_HEADER_SIZE - 1),
            )
        }
    }

    @Test
    fun `decoders reject a wrong protocol version`() {
        val hello = AppProtocol.encodeAppHello(AppProtocol.AppHello(appGeneration = 1))
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppHello(corrupt(hello, 0, 2, 0))
        }
        val ack =
            AppProtocol.encodeAppHelloAck(
                AppProtocol.AppHelloAck(
                    flags = 0,
                    daemonGeneration = 1,
                    routeEpoch = 1,
                    routeKeyHash = hash,
                ),
            )
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppHelloAck(corrupt(ack, 0, 0, 0))
        }
        val encodedReport = AppProtocol.encodeAppRouteReport(report())
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteReport(corrupt(encodedReport, 0, 9, 0))
        }
    }

    @Test
    fun `encoders reject a wrong protocol version`() {
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.encodeAppHello(AppProtocol.AppHello(appGeneration = 1, version = 2))
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.encodeAppRouteReport(report().copy(version = 2))
        }
    }

    @Test
    fun `decoders reject non-zero reserved fields`() {
        // hello: reserved u16 at 2, reserved u64 at 12, reserved u32 at 20.
        val hello = AppProtocol.encodeAppHello(AppProtocol.AppHello(appGeneration = 1))
        for (offset in listOf(2, 12, 20)) {
            assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
                AppProtocol.decodeAppHello(corrupt(hello, offset, 1))
            }
        }

        // hello ack: reserved u64 at 20, reserved u32 at 28.
        val helloAck =
            AppProtocol.encodeAppHelloAck(
                AppProtocol.AppHelloAck(
                    flags = 0,
                    daemonGeneration = 1,
                    routeEpoch = 1,
                    routeKeyHash = hash,
                ),
            )
        for (offset in listOf(20, 28)) {
            assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
                AppProtocol.decodeAppHelloAck(corrupt(helloAck, offset, 1))
            }
        }

        // route ack: reserved u16 at 2, reserved u64 at 20, reserved u32 at 28.
        val routeAck =
            AppProtocol.encodeAppRouteAck(
                AppProtocol.AppRouteAck(
                    accepted = true,
                    daemonGeneration = 1,
                    routeEpoch = 1,
                    routeKeyHash = hash,
                ),
            )
        for (offset in listOf(2, 20, 28)) {
            assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
                AppProtocol.decodeAppRouteAck(corrupt(routeAck, offset, 1))
            }
        }

        // apply result: reserved u16 at 2, reserved u64 at 40.
        val applyResult =
            AppProtocol.encodeAppApplyResult(
                AppProtocol.AppApplyResult(
                    accepted = true,
                    errorCode = 0,
                    appGeneration = 1,
                    daemonGeneration = 1,
                    resourceGeneration = 1,
                    graphGeneration = 1,
                ),
            )
        for (offset in listOf(2, 40)) {
            assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
                AppProtocol.decodeAppApplyResult(corrupt(applyResult, offset, 1))
            }
        }

        // route report: reserved u16 at 2.
        val encodedReport = AppProtocol.encodeAppRouteReport(report())
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteReport(corrupt(encodedReport, 2, 1))
        }
    }

    @Test
    fun `route fields must not be empty`() {
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.encodeAppRouteReport(report().copy(routeType = ""))
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.encodeAppRouteReport(report().copy(encoding = ""))
        }
        // A wire report whose lengths are consistent but zero must still be refused.
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteReport(routeReportBytes(0, 0, 0, 0, ""))
        }
    }

    @Test
    fun `route fields must not exceed the bound`() {
        val tooLong = "a".repeat(AppProtocol.MAX_APP_ROUTE_FIELD_BYTES + 1)
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.encodeAppRouteReport(report().copy(productName = tooLong))
        }
        // A declared length above the bound must be refused before any allocation.
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteReport(
                routeReportBytes(AppProtocol.MAX_APP_ROUTE_FIELD_BYTES + 1, 1, 1, 1, ""),
            )
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteReport(routeReportBytes(-1, 1, 1, 1, ""))
        }
    }

    @Test
    fun `route fields must not carry the key delimiter`() {
        // '|' joins device-key fields, so a field containing it could forge a key.
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.encodeAppRouteReport(report().copy(routeType = "speaker|builtin"))
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteReport(routeReportBytes(2, 1, 1, 1, "a|bcd"))
        }
    }

    @Test
    fun `route fields must not carry control characters`() {
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.encodeAppRouteReport(report().copy(productName = "WH\u0001XM4"))
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.encodeAppRouteReport(report().copy(productName = "WH\u007FXM4"))
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteReport(routeReportBytes(2, 1, 1, 1, "a\u0000bcd"))
        }
    }

    @Test
    fun `route format fields must be non-zero`() {
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.encodeAppRouteReport(report().copy(sampleRate = 0))
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.encodeAppRouteReport(report().copy(channelMask = 0))
        }
        val encoded = AppProtocol.encodeAppRouteReport(report())
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteReport(corrupt(encoded, 4, 0, 0, 0, 0))
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteReport(corrupt(encoded, 8, 0, 0, 0, 0))
        }
    }

    @Test
    fun `route report length must match the declared fields`() {
        val encoded = AppProtocol.encodeAppRouteReport(report())
        // Shrink one declared length without shrinking the payload.
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteReport(corrupt(encoded, 16, 13, 0, 0, 0))
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppRouteReport(encoded.copyOf(encoded.size - 1))
        }
    }

    @Test
    fun `a route hash must be empty or 64 lowercase hex characters`() {
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.encodeAppHelloAck(
                AppProtocol.AppHelloAck(
                    flags = 0,
                    daemonGeneration = 1,
                    routeEpoch = 1,
                    routeKeyHash = hash.uppercase(),
                ),
            )
        }
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.encodeAppRouteAck(
                AppProtocol.AppRouteAck(
                    accepted = true,
                    daemonGeneration = 1,
                    routeEpoch = 1,
                    routeKeyHash = hash.dropLast(1),
                ),
            )
        }

        val encoded =
            AppProtocol.encodeAppHelloAck(
                AppProtocol.AppHelloAck(
                    flags = 0,
                    daemonGeneration = 1,
                    routeEpoch = 1,
                    routeKeyHash = hash,
                ),
            )
        // A non-hex byte inside the fixed hash field.
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppHelloAck(corrupt(encoded, 40, 'Z'.code))
        }
        // A short-but-non-empty hash: NUL terminated before 64 characters.
        assertThrows(DaemonSnapshotCodec.CodecException::class.java) {
            AppProtocol.decodeAppHelloAck(corrupt(encoded, 90, 0))
        }
    }

    @Test
    fun `app payloads travel inside daemon frames`() {
        val payload = AppProtocol.encodeAppRouteReport(report())
        val frame =
            DaemonProtocol.encodeFrame(
                DaemonProtocol.FrameHeader(
                    messageType = AppProtocol.MSG_APP_ROUTE_REPORT,
                    requestId = 42,
                ),
                payload,
            )
        val decoded = DaemonProtocol.decodeFrame(frame)
        assertEquals(AppProtocol.MSG_APP_ROUTE_REPORT, decoded.header.messageType)
        assertEquals(42L, decoded.header.requestId)
        assertTrue(AppProtocol.isAppMessageType(decoded.header.messageType))
        assertEquals(report(), AppProtocol.decodeAppRouteReport(decoded.payload))
    }

    /** Builds a route report with arbitrary declared lengths, bypassing the encoder. */
    private fun routeReportBytes(
        routeTypeSize: Int,
        addressSize: Int,
        productSize: Int,
        encodingSize: Int,
        body: String,
    ): ByteArray {
        val bodyBytes = body.toByteArray(Charsets.ISO_8859_1)
        val buffer =
            ByteBuffer
                .allocate(AppProtocol.APP_ROUTE_REPORT_HEADER_SIZE + bodyBytes.size)
                .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(AppProtocol.PROTOCOL_VERSION.toShort())
        buffer.putShort(0)
        buffer.putInt(48000)
        buffer.putInt(3)
        buffer.putInt(0)
        buffer.putInt(routeTypeSize)
        buffer.putInt(addressSize)
        buffer.putInt(productSize)
        buffer.putInt(encodingSize)
        buffer.put(bodyBytes)
        return buffer.array()
    }
}

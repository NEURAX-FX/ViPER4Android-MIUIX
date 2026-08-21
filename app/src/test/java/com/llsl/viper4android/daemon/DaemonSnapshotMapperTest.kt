package com.llsl.viper4android.daemon

import com.llsl.viper4android.effect.BassState
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.EqState
import com.llsl.viper4android.effect.FetCompressorState
import com.llsl.viper4android.effect.MultibandCompressorState
import com.llsl.viper4android.effect.OutputState
import com.llsl.viper4android.effect.ParamRaw
import com.llsl.viper4android.viper.ViperDispatcher
import com.llsl.viper4android.viper.ViperParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DaemonSnapshotMapperTest {
    private val identity =
        DaemonProtocol.DeviceIdentity(
            routeType = "speaker",
            stableAddressOrPort = "builtin",
            productName = "internal",
            sampleRate = 48000,
            channelMask = 3,
            encoding = "pcm_16",
        )

    private fun inputs(
        state: EffectState,
        appGeneration: Long = 5,
        daemonGeneration: Long = 7,
        identity: DaemonProtocol.DeviceIdentity = this.identity,
    ) = DaemonSnapshotMapper.SnapshotInputs(
        state = state,
        identity = identity,
        bootId = 0x1122334455667788L,
        appGeneration = appGeneration,
        daemonGeneration = daemonGeneration,
        createdAtMillis = 1700000000000L,
    )

    private fun recordsOf(state: EffectState) = DaemonSnapshotMapper.viperRecords(state)

    private fun valueOf(
        state: EffectState,
        param: Int,
    ): Int? = recordsOf(state).lastOrNull { it.param == param }?.val1

    @Test
    fun `mapper records the same writes the legacy backend performs`() {
        val state = EffectState(masterEnable = true, out = OutputState(volume = 80, channelPan = -5, limiter = 90))

        // The mapper must not reimplement the mapping: driving the dispatcher with a
        // second recording sink has to produce the same writes. The mapper only
        // partitions that one pass into the snapshot's two parameter streams, so the
        // two streams concatenated must equal the full dispatch, in order.
        val reference = DaemonSnapshotMapper.RecordingSink()
        ViperDispatcher.dispatchState(reference, state)
        val split = DaemonSnapshotMapper.splitRecords(state)
        assertEquals(reference.recorded.toSet(), (split.viper + split.iem).toSet())
        assertEquals(reference.recorded.size, split.viper.size + split.iem.size)
        // Relative order inside each stream is preserved.
        assertEquals(reference.recorded.filter { it in split.viper }, split.viper)
        assertEquals(reference.recorded.filter { it in split.iem }, split.iem)
        assertTrue(split.viper.isNotEmpty())
        assertTrue(split.iem.isNotEmpty())
    }

    @Test
    fun `scalar parameters carry the dispatcher's converted values`() {
        val state =
            EffectState(
                masterEnable = true,
                out = OutputState(volume = 80, channelPan = -5, limiter = 90),
                bass = BassState(enable = true, mode = 1, frequency = 60, gain = 7, antiPop = true),
            )

        assertEquals(80, valueOf(state, ViperParams.PARAM_MASTER_LIMITER_OUTPUT_VOLUME))
        assertEquals(-5, valueOf(state, ViperParams.PARAM_MASTER_LIMITER_CHANNEL_PAN))
        assertEquals(90, valueOf(state, ViperParams.PARAM_MASTER_LIMITER_THRESHOLD))
        assertEquals(1, valueOf(state, ViperParams.PARAM_BASS_ENABLE))
        // Bass frequency goes through ParamRaw, so a raw 60 would be wrong.
        assertEquals(75, valueOf(state, ViperParams.PARAM_BASS_FREQUENCY))
    }

    @Test
    fun `disabled effects still emit their enable parameter`() {
        val enabled = EffectState(masterEnable = true, bass = BassState(enable = true, frequency = 60))
        val disabled = EffectState(masterEnable = true, bass = BassState(enable = false, frequency = 60))

        assertEquals(1, valueOf(enabled, ViperParams.PARAM_BASS_ENABLE))
        // A disabled effect must be explicitly turned off, not simply omitted:
        // otherwise a restored snapshot could leave a stale effect running.
        assertEquals(0, valueOf(disabled, ViperParams.PARAM_BASS_ENABLE))
    }

    @Test
    fun `indexed parameters keep their band index`() {
        val state =
            EffectState(
                masterEnable = true,
                multibandCompressor =
                    MultibandCompressorState(
                        enable = true,
                        thresholds = listOf(-10, -11, -12, -13, -14),
                    ),
            )
        val thresholds =
            recordsOf(state).filter { it.param == ViperParams.PARAM_MULTIBAND_COMPRESSOR_BAND_THRESHOLD }

        assertEquals(5, thresholds.size)
        // val1 is the band index, val2 the value, converted by ParamRaw exactly as
        // the legacy AudioEffect path converts it.
        assertEquals(listOf(0, 1, 2, 3, 4), thresholds.map { it.val1 })
        assertEquals(
            listOf(-10, -11, -12, -13, -14).map { ParamRaw.fetCompressorThreshold(it) },
            thresholds.map { it.val2 },
        )
    }

    @Test
    fun `equalizer band levels are translated to the snapshot array layout`() {
        val bands = listOf(1.0, -2.5, 3.25, 0.0, 4.5, -1.0, 2.0, 0.5, -3.0, 6.0)
        val state = EffectState(masterEnable = true, eq = EqState(enable = true, bandCount = 10, bands = bands))

        val record =
            recordsOf(state).single { it.param == ViperParams.PARAM_EQUALIZER_BAND_LEVELS }

        // The legacy layout is a 256-byte buffer with a count prefix; the snapshot
        // carries exactly arrSize floats, which is what the driver reads.
        assertEquals(bands.size, record.arrSize)
        assertEquals(bands.size * 4, record.payload.size)
        val buffer = ByteBuffer.wrap(record.payload).order(ByteOrder.LITTLE_ENDIAN)
        for (expected in bands) {
            assertEquals(expected.toFloat(), buffer.float, 0.0f)
        }
        // Validation would reject a mismatched arrSize/payload pair.
        DaemonSnapshotCodec.validateRawParamRecord(record)
    }

    @Test
    fun `every recorded parameter survives the wire codec`() {
        val state =
            EffectState(
                masterEnable = true,
                out = OutputState(volume = 77),
                bass = BassState(enable = true, frequency = 45, gain = 3),
                eq = EqState(enable = true, bandCount = 10, bands = List(10) { it * 0.5 }),
                fetCompressor = FetCompressorState(enable = true, threshold = -12, ratio = 40),
                multibandCompressor = MultibandCompressorState(enable = true),
            )
        val records = recordsOf(state)
        val encoded = DaemonSnapshotCodec.encodeParameterStream(records)

        assertEquals(records, DaemonSnapshotCodec.decodeParameterStream(encoded))
    }

    @Test
    fun `iem records are captured separately from viper records`() {
        val state = EffectState(masterEnable = true)
        val iem = DaemonSnapshotMapper.iemRecords(state)
        val viper = DaemonSnapshotMapper.viperRecords(state)

        assertTrue(iem.isNotEmpty())
        // IEM parameters live in their own id range and their own snapshot field.
        assertTrue(iem.all { it.param >= ViperParams.PARAM_IEM_ENABLE })
        assertTrue(viper.none { it.param >= ViperParams.PARAM_IEM_ENABLE })
    }

    @Test
    fun `snapshot carries route identity master flag and both parameter streams`() {
        val state = EffectState(masterEnable = true, bass = BassState(enable = true, frequency = 60))
        val snapshot = DaemonSnapshotMapper.buildSnapshot(inputs(state))

        assertEquals("speaker|builtin|internal|48000|3|pcm_16|0", snapshot.deviceKey)
        assertEquals(DaemonProtocol.hashDeviceKey(snapshot.deviceKey), snapshot.deviceKeyHash)
        assertEquals(5L, snapshot.appGeneration)
        assertEquals(7L, snapshot.daemonGeneration)
        assertTrue(snapshot.masterEnabled)
        assertEquals(
            DaemonSnapshotMapper.viperRecords(state),
            DaemonSnapshotCodec.decodeParameterStream(snapshot.parameters),
        )
        assertEquals(
            DaemonSnapshotMapper.iemRecords(state),
            DaemonSnapshotCodec.decodeParameterStream(snapshot.iemParameters),
        )
        // A built snapshot must already be encodable; the daemon gets bytes, not objects.
        assertTrue(DaemonSnapshotCodec.encodeSnapshot(snapshot).isNotEmpty())
    }

    @Test
    fun `master disabled state is preserved in the snapshot`() {
        val snapshot = DaemonSnapshotMapper.buildSnapshot(inputs(EffectState(masterEnable = false)))
        // Restoring this snapshot must leave the effect off, not silently on.
        assertEquals(false, snapshot.masterEnabled)
    }

    @Test
    fun `different routes produce different snapshot keys`() {
        val state = EffectState(masterEnable = true)
        val speaker = DaemonSnapshotMapper.buildSnapshot(inputs(state))
        val bluetooth =
            DaemonSnapshotMapper.buildSnapshot(
                inputs(
                    state,
                    identity =
                        identity.copy(
                            routeType = "bluetooth_a2dp",
                            stableAddressOrPort = "AA:BB:CC:DD:EE:FF",
                            productName = "Buds",
                            sampleRate = 44100,
                        ),
                ),
            )

        // Cross-device inheritance is the failure this key exists to prevent.
        assertNotEquals(speaker.deviceKey, bluetooth.deviceKey)
        assertNotEquals(speaker.deviceKeyHash, bluetooth.deviceKeyHash)
    }

    @Test
    fun `unusable route identity is refused instead of producing a wrong key`() {
        val failure =
            try {
                DaemonSnapshotMapper.buildSnapshot(
                    inputs(EffectState(masterEnable = true), identity = identity.copy(sampleRate = 0)),
                )
                null
            } catch (e: DaemonSnapshotCodec.CodecException) {
                e
            }
        assertTrue("expected a codec exception", failure != null)
    }

    @Test
    fun `zero generations are refused`() {
        val state = EffectState(masterEnable = true)
        for (bad in listOf(inputs(state, appGeneration = 0), inputs(state, daemonGeneration = 0))) {
            val failed =
                try {
                    DaemonSnapshotMapper.buildSnapshot(bad)
                    false
                } catch (e: DaemonSnapshotCodec.CodecException) {
                    true
                }
            assertTrue("zero generation must be refused", failed)
        }
    }

    @Test
    fun `snapshot encoding is deterministic for the same state`() {
        val state = EffectState(masterEnable = true, eq = EqState(enable = true, bandCount = 10, bands = List(10) { 1.5 }))
        val first = DaemonSnapshotCodec.encodeSnapshot(DaemonSnapshotMapper.buildSnapshot(inputs(state)))
        val second = DaemonSnapshotCodec.encodeSnapshot(DaemonSnapshotMapper.buildSnapshot(inputs(state)))

        // Deterministic bytes let the daemon dedupe and the App detect real changes.
        assertEquals(first.toList(), second.toList())
    }

    @Test
    fun `state changes change the encoded snapshot`() {
        val quiet = EffectState(masterEnable = true, out = OutputState(volume = 50))
        val loud = EffectState(masterEnable = true, out = OutputState(volume = 90))

        val quietBytes = DaemonSnapshotCodec.encodeSnapshot(DaemonSnapshotMapper.buildSnapshot(inputs(quiet)))
        val loudBytes = DaemonSnapshotCodec.encodeSnapshot(DaemonSnapshotMapper.buildSnapshot(inputs(loud)))
        assertNotEquals(quietBytes.toList(), loudBytes.toList())
    }
}

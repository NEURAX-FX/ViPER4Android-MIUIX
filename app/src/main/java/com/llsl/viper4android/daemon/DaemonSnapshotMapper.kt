package com.llsl.viper4android.daemon

import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.viper.ParamSink
import com.llsl.viper4android.viper.ViperDispatcher
import com.llsl.viper4android.viper.ViperParams
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts an `EffectState` into a daemon snapshot.
 *
 * The parameter mapping is not reimplemented here: `ViperDispatcher` writes into a
 * [ParamSink], so this records exactly the same writes the legacy
 * `AudioEffect` backend performs. Any mapping fix therefore reaches both backends.
 */
object DaemonSnapshotMapper {
    /** Captures raw parameter writes instead of sending them to a driver. */
    class RecordingSink : ParamSink {
        private val records = ArrayList<DaemonSnapshotCodec.RawParamRecord>()

        val recorded: List<DaemonSnapshotCodec.RawParamRecord> get() = records

        override fun setParameter(
            param: Int,
            value: Int,
        ) {
            records.add(DaemonSnapshotCodec.RawParamRecord(param = param, val1 = value))
        }

        override fun setParameter(
            param: Int,
            val1: Int,
            val2: Int,
        ) {
            records.add(DaemonSnapshotCodec.RawParamRecord(param = param, val1 = val1, val2 = val2))
        }

        override fun setParameter(
            param: Int,
            val1: Int,
            val2: Int,
            val3: Int,
        ) {
            records.add(
                DaemonSnapshotCodec.RawParamRecord(param = param, val1 = val1, val2 = val2, val3 = val3),
            )
        }

        override fun setParameter(
            param: Int,
            value: ByteArray,
        ) {
            records.add(translateArrayWrite(param, value))
        }
    }

    /**
     * Array writes use a parameter-specific layout on the `AudioEffect` path. The
     * snapshot wire format instead carries an explicit `arrSize` plus exactly the
     * bytes the driver will read, so each array parameter is translated here.
     */
    private fun translateArrayWrite(
        param: Int,
        value: ByteArray,
    ): DaemonSnapshotCodec.RawParamRecord =
        when (param) {
            ViperParams.PARAM_EQUALIZER_BAND_LEVELS -> {
                // Legacy layout: int count followed by `count` floats in a 256-byte
                // buffer. The driver reads arrSize floats, so drop the count prefix
                // and the trailing padding.
                val buffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)
                val count = buffer.int
                require(count >= 0 && count * 4 <= value.size - 4) {
                    "equalizer band level count $count does not fit in ${value.size} bytes"
                }
                val payload = ByteArray(count * 4)
                buffer.get(payload)
                DaemonSnapshotCodec.RawParamRecord(
                    param = param,
                    arrSize = count,
                    payload = payload,
                )
            }
            else -> throw DaemonSnapshotCodec.CodecException(
                "no snapshot translation for array parameter $param",
            )
        }

    data class SnapshotInputs(
        val state: EffectState,
        val identity: DaemonProtocol.DeviceIdentity,
        val bootId: Long,
        val appGeneration: Long,
        val daemonGeneration: Long,
        val createdAtMillis: Long,
        val globalMode: Boolean = false,
        val resources: List<DaemonSnapshotCodec.ResourceReference> = emptyList(),
    )

    /**
     * Splits one dispatcher pass into the two snapshot parameter streams.
     *
     * `ViperDispatcher.dispatchState` already emits the IEM writes as part of the
     * full state, so dispatching twice would encode them into both streams and the
     * driver would replay them twice. Partition a single pass instead.
     */
    data class RecordSplit(
        val viper: List<DaemonSnapshotCodec.RawParamRecord>,
        val iem: List<DaemonSnapshotCodec.RawParamRecord>,
    )

    fun splitRecords(state: EffectState): RecordSplit {
        val sink = RecordingSink()
        ViperDispatcher.dispatchState(sink, state)
        val (iem, viper) = sink.recorded.partition { it.param >= ViperParams.PARAM_IEM_ENABLE }
        return RecordSplit(viper = viper, iem = iem)
    }

    /** Raw ViPER parameter records for `state`, in dispatcher order. */
    fun viperRecords(state: EffectState): List<DaemonSnapshotCodec.RawParamRecord> = splitRecords(state).viper

    /** Raw IEM parameter records for `state`, in dispatcher order. */
    fun iemRecords(state: EffectState): List<DaemonSnapshotCodec.RawParamRecord> = splitRecords(state).iem

    /**
     * Builds a validated snapshot. Throws [DaemonSnapshotCodec.CodecException] when
     * the inputs cannot produce one, rather than emitting a snapshot the daemon
     * would reject or, worse, mis-attribute to another device.
     */
    fun buildSnapshot(inputs: SnapshotInputs): DaemonSnapshotCodec.Snapshot {
        val deviceKey =
            DaemonProtocol.normalizeDeviceKey(inputs.identity)
                ?: throw DaemonSnapshotCodec.CodecException("route identity is not usable as a device key")

        val split = splitRecords(inputs.state)
        val snapshot =
            DaemonSnapshotCodec.Snapshot(
                deviceKey = deviceKey,
                deviceKeyHash = DaemonProtocol.hashDeviceKey(deviceKey),
                bootId = inputs.bootId,
                daemonGeneration = inputs.daemonGeneration,
                appGeneration = inputs.appGeneration,
                createdAtMillis = inputs.createdAtMillis,
                masterEnabled = inputs.state.masterEnable,
                globalMode = inputs.globalMode,
                parameters = DaemonSnapshotCodec.encodeParameterStream(split.viper),
                iemParameters = DaemonSnapshotCodec.encodeParameterStream(split.iem),
                resources = inputs.resources,
            )
        // Fail here rather than at the daemon: a rejected snapshot on the socket is
        // far harder to attribute than a rejected build.
        DaemonSnapshotCodec.validateSnapshot(snapshot)
        return snapshot
    }
}

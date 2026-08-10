package com.llsl.viper4android.viper

import com.llsl.viper4android.effect.ConvolverState
import com.llsl.viper4android.effect.DdcState
import com.llsl.viper4android.effect.EffectState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvolverParamsSerializerTest {
    @Test
    fun writesExpandedConvolverAndKeepsFollowingStructAligned() {
        val state =
            EffectState(
                convolver =
                    ConvolverState(
                        enable = true,
                        kernelFile = "matrix.wav",
                        crossChannel = 35,
                        wet = 65,
                        outputGain = -35,
                        routing = 2,
                        crossDelay100Ns = 3125,
                    ),
                ddc = DdcState(enable = true, device = "device.vdc"),
            )
        val buffer =
            ByteBuffer.allocate(ViperParamsLayout.SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)

        ViperParamsSerializer.write(buffer, 0, state)

        val base = ViperParamsLayout.CONVOLVER
        val layout = ViperParamsLayout.Convolver
        assertEquals(1160, ViperParamsLayout.SIZE)
        assertEquals(20, ViperParamsLayout.Bass.SIZE)
        assertEquals(24, layout.SIZE)
        assertEquals(1, buffer.get(base + layout.ENABLE).toInt())
        assertEquals(0.35f, buffer.getFloat(base + layout.CROSS_CHANNEL), 0f)
        assertEquals(0.65f, buffer.getFloat(base + layout.WET), 0f)
        assertEquals(-3.5f, buffer.getFloat(base + layout.OUTPUT_GAIN_DB), 0f)
        assertEquals(2, buffer.getInt(base + layout.ROUTING))
        assertEquals(0.3125f, buffer.getFloat(base + layout.CROSS_DELAY_MS), 0f)
        assertEquals(340, ViperParamsLayout.DDC)
        assertEquals(1, buffer.get(ViperParamsLayout.DDC).toInt())
    }
}

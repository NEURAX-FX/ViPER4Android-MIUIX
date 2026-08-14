package com.llsl.viper4android.viper

import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.IemGeneralState
import com.llsl.viper4android.effect.IemMultiState
import com.llsl.viper4android.effect.IemState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IemDispatchTest {
    @Test
    fun fullStateDisablesThenPublishesAllPersistentFieldsAndEnablesLast() {
        val state =
            EffectState(
                iem =
                    IemState(
                        general = IemGeneralState(enable = true, encoderMode = 3, order = 2, renderMode = 1),
                        multi = IemMultiState(
                            azimuthCentidegrees = listOf(-1000, 2000),
                            elevationCentidegrees = listOf(300, -400),
                            gainDecidb = listOf(-50, 60),
                            mute = listOf(false, true),
                        ),
                        freeze = true,
                    ),
            )
        val writes = ViperDispatcher.iemWrites(state)

        assertEquals(ViperDispatcher.IemWrite.Scalar(ViperParams.PARAM_IEM_ENABLE, 0), writes.first())
        assertEquals(ViperDispatcher.IemWrite.Scalar(ViperParams.PARAM_IEM_ENABLE, 1), writes.last())
        assertEquals(72, writes.size)
        assertEquals(8, writes.filterIsInstance<ViperDispatcher.IemWrite.Indexed>().size)
        assertTrue(
            writes.contains(
                ViperDispatcher.IemWrite.Indexed(ViperParams.PARAM_IEM_MULTI_GAIN, 1, 60),
            ),
        )
        assertTrue(writes.contains(ViperDispatcher.IemWrite.Scalar(ViperParams.PARAM_IEM_RENDER_MODE, 1)))
        assertTrue(writes.contains(ViperDispatcher.IemWrite.Scalar(ViperParams.PARAM_IEM_HALO_SPACE, 800)))
        assertTrue(writes.contains(ViperDispatcher.IemWrite.Scalar(ViperParams.PARAM_IEM_HALO_LFE_ENABLE, 1)))
        assertTrue(writes.contains(ViperDispatcher.IemWrite.Scalar(ViperParams.PARAM_IEM_HALO_LFE_FREQUENCY, 750000)))
        assertTrue(writes.contains(ViperDispatcher.IemWrite.Scalar(ViperParams.PARAM_IEM_HALO_LFE_SPLIT, 0)))
        assertTrue(writes.contains(ViperDispatcher.IemWrite.Scalar(ViperParams.PARAM_IEM_HALO_LFE_GAIN, 272727)))
        assertFalse(
            writes.any {
                it is ViperDispatcher.IemWrite.Scalar &&
                    it.param == ViperParams.COMMAND_IEM_GRANULAR_FREEZE
            },
        )
    }

    @Test
    fun exactPhaseOneIdsRemainStable() {
        assertEquals(0x12000, ViperParams.PARAM_IEM_ENABLE)
        assertEquals(0x12045, ViperParams.PARAM_IEM_GRANULAR_SAMPLE_WISE)
        assertEquals(0x12057, ViperParams.PARAM_IEM_ROTATION_SEQUENCE)
        assertEquals(0x12060, ViperParams.PARAM_IEM_HEADPHONE_EQ)
        assertEquals(0x12008, ViperParams.PARAM_IEM_RENDER_MODE)
        assertEquals(0x12070, ViperParams.PARAM_IEM_HALO_DIALOG_ISOLATE)
        assertEquals(0x1207D, ViperParams.PARAM_IEM_HALO_REAR_SHELF_GAIN)
        assertEquals(0x1207E, ViperParams.PARAM_IEM_HALO_LFE_ENABLE)
        assertEquals(0x1207F, ViperParams.PARAM_IEM_HALO_LFE_FREQUENCY)
        assertEquals(0x12080, ViperParams.PARAM_IEM_HALO_LFE_SPLIT)
        assertEquals(0x12081, ViperParams.PARAM_IEM_HALO_LFE_GAIN)
        assertEquals(0x12102, ViperParams.COMMAND_IEM_GRANULAR_FREEZE)
    }
}

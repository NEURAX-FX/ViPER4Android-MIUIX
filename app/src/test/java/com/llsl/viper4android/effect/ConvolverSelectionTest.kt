package com.llsl.viper4android.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvolverSelectionTest {
    @Test
    fun keepsAnExistingKernelThatIsStillAvailable() {
        assertEquals(
            "room.wav",
            resolveConvolverKernel("room.wav", listOf("hall.wav", "room.wav")),
        )
    }

    @Test
    fun selectsTheFirstImportedKernelWhenSelectionIsEmptyOrMissing() {
        val available = listOf("first.wav", "second.wav")

        assertEquals("first.wav", resolveConvolverKernel("", available))
        assertEquals("first.wav", resolveConvolverKernel("deleted.wav", available))
    }

    @Test
    fun returnsNullWhenNoKernelExists() {
        assertNull(resolveConvolverKernel("", emptyList()))
    }
}

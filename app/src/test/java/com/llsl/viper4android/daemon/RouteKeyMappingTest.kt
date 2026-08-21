package com.llsl.viper4android.daemon

import android.media.AudioDeviceInfo
import com.llsl.viper4android.audio.AudioDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteKeyMappingTest {
    private fun keyFor(device: AudioDevice): String? =
        DaemonRouteMapper.identityFor(device)?.let { DaemonProtocol.normalizeDeviceKey(it) }

    @Test
    fun `speaker key matches the native adapter's canonical form`() {
        // AndroidRouteAdapter emits route_type=speaker, address=builtin,
        // product_name=speaker with the fixed format constants.
        assertEquals(
            "speaker|builtin|speaker|48000|3|pcm_16|0",
            keyFor(AudioDevice.DEFAULT_SPEAKER),
        )
    }

    @Test
    fun `wired key matches the native adapter's canonical form`() {
        val wired =
            AudioDevice(
                id = AudioDevice.ID_WIRED,
                name = "Wired Headphone",
                type = AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                isHeadphone = true,
            )
        assertEquals("wired_headset|h2w|wired_headset|48000|3|pcm_16|0", keyFor(wired))
    }

    @Test
    fun `format constants agree with the native protocol header`() {
        // A drift here makes every snapshot fail with DEVICE_MISMATCH.
        assertEquals(48000, DaemonRouteMapper.ROUTE_SAMPLE_RATE)
        assertEquals(3, DaemonRouteMapper.ROUTE_CHANNEL_MASK)
        assertEquals("pcm_16", DaemonRouteMapper.ROUTE_ENCODING)
    }

    @Test
    fun `bluetooth devices key on their mac address`() {
        val buds =
            AudioDevice(
                id = "AA:BB:CC:DD:EE:FF",
                name = "My Buds",
                type = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                isHeadphone = true,
            )
        assertEquals("bluetooth_a2dp|aa:bb:cc:dd:ee:ff|bluetooth_a2dp|48000|3|pcm_16|0", keyFor(buds))

        // A renamed device keeps its key: the name is not part of the identity.
        assertEquals(keyFor(buds), keyFor(buds.copy(name = "Renamed Buds")))
    }

    @Test
    fun `different bluetooth devices do not share a key`() {
        val first =
            AudioDevice("AA:BB:CC:DD:EE:FF", "Buds", AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, true)
        val second =
            AudioDevice("11:22:33:44:55:66", "Other", AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, true)
        // Cross-device inheritance is exactly what the key exists to prevent.
        assertNotEquals(keyFor(first), keyFor(second))
    }

    @Test
    fun `usb devices get their own route type`() {
        val usb =
            AudioDevice("usb_7", "USB Audio", AudioDeviceInfo.TYPE_USB_HEADSET, true)
        assertEquals("usb_headset|usb_7|usb_headset|48000|3|pcm_16|0", keyFor(usb))
        assertNotEquals(keyFor(usb), keyFor(AudioDevice.DEFAULT_SPEAKER))
    }

    @Test
    fun `every mapped route yields a distinct key`() {
        val devices =
            listOf(
                AudioDevice.DEFAULT_SPEAKER,
                AudioDevice(AudioDevice.ID_WIRED, "Wired", AudioDeviceInfo.TYPE_WIRED_HEADSET, true),
                AudioDevice("AA:BB:CC:DD:EE:FF", "Buds", AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, true),
                AudioDevice("usb_1", "USB", AudioDeviceInfo.TYPE_USB_HEADSET, true),
            )
        val keys = devices.mapNotNull { keyFor(it) }
        assertEquals(devices.size, keys.size)
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun `unmappable device yields no key instead of a wrong one`() {
        val unknown = AudioDevice("hdmi_3", "HDMI", AudioDeviceInfo.TYPE_HDMI, false)
        // Better to skip the daemon than to file HDMI settings under another route.
        assertNull(DaemonRouteMapper.identityFor(unknown))
        assertNull(DaemonRouteMapper.routeHashFor(unknown))
    }

    @Test
    fun `route hash is the sha256 of the normalized key`() {
        val expected = DaemonProtocol.hashDeviceKey(requireNotNull(keyFor(AudioDevice.DEFAULT_SPEAKER)))
        val actual = requireNotNull(DaemonRouteMapper.routeHashFor(AudioDevice.DEFAULT_SPEAKER))
        assertEquals(expected, actual)
        assertEquals(64, actual.length)
        assertTrue(actual.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `mapped identities are all valid`() {
        val devices =
            listOf(
                AudioDevice.DEFAULT_SPEAKER,
                AudioDevice(AudioDevice.ID_WIRED, "Wired", AudioDeviceInfo.TYPE_WIRED_HEADSET, true),
                AudioDevice("AA:BB:CC:DD:EE:FF", "Buds", AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, true),
            )
        for (device in devices) {
            val identity = requireNotNull(DaemonRouteMapper.identityFor(device))
            // An invalid identity would be rejected by the snapshot schema.
            assertTrue(DaemonProtocol.isValidDeviceIdentity(identity))
        }
    }
}

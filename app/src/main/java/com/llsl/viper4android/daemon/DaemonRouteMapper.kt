package com.llsl.viper4android.daemon

import android.media.AudioDeviceInfo
import com.llsl.viper4android.audio.AudioDevice

/**
 * Maps an [AudioDevice] to the canonical route identity the daemon keys snapshots by.
 *
 * The daemon derives the same identity independently in
 * `daemon/RouteWatcher.cpp` (`AndroidRouteAdapter`). Both sides must agree
 * exactly: a snapshot whose key does not match the daemon's current route is
 * rejected as `DEVICE_MISMATCH`, so the App's state would silently never restore.
 *
 * That is why the format fields are fixed constants rather than the live stream
 * format, and why the speaker/wired route names and addresses below are copied
 * from the native adapter verbatim.
 */
object DaemonRouteMapper {
    // Must equal kRouteIdentitySampleRate / kRouteIdentityChannelMask /
    // kRouteIdentityEncoding in protocol/DeviceKey.h.
    const val ROUTE_SAMPLE_RATE = 48000
    const val ROUTE_CHANNEL_MASK = 3
    const val ROUTE_ENCODING = "pcm_16"

    // Route names shared with AndroidRouteAdapter.
    const val ROUTE_SPEAKER = "speaker"
    const val ROUTE_WIRED = "wired_headset"
    const val ROUTE_BLUETOOTH = "bluetooth_a2dp"
    const val ROUTE_USB = "usb_headset"

    // Addresses the native adapter reports for the routes it can detect.
    const val ADDRESS_SPEAKER = "builtin"
    const val ADDRESS_WIRED = "h2w"

    private val BT_TYPES =
        setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )

    private val USB_TYPES =
        setOf(
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
        )

    /**
     * Returns the identity for `device`, or null when it cannot produce a stable
     * key. Null is deliberate: inventing a key would attach this device's settings
     * to another route.
     */
    fun identityFor(device: AudioDevice): DaemonProtocol.DeviceIdentity? {
        val routeType: String
        val address: String
        when {
            device.id == AudioDevice.ID_SPEAKER -> {
                routeType = ROUTE_SPEAKER
                address = ADDRESS_SPEAKER
            }
            device.id == AudioDevice.ID_WIRED -> {
                routeType = ROUTE_WIRED
                address = ADDRESS_WIRED
            }
            device.type in BT_TYPES -> {
                routeType = ROUTE_BLUETOOTH
                // AudioOutputDetector uses the MAC address as the device id.
                address = device.id
            }
            device.type in USB_TYPES -> {
                routeType = ROUTE_USB
                address = device.id
            }
            else -> return null
        }

        val identity =
            DaemonProtocol.DeviceIdentity(
                routeType = routeType,
                // Product name would change the key whenever a vendor renames a device,
                // so the route type is used for the routes the daemon can also detect.
                stableAddressOrPort = address,
                productName = routeType,
                sampleRate = ROUTE_SAMPLE_RATE,
                channelMask = ROUTE_CHANNEL_MASK,
                encoding = ROUTE_ENCODING,
            )
        return identity.takeIf { DaemonProtocol.isValidDeviceIdentity(it) }
    }

    /** Convenience: the hashed key the daemon stores snapshots under. */
    fun routeHashFor(device: AudioDevice): String? {
        val identity = identityFor(device) ?: return null
        val key = DaemonProtocol.normalizeDeviceKey(identity) ?: return null
        return DaemonProtocol.hashDeviceKey(key)
    }
}

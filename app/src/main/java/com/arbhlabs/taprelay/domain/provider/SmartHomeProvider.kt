package com.arbhlabs.taprelay.domain.provider

import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.DeviceCapabilities
import com.arbhlabs.taprelay.domain.model.DeviceSnapshot
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.domain.model.DiscoveredScene
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.model.TargetType

/**
 * A smart-home integration. Implementations translate every provider-specific failure
 * into a [com.arbhlabs.taprelay.domain.model.TapException] so the UI never sees raw errors.
 */
interface SmartHomeProvider {
    val providerId: String
    val displayName: String

    suspend fun isConnected(): Boolean

    suspend fun discoverDevices(): List<DiscoveredDevice>

    suspend fun discoverScenes(): List<DiscoveredScene> = emptyList()

    /** Current power state: 1 = on, 0 = off, null = unknown. */
    suspend fun getPowerState(deviceId: String, sku: String): Int?

    suspend fun setPower(deviceId: String, sku: String, on: Boolean)

    /** Sets brightness as a 1-100 percentage. Providers translate to their own scale. */
    suspend fun setBrightness(deviceId: String, sku: String, percent: Int): Unit =
        throw TapException(TapError.UNSUPPORTED_ACTION)

    /** Sets colour from a packed 0xRRGGBB integer. */
    suspend fun setColor(deviceId: String, sku: String, rgb: Int): Unit =
        throw TapException(TapError.UNSUPPORTED_ACTION)

    /**
     * Applies a colour and a brightness together. The default does the two calls in order,
     * which is right for providers that treat them as separate capabilities.
     */
    suspend fun setLook(deviceId: String, sku: String, rgb: Int, percent: Int) {
        setColor(deviceId, sku, rgb)
        setBrightness(deviceId, sku, percent)
    }

    suspend fun executeAction(
        targetId: String,
        targetType: TargetType,
        sku: String,
        action: ActionType,
        targetState: Int,
        brightnessPercent: Int? = null,
        colorRgb: Int? = null,
        fanLevel: String? = null
    ) {
        if (targetType == TargetType.SCENE) return
        applyToDevice(targetId, sku, targetState, brightnessPercent, colorRgb, fanLevel)
    }

    /**
     * Power, colour, brightness and fan speed are capabilities: a tag may set any combination of them.
     * Turning a device off is just off — while anything that ends with the device on also applies
     * whichever of fan speed, colour and brightness the tag carries.
     */
    suspend fun applyToDevice(
        deviceId: String,
        sku: String,
        targetState: Int,
        brightnessPercent: Int?,
        colorRgb: Int?,
        fanLevel: String? = null
    ) {
        if (targetState != 1) {
            setPower(deviceId, sku, on = false)
            return
        }
        when {
            colorRgb != null && brightnessPercent != null ->
                setLook(deviceId, sku, colorRgb, brightnessPercent)
            colorRgb != null -> setColor(deviceId, sku, colorRgb)
            brightnessPercent != null -> setBrightness(deviceId, sku, brightnessPercent)
            else -> setPower(deviceId, sku, on = true)
        }
    }

    /**
     * What this device can actually do. Quick Controls draws itself from this, so a provider
     * that knows more than discovery does should override it and say so.
     */
    suspend fun getCapabilities(deviceId: String, sku: String): DeviceCapabilities =
        discoverDevices().firstOrNull { it.deviceId == deviceId }
            ?.let { DeviceCapabilities.of(it) }
            ?: DeviceCapabilities.UNKNOWN

    /**
     * Capabilities for several of this provider's devices at once. The default reads discovery
     * a single time, so a tag driving five lights costs one request rather than five.
     */
    suspend fun getCapabilities(devices: List<Pair<String, String>>): List<DeviceCapabilities> {
        if (devices.isEmpty()) return emptyList()
        if (devices.size == 1) return listOf(getCapabilities(devices[0].first, devices[0].second))
        val discovered = discoverDevices().associateBy { it.deviceId }
        return devices.map { (id, _) ->
            discovered[id]?.let { DeviceCapabilities.of(it) } ?: DeviceCapabilities.UNKNOWN
        }
    }

    /** The device's live state, so a control surface opens showing what is really happening. */
    suspend fun getSnapshot(deviceId: String, sku: String): DeviceSnapshot =
        DeviceSnapshot(power = runCatching { getPowerState(deviceId, sku) }.getOrNull())

    /** Sets a provider-native fan level. Only called for devices that report fan levels. */
    suspend fun setFanLevel(deviceId: String, sku: String, level: String): Unit =
        throw TapException(TapError.UNSUPPORTED_ACTION)

    /** Sets a provider-native operating mode. Only called for devices that report modes. */
    suspend fun setMode(deviceId: String, sku: String, mode: String): Unit =
        throw TapException(TapError.UNSUPPORTED_ACTION)
}

package com.arbhlabs.taprelay.domain.provider

import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.Brightness
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
        colorRgb: Int? = null
    ) {
        if (targetType == TargetType.SCENE) return
        applyToDevice(targetId, sku, targetState, brightnessPercent, colorRgb)
    }

    /**
     * Power, colour and brightness are independent: a tag may set any combination of them.
     * Turning a light off is just off — there is no point colouring a dark bulb — while
     * anything that ends with the light on also applies whichever of colour and brightness
     * the tag carries.
     */
    suspend fun applyToDevice(
        deviceId: String,
        sku: String,
        targetState: Int,
        brightnessPercent: Int?,
        colorRgb: Int?
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
}

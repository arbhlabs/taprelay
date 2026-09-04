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
        when (action) {
            ActionType.SET_BRIGHTNESS -> setBrightness(
                targetId, sku, brightnessPercent ?: Brightness.DEFAULT_PERCENT
            )
            ActionType.SET_COLOR -> setColor(
                targetId, sku, colorRgb ?: throw TapException(TapError.UNSUPPORTED_ACTION)
            )
            ActionType.SET_SCENE -> setLook(
                targetId,
                sku,
                colorRgb ?: throw TapException(TapError.UNSUPPORTED_ACTION),
                brightnessPercent ?: Brightness.DEFAULT_PERCENT
            )
            else -> setPower(targetId, sku, on = targetState == 1)
        }
    }
}

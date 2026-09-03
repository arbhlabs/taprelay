package com.arbhlabs.taprelay.domain.provider

import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.domain.model.DiscoveredScene
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

    suspend fun executeAction(
        targetId: String,
        targetType: TargetType,
        sku: String,
        action: ActionType,
        targetState: Int
    ) {
        when (targetType) {
            TargetType.DEVICE -> setPower(targetId, sku, on = targetState == 1)
            TargetType.SCENE -> Unit
        }
    }
}

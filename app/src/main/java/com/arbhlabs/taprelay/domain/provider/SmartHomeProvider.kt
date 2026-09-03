package com.arbhlabs.taprelay.domain.provider

import com.arbhlabs.taprelay.domain.model.DiscoveredDevice

/**
 * A smart-home integration. Implementations translate every provider-specific failure
 * into a [com.arbhlabs.taprelay.domain.model.TapException] so the UI never sees raw errors.
 */
interface SmartHomeProvider {
    val providerId: String

    suspend fun isConnected(): Boolean

    suspend fun discoverDevices(): List<DiscoveredDevice>

    /** Current power state: 1 = on, 0 = off, null = unknown. */
    suspend fun getPowerState(deviceId: String, sku: String): Int?

    suspend fun setPower(deviceId: String, sku: String, on: Boolean)
}

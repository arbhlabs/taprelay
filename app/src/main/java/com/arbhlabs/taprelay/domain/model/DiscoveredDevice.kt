package com.arbhlabs.taprelay.domain.model

/** A smart-home device surfaced to the user by friendly name only. */
data class DiscoveredDevice(
    val deviceId: String,
    val sku: String,
    val name: String,
    val providerId: String,
    val supportsPower: Boolean,
    val supportsBrightness: Boolean = false,
    val supportsColor: Boolean = false,
    val isOnline: Boolean = true
)

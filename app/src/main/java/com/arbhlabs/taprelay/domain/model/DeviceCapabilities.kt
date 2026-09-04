package com.arbhlabs.taprelay.domain.model

/** Broadly what a device is, which decides the shape of its Quick Controls surface. */
enum class DeviceKind { LIGHT, CLIMATE, SWITCH, SCENE }

/**
 * What one device can actually do, as reported by its own provider.
 *
 * Quick Controls renders straight from this, so a control is only ever drawn when the real
 * device can perform it: a purifier with no `medium` fan level never shows a Medium button,
 * and a plug with no dimmer never shows a brightness slider.
 */
data class DeviceCapabilities(
    val kind: DeviceKind,
    val supportsPower: Boolean = true,
    val supportsBrightness: Boolean = false,
    val supportsColor: Boolean = false,
    val supportsColorTemperature: Boolean = false,
    /** Provider-native fan level ids, in the order the device reports them. Empty = no fan control. */
    val fanLevels: List<String> = emptyList(),
    /** Provider-native operating modes. Empty = no mode control. */
    val modes: List<String> = emptyList()
) {
    val supportsFanSpeed: Boolean get() = fanLevels.isNotEmpty()
    val supportsModes: Boolean get() = modes.size > 1

    companion object {
        /** What we can say about a device from discovery alone. */
        fun of(device: DiscoveredDevice): DeviceCapabilities = DeviceCapabilities(
            kind = DeviceKind.LIGHT,
            supportsPower = device.supportsPower,
            supportsBrightness = device.supportsBrightness,
            supportsColor = device.supportsColor,
            // Colour temperature is a point on the white curve, so it rides on colour support.
            supportsColorTemperature = device.supportsColor
        )

        val UNKNOWN = DeviceCapabilities(kind = DeviceKind.SWITCH, supportsPower = true)
        val SCENE = DeviceCapabilities(kind = DeviceKind.SCENE, supportsPower = false)
    }
}

/** The device's live state, used so Quick Controls opens showing what is actually happening. */
data class DeviceSnapshot(
    val power: Int? = null,
    val fanLevel: String? = null,
    val mode: String? = null
)

/** Turns provider-native ids into something a person reads. */
object DeviceLabels {
    fun fanLevel(id: String): String = when (id.lowercase()) {
        "quiet" -> "Quiet"
        "low" -> "Low"
        "medium_low" -> "Med-low"
        "medium" -> "Medium"
        "medium_high" -> "Med-high"
        "high" -> "High"
        "strong" -> "Strong"
        "auto" -> "Auto"
        else -> id.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun mode(id: String): String = when (id.lowercase()) {
        "cool" -> "Cool"
        "heat" -> "Heat"
        "fan" -> "Fan"
        "dry" -> "Dry"
        "auto" -> "Auto"
        else -> id.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

package com.arbhlabs.taprelay.data.remote.govee

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---- Device discovery: GET /router/api/v1/user/devices ----

@Serializable
data class GoveeDeviceListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<GoveeDeviceDto> = emptyList()
)

@Serializable
data class GoveeDeviceDto(
    val sku: String = "",
    val device: String = "",
    val deviceName: String = "",
    val type: String = "",
    val capabilities: List<GoveeCapabilityDto> = emptyList()
) {
    val supportsPower: Boolean
        get() = capabilities.any { it.type == "devices.capabilities.on_off" }

    val supportsBrightness: Boolean
        get() = capabilities.any { it.instance == "brightness" }

    val supportsColor: Boolean
        get() = capabilities.any { it.instance == "colorRgb" }
}

@Serializable
data class GoveeCapabilityDto(
    val type: String = "",
    val instance: String = ""
)

// ---- Control: POST /router/api/v1/device/control ----

@Serializable
data class GoveeControlRequest(
    val requestId: String,
    val payload: GoveeControlPayload
)

@Serializable
data class GoveeControlPayload(
    val sku: String,
    val device: String,
    val capability: GoveeCapabilityValue
)

@Serializable
data class GoveeCapabilityValue(
    val type: String,
    val instance: String,
    val value: Int
)

@Serializable
data class GoveeControlResponse(
    val code: Int = 0,
    val message: String = ""
)

// ---- State: POST /router/api/v1/device/state ----

@Serializable
data class GoveeStateRequest(
    val requestId: String,
    val payload: GoveeStateRequestPayload
)

@Serializable
data class GoveeStateRequestPayload(
    val sku: String,
    val device: String
)

@Serializable
data class GoveeStateResponse(
    val code: Int = 0,
    val message: String = "",
    val payload: GoveeStatePayload? = null
)

@Serializable
data class GoveeStatePayload(
    val sku: String = "",
    val device: String = "",
    val capabilities: List<GoveeStateCapability> = emptyList()
)

@Serializable
data class GoveeStateCapability(
    val type: String = "",
    val instance: String = "",
    val state: GoveeStateValue? = null
)

@Serializable
data class GoveeStateValue(
    val value: JsonElement? = null
)

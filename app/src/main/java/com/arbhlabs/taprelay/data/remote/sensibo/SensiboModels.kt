package com.arbhlabs.taprelay.data.remote.sensibo

import kotlinx.serialization.Serializable

@Serializable
data class SensiboPodsResponse(
    val status: String = "",
    val result: List<SensiboPodDto> = emptyList()
)

@Serializable
data class SensiboPodResponse(
    val status: String = "",
    val result: SensiboPodDto? = null
)

@Serializable
data class SensiboAcStateResponse(
    val status: String = "",
    val result: SensiboAcStateDto? = null
)

@Serializable
data class SensiboPodDto(
    val id: String = "",
    val room: SensiboRoomDto? = null,
    val productModel: String = "",
    val acState: SensiboAcStateDto? = null,
    val connectionStatus: SensiboConnectionStatusDto? = null
) {
    val isOnline: Boolean
        get() = connectionStatus?.isAlive ?: true

    val displayName: String
        get() {
            val roomName = room?.name?.takeIf { it.isNotBlank() } ?: "Sensibo"
            val modelName = when (productModel.lowercase()) {
                "pure" -> "Pure Air Purifier"
                "skyv2", "sky" -> "Sky"
                "air" -> "Air"
                "airpro" -> "Air Pro"
                "elements" -> "Elements"
                else -> if (productModel.isNotBlank()) productModel.replaceFirstChar { it.uppercase() } else "Device"
            }
            return "$roomName ($modelName)"
        }
}

@Serializable
data class SensiboRoomDto(
    val name: String = ""
)

@Serializable
data class SensiboConnectionStatusDto(
    val isAlive: Boolean = true
)

@Serializable
data class SensiboAcStateDto(
    val on: Boolean = false,
    val mode: String? = null,
    val fanLevel: String? = null,
    val targetTemperature: Double? = null,
    val temperatureUnit: String? = null
)

@Serializable
data class SensiboAcStateSetRequest(
    val acState: SensiboAcStatePayload
)

@Serializable
data class SensiboAcStatePayload(
    val on: Boolean,
    val fanLevel: String? = null
)

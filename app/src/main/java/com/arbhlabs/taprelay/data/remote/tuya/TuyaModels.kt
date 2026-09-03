package com.arbhlabs.taprelay.data.remote.tuya

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TuyaResponse<T>(
    val result: T? = null,
    val success: Boolean = false,
    val code: Int? = null,
    val msg: String? = null,
    val t: Long? = null
)

@Serializable
data class TuyaTokenResult(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("expire_time") val expireTime: Long = 7200,
    @SerialName("refresh_token") val refreshToken: String = "",
    val uid: String = ""
)

@Serializable
data class TuyaDeviceDto(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val product_id: String = "",
    val product_name: String = "",
    val online: Boolean = true,
    val status: List<TuyaDeviceStatusDto> = emptyList()
) {
    val friendlyName: String
        get() = name.ifBlank { product_name.ifBlank { id } }

    val supportsPower: Boolean
        get() = status.any { isPowerDp(it.code) } || category in setOf("dj", "cz", "kg", "cl", "sd")

    val powerState: Int?
        get() {
            val dp = status.firstOrNull { isPowerDp(it.code) } ?: return null
            return when (dp.value.toString().trim().lowercase()) {
                "true", "1", "\"true\"" -> 1
                "false", "0", "\"false\"" -> 0
                else -> null
            }
        }

    val primaryPowerDpCode: String
        get() {
            val found = status.firstOrNull { isPowerDp(it.code) }?.code
            if (!found.isNullOrBlank()) return found
            return if (category.lowercase() == "dj") "switch_led" else "switch_1"
        }

    companion object {
        fun isPowerDp(code: String): Boolean {
            val lower = code.lowercase()
            return lower == "switch_1" || lower == "switch_led" || lower == "switch" ||
                    lower == "switch_2" || lower == "switch_3" || lower == "power" ||
                    lower.startsWith("switch")
        }
    }
}

@Serializable
data class TuyaProjectDeviceDto(
    val id: String = "",
    val name: String = "",
    val customName: String = "",
    val category: String = "",
    val productId: String = "",
    val productName: String = "",
    val isOnline: Boolean = true
) {
    fun toLegacyDto() = TuyaDeviceDto(
        id = id,
        name = customName.ifBlank { name },
        category = category,
        product_id = productId,
        product_name = productName,
        online = isOnline
    )
}

@Serializable
data class TuyaDevicePageDto(
    val list: List<TuyaDeviceDto> = emptyList(),
    val total: Int = 0,
    val has_more: Boolean = false
)

@Serializable
data class TuyaHomeDto(
    val home_id: Long = 0,
    val name: String = ""
)

@Serializable
data class TuyaDeviceStatusDto(
    val code: String = "",
    val value: JsonElement
)

@Serializable
data class TuyaSceneDto(
    val scene_id: String = "",
    val name: String = "",
    val enabled: Boolean = true
)

@Serializable
data class TuyaCommandPayload(
    val commands: List<TuyaCommandItem>
)

@Serializable
data class TuyaCommandItem(
    val code: String,
    val value: JsonElement
)

package com.arbhlabs.taprelay.domain.provider

import com.arbhlabs.taprelay.data.remote.ha.HomeAssistantApiClient
import com.arbhlabs.taprelay.data.secure.HomeAssistantCredentials
import com.arbhlabs.taprelay.data.secure.SecureKeyStorage
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.DeviceCapabilities
import com.arbhlabs.taprelay.domain.model.DeviceKind
import com.arbhlabs.taprelay.domain.model.DeviceSnapshot
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.domain.model.DiscoveredScene
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.model.TargetType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

const val HOME_ASSISTANT_PROVIDER_ID = "homeassistant"

/**
 * Home Assistant as an ordinary [SmartHomeProvider].
 *
 * This is the whole reason Home Assistant took one file instead of a feature: because an HA light
 * is a `DiscoveredDevice` and an HA scene is a `DiscoveredScene`, the tag wizard, Quick Controls,
 * the controller mapping picker, places, chains, widgets and tap history all understood it the
 * moment it was registered - none of them were touched. A "Home Assistant action type" would have
 * needed its own copy of every one of those.
 *
 * TapRelay speaks to the owner's own instance directly. Nothing is proxied and no ARBH Labs
 * service is involved.
 */
class HomeAssistantProvider(
    private val api: HomeAssistantApiClient,
    private val secureStorage: SecureKeyStorage
) : SmartHomeProvider {

    override val providerId = HOME_ASSISTANT_PROVIDER_ID
    override val displayName = "Home Assistant"

    private suspend fun credentials(): HomeAssistantCredentials =
        secureStorage.getHomeAssistantCredentials().first()
            ?: throw TapException(TapError.AUTH_HOME_ASSISTANT)

    override suspend fun isConnected(): Boolean =
        secureStorage.getHomeAssistantCredentials().first() != null

    override suspend fun discoverDevices(): List<DiscoveredDevice> {
        val creds = credentials()
        return api.states(creds.baseUrl, creds.token)
            .mapNotNull { entity ->
                val id = entity.stringOf("entity_id") ?: return@mapNotNull null
                val domain = id.substringBefore(ENTITY_SEPARATOR)
                if (domain !in DEVICE_DOMAINS) return@mapNotNull null
                val attrs = entity["attributes"] as? JsonObject
                val colorModes = attrs.colorModes()
                DiscoveredDevice(
                    deviceId = id,
                    // Nothing in Home Assistant is a "SKU"; the domain is what decides how it is
                    // driven, so that is what the item carries in the same column.
                    sku = domain,
                    name = attrs?.stringOf("friendly_name") ?: id.friendlyFallback(),
                    providerId = providerId,
                    supportsPower = true,
                    supportsBrightness = domain == "light" && colorModes.any { it != "onoff" },
                    supportsColor = colorModes.any { it in COLOUR_MODES },
                    isOnline = entity.stringOf("state") != "unavailable"
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun discoverScenes(): List<DiscoveredScene> {
        val creds = credentials()
        return api.states(creds.baseUrl, creds.token)
            .mapNotNull { entity ->
                val id = entity.stringOf("entity_id") ?: return@mapNotNull null
                if (id.substringBefore(ENTITY_SEPARATOR) !in SCENE_DOMAINS) return@mapNotNull null
                val attrs = entity["attributes"] as? JsonObject
                DiscoveredScene(
                    sceneId = id,
                    name = attrs?.stringOf("friendly_name") ?: id.friendlyFallback(),
                    providerId = providerId
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    override suspend fun getPowerState(deviceId: String, sku: String): Int? {
        val creds = credentials()
        return when (api.state(creds.baseUrl, creds.token, deviceId)?.stringOf("state")) {
            "on" -> 1
            "off" -> 0
            else -> null
        }
    }

    override suspend fun setPower(deviceId: String, sku: String, on: Boolean) {
        val creds = credentials()
        // homeassistant.turn_on works for every domain, so a switch, a fan and a light are one
        // call rather than three branches that drift apart.
        api.callService(
            creds.baseUrl, creds.token,
            domain = "homeassistant",
            service = if (on) "turn_on" else "turn_off",
            entityId = deviceId
        )
    }

    override suspend fun setBrightness(deviceId: String, sku: String, percent: Int) {
        val creds = requireLight(deviceId)
        api.callService(
            creds.baseUrl, creds.token, "light", "turn_on", deviceId,
            buildJsonObject { put("brightness_pct", Brightness.clampPercent(percent)) }
        )
    }

    override suspend fun setColor(deviceId: String, sku: String, rgb: Int) {
        val creds = requireLight(deviceId)
        api.callService(
            creds.baseUrl, creds.token, "light", "turn_on", deviceId,
            buildJsonObject { put("rgb_color", rgb.toRgbArray()) }
        )
    }

    /** Home Assistant takes colour and brightness in one call, so a light never flashes twice. */
    override suspend fun setLook(deviceId: String, sku: String, rgb: Int, percent: Int) {
        val creds = requireLight(deviceId)
        api.callService(
            creds.baseUrl, creds.token, "light", "turn_on", deviceId,
            buildJsonObject {
                put("rgb_color", rgb.toRgbArray())
                put("brightness_pct", Brightness.clampPercent(percent))
            }
        )
    }

    override suspend fun executeAction(
        targetId: String,
        targetType: TargetType,
        sku: String,
        action: ActionType,
        targetState: Int,
        brightnessPercent: Int?,
        colorRgb: Int?,
        fanLevel: String?
    ) {
        if (targetType == TargetType.SCENE) {
            val creds = credentials()
            // A scene and a script both start with turn_on, each in its own domain.
            api.callService(
                creds.baseUrl, creds.token,
                domain = targetId.substringBefore(ENTITY_SEPARATOR).ifBlank { "scene" },
                service = "turn_on",
                entityId = targetId
            )
            return
        }
        applyToDevice(targetId, sku, targetState, brightnessPercent, colorRgb, fanLevel)
    }

    override suspend fun getCapabilities(deviceId: String, sku: String): DeviceCapabilities {
        val creds = credentials()
        val entity = api.state(creds.baseUrl, creds.token, deviceId) ?: return DeviceCapabilities.UNKNOWN
        val attrs = entity["attributes"] as? JsonObject
        val colorModes = attrs.colorModes()
        val domain = deviceId.substringBefore(ENTITY_SEPARATOR)
        return DeviceCapabilities(
            kind = if (domain == "light") DeviceKind.LIGHT else DeviceKind.SWITCH,
            supportsPower = true,
            supportsBrightness = domain == "light" && colorModes.any { it != "onoff" },
            supportsColor = colorModes.any { it in COLOUR_MODES },
            supportsColorTemperature = colorModes.any { it == "color_temp" }
        )
    }

    override suspend fun getSnapshot(deviceId: String, sku: String): DeviceSnapshot =
        DeviceSnapshot(power = runCatching { getPowerState(deviceId, sku) }.getOrNull())

    /** True when the supplied address and access token together reach a live instance. */
    suspend fun testConnection(baseUrl: String, token: String): Boolean = api.ping(baseUrl, token)

    private suspend fun requireLight(deviceId: String): HomeAssistantCredentials {
        if (deviceId.substringBefore(ENTITY_SEPARATOR) != "light") {
            throw TapException(TapError.UNSUPPORTED_ACTION)
        }
        return credentials()
    }

    private companion object {
        const val ENTITY_SEPARATOR = '.'

        /** Entity domains TapRelay can meaningfully drive with one press. */
        val DEVICE_DOMAINS = setOf("light", "switch", "fan", "input_boolean", "automation")
        val SCENE_DOMAINS = setOf("scene", "script")

        /** Colour modes that carry an actual hue, as opposed to white-only or on/off. */
        val COLOUR_MODES = setOf("hs", "rgb", "rgbw", "rgbww", "xy", "color_temp")

        fun JsonObject.stringOf(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        fun JsonObject?.colorModes(): List<String> =
            (this?.get("supported_color_modes") as? JsonArray)
                ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                .orEmpty()

        /** "light.desk_lamp" reads as "desk lamp" when the instance has no friendly name set. */
        fun String.friendlyFallback(): String =
            substringAfter(ENTITY_SEPARATOR).replace('_', ' ')

        fun Int.toRgbArray(): JsonArray = JsonArray(
            listOf(
                JsonPrimitive((this shr 16) and 0xFF),
                JsonPrimitive((this shr 8) and 0xFF),
                JsonPrimitive(this and 0xFF)
            )
        )
    }
}

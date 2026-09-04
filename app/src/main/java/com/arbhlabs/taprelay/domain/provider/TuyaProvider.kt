package com.arbhlabs.taprelay.domain.provider

import com.arbhlabs.taprelay.data.remote.tuya.TuyaApiClient
import com.arbhlabs.taprelay.data.remote.tuya.TuyaDeviceDto
import com.arbhlabs.taprelay.data.secure.SecureKeyStorage
import com.arbhlabs.taprelay.data.secure.TuyaCredentials
import com.arbhlabs.taprelay.data.remote.tuya.TuyaCommandItem
import com.arbhlabs.taprelay.data.remote.tuya.TuyaDeviceStatusDto
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.ColorMath
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.domain.model.DiscoveredScene
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.model.TargetType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

const val TUYA_PROVIDER_ID = "tuya_cloud"

class TuyaProvider(
    private val api: TuyaApiClient,
    private val keys: SecureKeyStorage? = null,
    private var inMemoryCreds: TuyaCredentials? = null
) : SmartHomeProvider {

    override val providerId = TUYA_PROVIDER_ID
    override val displayName = "Smart Life"

    private suspend fun credentials(): TuyaCredentials =
        inMemoryCreds ?: keys?.getTuyaCredentials()?.first() ?: throw TapException(TapError.AUTH_TUYA)

    /**
     * Drops the in-memory credential cache so a disconnect in the UI actually sticks.
     * Without this, [isConnected] keeps returning true from the cache until the process
     * dies and the next connection check silently re-enables the provider.
     */
    fun clearCredentials() {
        inMemoryCreds = null
    }

    suspend fun validateAndSave(creds: TuyaCredentials): Pair<List<DiscoveredDevice>, List<DiscoveredScene>> {
        val devices = api.getDevices(
            accessId = creds.accessId.trim(),
            secret = creds.accessSecret.trim(),
            region = creds.region.trim(),
            uid = creds.uid.trim()
        )
        val scenes = api.getScenes(
            accessId = creds.accessId.trim(),
            secret = creds.accessSecret.trim(),
            region = creds.region.trim(),
            uid = creds.uid.trim()
        )

        keys?.saveTuyaCredentials(creds)
        inMemoryCreds = creds
        val discDevices = devices.toDiscovered()
        val discScenes = scenes.map { DiscoveredScene(it.scene_id, it.name.ifBlank { "Scene" }, TUYA_PROVIDER_ID) }
        return Pair(discDevices, discScenes)
    }

    override suspend fun isConnected(): Boolean =
        inMemoryCreds != null || (keys?.getTuyaCredentials()?.first() != null)

    override suspend fun discoverDevices(): List<DiscoveredDevice> {
        val creds = credentials()
        val raw = api.getDevices(
            accessId = creds.accessId,
            secret = creds.accessSecret,
            region = creds.region,
            uid = creds.uid
        )
        return raw.toDiscovered()
    }

    override suspend fun discoverScenes(): List<DiscoveredScene> {
        val creds = credentials()
        val raw = api.getScenes(
            accessId = creds.accessId,
            secret = creds.accessSecret,
            region = creds.region,
            uid = creds.uid
        )
        return raw.map {
            DiscoveredScene(
                sceneId = it.scene_id,
                name = it.name.ifBlank { "Smart Scene" },
                providerId = TUYA_PROVIDER_ID
            )
        }
    }

    override suspend fun getPowerState(deviceId: String, sku: String): Int? {
        val creds = credentials()
        if (api.getDeviceOnline(
                accessId = creds.accessId,
                secret = creds.accessSecret,
                region = creds.region,
                deviceId = deviceId
            ) == false
        ) throw TapException(TapError.DEVICE_OFFLINE)

        // The status endpoint is the authoritative live value. The device record can
        // carry an older snapshot and must not drive a toggle decision.
        val statusList = api.getDeviceStatus(
            accessId = creds.accessId,
            secret = creds.accessSecret,
            region = creds.region,
            deviceId = deviceId
        )
        val dp = statusList.firstOrNull { TuyaDeviceDto.isPowerDp(it.code) } ?: return null
        return when (dp.value.toString().trim().lowercase()) {
            "true", "1", "\"true\"" -> 1
            "false", "0", "\"false\"" -> 0
            else -> null
        }
    }

    override suspend fun setPower(deviceId: String, sku: String, on: Boolean) {
        val creds = credentials()
        val dpCode = if (sku.isNotBlank() && sku != "tuya_device") sku else {
            api.getDeviceStatus(
                accessId = creds.accessId,
                secret = creds.accessSecret,
                region = creds.region,
                deviceId = deviceId
            ).firstOrNull { TuyaDeviceDto.isPowerDp(it.code) }?.code ?: "switch_1"
        }
        api.sendPowerCommand(
            accessId = creds.accessId,
            secret = creds.accessSecret,
            region = creds.region,
            deviceId = deviceId,
            dpCode = dpCode,
            turnOn = on
        )
    }

    /**
     * The exact data-point codes differ between Tuya products (`bright_value` vs
     * `bright_value_v2`), so read them from the device rather than assuming.
     */
    private suspend fun dataPoints(deviceId: String): List<TuyaDeviceStatusDto> {
        val creds = credentials()
        return api.getDeviceStatus(
            accessId = creds.accessId,
            secret = creds.accessSecret,
            region = creds.region,
            deviceId = deviceId
        )
    }

    private suspend fun send(deviceId: String, commands: List<TuyaCommandItem>) {
        val creds = credentials()
        api.sendCommands(
            accessId = creds.accessId,
            secret = creds.accessSecret,
            region = creds.region,
            deviceId = deviceId,
            commands = commands
        )
    }

    override suspend fun setBrightness(deviceId: String, sku: String, percent: Int) {
        val status = dataPoints(deviceId)
        val codes = status.map { it.code }
        val powerCode = codes.firstOrNull { TuyaDeviceDto.isPowerDp(it) }
            ?: sku.takeIf { it.isNotBlank() && it != "tuya_device" }
        val mode = status.firstOrNull { it.code == WORK_MODE }
            ?.value?.let { (it as? JsonPrimitive)?.contentOrNull ?: it.toString().trim('"') }
            ?.lowercase()
        val colorCode = codes.firstOrNull { it.startsWith(COLOR_PREFIX) }

        // A brightness-only tag must not disturb the colour the bulb is already showing —
        // the user may have set it from Google Home or the Smart Life app. In colour mode
        // the brightness IS the colour's own value channel, so change only that and leave
        // hue and saturation exactly where they are. Forcing work_mode=white here is what
        // used to knock the light back to plain white.
        if (mode == "colour" && colorCode != null) {
            val (h, s) = currentHueSat(status, colorCode)
            val commands = buildList {
                powerCode?.let { add(TuyaCommandItem(it, JsonPrimitive(true))) }
                add(
                    TuyaCommandItem(
                        colorCode,
                        buildJsonObject {
                            put("h", h)
                            put("s", s)
                            put("v", Brightness.toTuyaColorValue(percent))
                        }
                    )
                )
            }
            send(deviceId, commands)
            return
        }

        // White mode (or a light with no colour mode at all): the plain brightness data point.
        val brightCode = codes.firstOrNull { it.startsWith(BRIGHTNESS_PREFIX) }
            ?: throw TapException(TapError.UNSUPPORTED_ACTION)
        val commands = buildList {
            // Asking for a brightness means asking for light.
            powerCode?.let { add(TuyaCommandItem(it, JsonPrimitive(true))) }
            add(TuyaCommandItem(brightCode, JsonPrimitive(Brightness.toTuya(percent))))
        }
        send(deviceId, commands)
    }

    /** Reads the hue and saturation the light is currently set to, defaulting to a plain warm value. */
    private fun currentHueSat(status: List<TuyaDeviceStatusDto>, colorCode: String): Pair<Int, Int> {
        val el = status.firstOrNull { it.code == colorCode }?.value ?: return 0 to 1000
        val raw = (el as? JsonPrimitive)?.contentOrNull ?: el.toString().trim('"')
        return runCatching {
            val obj = Json.parseToJsonElement(raw).jsonObject
            (obj["h"]?.jsonPrimitive?.intOrNull ?: 0) to (obj["s"]?.jsonPrimitive?.intOrNull ?: 1000)
        }.getOrDefault(0 to 1000)
    }

    override suspend fun setColor(deviceId: String, sku: String, rgb: Int) {
        val codes = dataPoints(deviceId).map { it.code }
        val colorCode = codes.firstOrNull { it.startsWith(COLOR_PREFIX) }
            ?: throw TapException(TapError.UNSUPPORTED_ACTION)
        val powerCode = codes.firstOrNull { TuyaDeviceDto.isPowerDp(it) }
            ?: sku.takeIf { it.isNotBlank() && it != "tuya_device" }

        val (h, sat, v) = ColorMath.toTuyaHsv(rgb)
        val commands = buildList {
            powerCode?.let { add(TuyaCommandItem(it, JsonPrimitive(true))) }
            if (codes.contains(WORK_MODE)) add(TuyaCommandItem(WORK_MODE, JsonPrimitive("colour")))
            add(
                TuyaCommandItem(
                    colorCode,
                    buildJsonObject {
                        put("h", h)
                        put("s", sat)
                        put("v", v)
                    }
                )
            )
        }
        send(deviceId, commands)
    }

    override suspend fun setLook(deviceId: String, sku: String, rgb: Int, percent: Int) {
        val codes = dataPoints(deviceId).map { it.code }
        val colorCode = codes.firstOrNull { it.startsWith(COLOR_PREFIX) }
            ?: throw TapException(TapError.UNSUPPORTED_ACTION)
        val powerCode = codes.firstOrNull { TuyaDeviceDto.isPowerDp(it) }
            ?: sku.takeIf { it.isNotBlank() && it != "tuya_device" }

        // In colour mode the brightness is the "v" of the colour, not bright_value, so the
        // two are sent as one data point rather than as two commands that undo each other.
        val (h, sat, _) = ColorMath.toTuyaHsv(rgb)
        val value = Brightness.toTuyaColorValue(percent)
        val commands = buildList {
            powerCode?.let { add(TuyaCommandItem(it, JsonPrimitive(true))) }
            if (codes.contains(WORK_MODE)) add(TuyaCommandItem(WORK_MODE, JsonPrimitive("colour")))
            add(
                TuyaCommandItem(
                    colorCode,
                    buildJsonObject {
                        put("h", h)
                        put("s", sat)
                        put("v", value)
                    }
                )
            )
        }
        send(deviceId, commands)
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
        if (targetType == TargetType.SCENE || action == ActionType.RUN_SCENE) {
            val creds = credentials()
            api.triggerScene(
                accessId = creds.accessId,
                secret = creds.accessSecret,
                region = creds.region,
                sceneId = targetId
            )
            return
        }
        applyToDevice(targetId, sku, targetState, brightnessPercent, colorRgb)
    }

    private fun List<TuyaDeviceDto>.toDiscovered(): List<DiscoveredDevice> =
        filter { it.supportsPower || it.category in setOf("dj", "cz", "kg", "cl", "sd") }
            .map {
                val codes = it.status.map { dp -> dp.code }
                // An offline light cannot report its data points, so fall back to its category:
                // "dj" is Tuya's colour-light category.
                val assumeLight = codes.isEmpty() && it.category.lowercase() == "dj"
                DiscoveredDevice(
                    deviceId = it.id,
                    sku = it.primaryPowerDpCode,
                    name = it.friendlyName,
                    providerId = TUYA_PROVIDER_ID,
                    supportsPower = true,
                    supportsBrightness = assumeLight || codes.any { c -> c.startsWith(BRIGHTNESS_PREFIX) },
                    supportsColor = assumeLight || codes.any { c -> c.startsWith(COLOR_PREFIX) },
                    isOnline = it.online
                )
            }

    private companion object {
        const val BRIGHTNESS_PREFIX = "bright_value"
        const val COLOR_PREFIX = "colour_data"
        const val WORK_MODE = "work_mode"
    }
}

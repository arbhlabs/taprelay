package com.arbhlabs.taprelay.domain.provider

import com.arbhlabs.taprelay.data.remote.sensibo.SensiboApiClient
import com.arbhlabs.taprelay.data.remote.sensibo.SensiboPodDto
import com.arbhlabs.taprelay.data.secure.SecureKeyStorage
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.DeviceCapabilities
import com.arbhlabs.taprelay.domain.model.DeviceKind
import com.arbhlabs.taprelay.domain.model.DeviceSnapshot
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.model.TargetType
import kotlinx.coroutines.flow.first

const val SENSIBO_PROVIDER_ID = "sensibo"

/**
 * Sensibo API v2.0 (cloud REST). Controls Sensibo Pure air purifiers, smart fans, and AC controllers.
 * Supports power toggle/on/off and fan speed levels.
 */
class SensiboProvider(
    private val api: SensiboApiClient,
    private val keys: SecureKeyStorage? = null,
    private var inMemoryKey: String? = null
) : SmartHomeProvider {

    override val providerId = SENSIBO_PROVIDER_ID
    override val displayName = "Sensibo"

    private suspend fun key(): String =
        inMemoryKey ?: keys?.getSensiboApiKey()?.first() ?: throw TapException(TapError.AUTH_SENSIBO)

    /** Validates an API key by attempting a discovery call. Throws TapException on failure. */
    suspend fun validateAndSave(apiKey: String): List<DiscoveredDevice> {
        val pods = api.getPods(apiKey.trim())
        keys?.saveSensiboApiKey(apiKey.trim())
        inMemoryKey = apiKey.trim()
        return pods.toDiscovered()
    }

    override suspend fun isConnected(): Boolean =
        inMemoryKey?.isNotBlank() == true || keys?.getSensiboApiKey()?.first()?.isNotBlank() == true

    override suspend fun discoverDevices(): List<DiscoveredDevice> =
        api.getPods(key()).toDiscovered()

    override suspend fun getPowerState(deviceId: String, sku: String): Int? =
        api.getPowerState(key(), deviceId)

    override suspend fun setPower(deviceId: String, sku: String, on: Boolean) =
        api.setPower(key(), deviceId, on)

    override suspend fun setBrightness(deviceId: String, sku: String, percent: Int) {
        val apiKey = key()
        // Map 1-100 percentage to Sensibo fan speed level
        val fanLevel = when {
            percent <= 25 -> "quiet"
            percent <= 50 -> "low"
            percent <= 75 -> "medium"
            else -> "high"
        }
        api.setFanLevel(apiKey, deviceId, fanLevel)
    }

    suspend fun toggleFanSpeed(deviceId: String): String =
        api.toggleFanSpeed(key(), deviceId)

    /**
     * Only the levels and modes the unit itself reports. A Pure air purifier reports a single
     * "fan" mode, so no mode picker is offered for it; an AC controller reports several.
     */
    override suspend fun getCapabilities(deviceId: String, sku: String): DeviceCapabilities {
        val pod = api.getPod(key(), deviceId)
        val modes = pod.remoteCapabilities?.modes.orEmpty()
        val currentMode = pod.acState?.mode?.lowercase()
        // Fan levels belong to a mode; show the ones for the mode the unit is actually in.
        val levels = modes[currentMode]?.fanLevels
            ?: modes["fan"]?.fanLevels
            ?: modes.values.firstOrNull()?.fanLevels
            ?: emptyList()
        return DeviceCapabilities(
            kind = DeviceKind.CLIMATE,
            supportsPower = true,
            supportsBrightness = false,
            supportsColor = false,
            supportsColorTemperature = false,
            fanLevels = levels,
            modes = modes.keys.toList()
        )
    }

    /** Each pod reports its own levels, so they are read one at a time rather than from a list. */
    override suspend fun getCapabilities(devices: List<Pair<String, String>>): List<DeviceCapabilities> =
        devices.map { (id, sku) -> getCapabilities(id, sku) }

    override suspend fun getSnapshot(deviceId: String, sku: String): DeviceSnapshot {
        val state = api.getAcState(key(), deviceId)
        return DeviceSnapshot(
            power = state?.let { if (it.on) 1 else 0 },
            fanLevel = state?.fanLevel,
            mode = state?.mode
        )
    }

    override suspend fun setFanLevel(deviceId: String, sku: String, level: String) =
        api.setFanLevel(key(), deviceId, level)

    override suspend fun setMode(deviceId: String, sku: String, mode: String) =
        api.setMode(key(), deviceId, mode)

    suspend fun getAcState(deviceId: String) =
        api.getAcState(key(), deviceId)

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
        if (targetType == TargetType.SCENE) return
        if (action == ActionType.TOGGLE_FAN_SPEED) {
            toggleFanSpeed(targetId)
            return
        }
        applyToDevice(targetId, sku, targetState, brightnessPercent, colorRgb, fanLevel)
    }

    override suspend fun applyToDevice(
        deviceId: String,
        sku: String,
        targetState: Int,
        brightnessPercent: Int?,
        colorRgb: Int?,
        fanLevel: String?
    ) {
        if (targetState != 1) {
            setPower(deviceId, sku, on = false)
            return
        }
        val level = fanLevel ?: when {
            brightnessPercent != null && brightnessPercent <= 25 -> "quiet"
            brightnessPercent != null && brightnessPercent <= 50 -> "low"
            brightnessPercent != null && brightnessPercent <= 75 -> "medium"
            brightnessPercent != null -> "high"
            else -> null
        }
        if (level != null) {
            api.setFanLevel(key(), deviceId, level)
        } else {
            setPower(deviceId, sku, on = true)
        }
    }

    private fun List<SensiboPodDto>.toDiscovered(): List<DiscoveredDevice> = map { pod ->
        DiscoveredDevice(
            deviceId = pod.id,
            sku = pod.productModel.ifBlank { "sensibo" },
            name = pod.displayName,
            providerId = SENSIBO_PROVIDER_ID,
            supportsPower = true,
            supportsBrightness = true,
            supportsColor = false,
            isOnline = pod.connectionStatus?.isAlive ?: true
        )
    }
}

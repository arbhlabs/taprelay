package com.arbhlabs.taprelay.domain.provider

import com.arbhlabs.taprelay.data.remote.sensibo.SensiboApiClient
import com.arbhlabs.taprelay.data.remote.sensibo.SensiboPodDto
import com.arbhlabs.taprelay.data.secure.SecureKeyStorage
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
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

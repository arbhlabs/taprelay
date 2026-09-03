package com.arbhlabs.taprelay.domain.provider

import com.arbhlabs.taprelay.data.remote.govee.GoveeApiClient
import com.arbhlabs.taprelay.data.secure.SecureKeyStorage
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import kotlinx.coroutines.flow.first

const val GOVEE_PROVIDER_ID = "govee_cloud"

/**
 * Govee OpenAPI v2.0 (cloud REST). Used for cloud-only devices such as the H6003 test bulb.
 *
 * COMMERCIAL NOTE: Govee's developer API is licensed for personal / non-profit use only.
 * TapRelay ships this integration as a private alpha under a Bring-Your-Own-Key model and
 * makes no claim of Govee partnership or approval.
 */
class GoveeCloudProvider(
    private val api: GoveeApiClient,
    private val keys: SecureKeyStorage
) : SmartHomeProvider {

    override val providerId = GOVEE_PROVIDER_ID

    private suspend fun key(): String =
        keys.getGoveeApiKey().first() ?: throw TapException(TapError.AUTH)

    /** Validates a key by attempting a discovery call. Throws TapException on failure. */
    suspend fun validateAndSave(apiKey: String): List<DiscoveredDevice> {
        val devices = api.getDevices(apiKey.trim())
        keys.saveGoveeApiKey(apiKey.trim())
        return devices.toDiscovered()
    }

    override suspend fun isConnected(): Boolean =
        keys.getGoveeApiKey().first()?.isNotBlank() == true

    override suspend fun discoverDevices(): List<DiscoveredDevice> =
        api.getDevices(key()).toDiscovered()

    override suspend fun getPowerState(deviceId: String, sku: String): Int? =
        api.getPowerState(key(), sku, deviceId)

    override suspend fun setPower(deviceId: String, sku: String, on: Boolean) =
        api.setPower(key(), sku, deviceId, on)

    private fun List<com.arbhlabs.taprelay.data.remote.govee.GoveeDeviceDto>.toDiscovered() =
        filter { it.supportsPower }.map {
            DiscoveredDevice(
                deviceId = it.device,
                sku = it.sku,
                name = it.deviceName.ifBlank { it.sku },
                providerId = GOVEE_PROVIDER_ID,
                supportsPower = true
            )
        }
}

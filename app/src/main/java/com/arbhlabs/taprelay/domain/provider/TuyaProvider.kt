package com.arbhlabs.taprelay.domain.provider

import com.arbhlabs.taprelay.data.remote.tuya.TuyaApiClient
import com.arbhlabs.taprelay.data.remote.tuya.TuyaDeviceDto
import com.arbhlabs.taprelay.data.secure.SecureKeyStorage
import com.arbhlabs.taprelay.data.secure.TuyaCredentials
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.domain.model.DiscoveredScene
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.model.TargetType
import kotlinx.coroutines.flow.first

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

    override suspend fun executeAction(
        targetId: String,
        targetType: TargetType,
        sku: String,
        action: ActionType,
        targetState: Int
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
        setPower(targetId, sku, on = targetState == 1)
    }

    private fun List<TuyaDeviceDto>.toDiscovered(): List<DiscoveredDevice> =
        filter { it.supportsPower || it.category in setOf("dj", "cz", "kg", "cl", "sd") }
            .map {
                DiscoveredDevice(
                    deviceId = it.id,
                    sku = it.primaryPowerDpCode,
                    name = it.friendlyName,
                    providerId = TUYA_PROVIDER_ID,
                    supportsPower = true,
                    isOnline = it.online
                )
            }
}

package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.remote.tuya.TuyaApiClient
import com.arbhlabs.taprelay.data.secure.TuyaCredentials
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.TargetType
import com.arbhlabs.taprelay.domain.provider.TUYA_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.TuyaProvider
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuyaProviderTest {

    @Test
    fun provider_metadata() {
        val provider = TuyaProvider(TuyaApiClient())
        assertEquals(TUYA_PROVIDER_ID, provider.providerId)
        assertEquals("Smart Life", provider.displayName)
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val tokenJson = """
        {
          "success": true,
          "result": {
            "access_token": "mock_token_123",
            "expire_time": 7200,
            "refresh_token": "mock_refresh_123",
            "uid": "bay123"
          }
        }
    """.trimIndent()

    private val realDevicesJson = """
        {
          "success": true,
          "result": [
            {
              "id": "lamp_corner",
              "name": "Corner Lamp",
              "category": "dj",
              "online": true,
              "status": [
                { "code": "switch_led", "value": true }
              ]
            },
            {
              "id": "light_accent_top",
              "name": "Accent Light Top",
              "category": "dj",
              "online": false,
              "status": []
            },
            {
              "id": "light_accent_bottom",
              "name": "Accent Lamp Bottom",
              "category": "dj",
              "online": false,
              "status": []
            }
          ]
        }
    """.trimIndent()

    private val lampDetailJson = """
        {
          "success": true,
          "result": {
            "id": "lamp_corner",
            "name": "Corner Lamp",
            "category": "dj",
            "online": true,
            "status": [
              { "code": "switch_led", "value": true }
            ]
          }
        }
    """.trimIndent()

    private val offlineDetailJson = """
        {
          "success": true,
          "result": {
            "id": "light_accent_top",
            "name": "Accent Light Top",
            "category": "dj",
            "online": false,
            "status": []
          }
        }
    """.trimIndent()

    private val commandSuccessJson = """
        {
          "success": true,
          "result": true
        }
    """.trimIndent()

    private val lampStatusJson = """
        {
          "success": true,
          "result": [
            { "code": "switch_led", "value": true }
          ]
        }
    """.trimIndent()

    private val scenesJson = """
        {
          "success": true,
          "result": [
            { "scene_id": "scene_movie", "name": "Movie Mode", "enabled": true }
          ]
        }
    """.trimIndent()

    @Test
    fun real_tuya_discovery_and_control() = runTest {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("token") -> respond(tokenJson, HttpStatusCode.OK, jsonHeaders)
                url.contains("devices/lamp_corner/commands") -> respond(commandSuccessJson, HttpStatusCode.OK, jsonHeaders)
                url.contains("devices/lamp_corner/status") -> respond(lampStatusJson, HttpStatusCode.OK, jsonHeaders)
                url.contains("devices/lamp_corner") -> respond(lampDetailJson, HttpStatusCode.OK, jsonHeaders)
                url.contains("devices/light_accent_top") -> respond(offlineDetailJson, HttpStatusCode.OK, jsonHeaders)
                url.contains("devices") -> respond(realDevicesJson, HttpStatusCode.OK, jsonHeaders)
                url.contains("scenes") -> respond(scenesJson, HttpStatusCode.OK, jsonHeaders)
                else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
            }
        }
        val client = TuyaApiClient(engine)
        val provider = TuyaProvider(client)
        assertTrue(!provider.isConnected())

        val creds = TuyaCredentials(
            accessId = "test_id",
            accessSecret = "test_secret",
            region = "us",
            uid = "bay123"
        )
        val (devices, scenes) = provider.validateAndSave(creds)

        assertTrue(provider.isConnected())
        assertEquals(3, devices.size)
        assertEquals(1, scenes.size)

        // Verify devices: Corner Lamp (online), Accent Light Top (offline), Accent Lamp Bottom (offline)
        val cornerLamp = devices.first { it.deviceId == "lamp_corner" }
        assertEquals("Corner Lamp", cornerLamp.name)
        assertTrue(cornerLamp.isOnline)
        assertEquals("switch_led", cornerLamp.sku)

        val accentTop = devices.first { it.deviceId == "light_accent_top" }
        assertEquals("Accent Light Top", accentTop.name)
        assertTrue(!accentTop.isOnline)

        val accentBottom = devices.first { it.deviceId == "light_accent_bottom" }
        assertEquals("Accent Lamp Bottom", accentBottom.name)
        assertTrue(!accentBottom.isOnline)

        // Read power state for online lamp -> 1 (ON)
        val powerState = provider.getPowerState("lamp_corner", cornerLamp.sku)
        assertEquals(1, powerState)

        // Read power state for offline lamp -> throws TapError.DEVICE_OFFLINE
        val offlineEx = runCatching { provider.getPowerState("light_accent_top", accentTop.sku) }.exceptionOrNull()
        assertTrue(offlineEx is TapException && (offlineEx as TapException).error == TapError.DEVICE_OFFLINE)

        // Set power for online lamp -> success
        provider.setPower("lamp_corner", cornerLamp.sku, false)

        // Execute action TOGGLE
        provider.executeAction("lamp_corner", TargetType.DEVICE, cornerLamp.sku, ActionType.TOGGLE, 0)

        // Disconnect must stick: clearing the in-memory cache drops the connection.
        provider.clearCredentials()
        assertTrue(!provider.isConnected())
    }
}

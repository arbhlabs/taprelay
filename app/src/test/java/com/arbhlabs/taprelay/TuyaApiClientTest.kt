package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.remote.tuya.TuyaApiClient
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

class TuyaApiClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val tokenJson = """
        {
          "success": true,
          "t": 1700000000000,
          "result": {
            "access_token": "mock_access_token_123",
            "expire_time": 7200,
            "refresh_token": "mock_refresh_token_456",
            "uid": "bay12345"
          }
        }
    """.trimIndent()

    private val devicesJson = """
        {
          "success": true,
          "result": [
            {
              "id": "device_light_1",
              "name": "Desk Lamp",
              "category": "dj",
              "product_name": "Smart LED",
              "online": true,
              "status": [
                { "code": "switch_led", "value": true }
              ]
            },
            {
              "id": "device_sensor_2",
              "name": "Door Sensor",
              "category": "mcs",
              "product_name": "Sensor",
              "online": true,
              "status": []
            }
          ]
        }
    """.trimIndent()

    private val scenesJson = """
        {
          "success": true,
          "result": [
            { "scene_id": "scene_bedtime_1", "name": "Bedtime", "enabled": true },
            { "scene_id": "scene_movie_2", "name": "Movie Night", "enabled": true }
          ]
        }
    """.trimIndent()

    @Test
    fun getAccessToken_and_getDevices_success() = runTest {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("token") -> respond(tokenJson, HttpStatusCode.OK, jsonHeaders)
                url.contains("devices") -> respond(devicesJson, HttpStatusCode.OK, jsonHeaders)
                else -> {
                    System.err.println("UNMATCHED URL: $url")
                    respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        }
        val client = TuyaApiClient(engine)
        val devices = client.getDevices("access_id", "access_secret", "us", "bay12345")
        assertEquals(2, devices.size)
        assertEquals("Desk Lamp", devices[0].friendlyName)
        assertTrue(devices[0].supportsPower)
        assertEquals("switch_led", devices[0].primaryPowerDpCode)
        assertEquals(1, devices[0].powerState)
        assertTrue(!devices[1].supportsPower)
    }

    @Test
    fun getScenes_success() = runTest {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("token") -> respond(tokenJson, HttpStatusCode.OK, jsonHeaders)
                url.contains("scenes") -> respond(scenesJson, HttpStatusCode.OK, jsonHeaders)
                else -> {
                    System.err.println("UNMATCHED URL: $url")
                    respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                }
            }
        }
        val client = TuyaApiClient(engine)
        val scenes = client.getScenes("access_id", "access_secret", "us", "bay12345")
        assertEquals(2, scenes.size)
        assertEquals("Bedtime", scenes[0].name)
        assertEquals("scene_bedtime_1", scenes[0].scene_id)
    }

    @Test
    fun unauthorized_token_maps_to_auth_tuya() = runTest {
        val engine = MockEngine {
            respond("{\"success\":false,\"code\":1004,\"msg\":\"sign invalid\"}", HttpStatusCode.OK, jsonHeaders)
        }
        val client = TuyaApiClient(engine)
        val e = runCatching { client.getAccessToken("bad_id", "bad_secret", "us") }.exceptionOrNull()
        assertTrue(e is TapException && (e as TapException).error == TapError.AUTH_TUYA)
    }

    @Test
    fun http_unauthorized_maps_to_auth_tuya() = runTest {
        val engine = MockEngine {
            respond("{}", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        val client = TuyaApiClient(engine)
        val e = runCatching { client.getAccessToken("bad_id", "bad_secret", "us") }.exceptionOrNull()
        assertTrue(e is TapException && (e as TapException).error == TapError.AUTH_TUYA)
    }

    @Test
    fun rate_limit_maps_to_rate_limit_error() = runTest {
        val engine = MockEngine {
            respond("{}", HttpStatusCode.TooManyRequests, jsonHeaders)
        }
        val client = TuyaApiClient(engine)
        val e = runCatching { client.getAccessToken("id", "secret", "us") }.exceptionOrNull()
        assertTrue(e is TapException && (e as TapException).error == TapError.RATE_LIMIT)
    }

    @Test
    fun server_error_maps_to_server_error() = runTest {
        val engine = MockEngine {
            respond("{}", HttpStatusCode.InternalServerError, jsonHeaders)
        }
        val client = TuyaApiClient(engine)
        val e = runCatching { client.getAccessToken("id", "secret", "us") }.exceptionOrNull()
        assertTrue(e is TapException && (e as TapException).error == TapError.SERVER)
    }
}

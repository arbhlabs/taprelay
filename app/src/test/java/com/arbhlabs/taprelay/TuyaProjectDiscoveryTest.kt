package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.remote.tuya.TuyaApiClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the response shapes a live Central-Europe Smart Home
 * project actually returns. Captured from the Tuya cloud on 2026-09-04.
 */
class TuyaProjectDiscoveryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val tokenJson = """
        {"success":true,"result":{"access_token":"tok","expire_time":7200,"refresh_token":"r","uid":"eu1769"}}
    """.trimIndent()

    /**
     * The project endpoint carries the account's friendly label in `customName`
     * while `name` holds the generic product label, and liveness in `isOnline`.
     */
    private val projectDevicesJson = """
        {
          "success": true,
          "result": [
            {
              "activeTime": 1769518472,
              "category": "dj",
              "customName": "Corner Lamp",
              "id": "bf6b6678a0920f988ahwxf",
              "isOnline": true,
              "name": "Candle Lamp W505Z2 3",
              "productId": "w57tepkyqlsegcov",
              "productName": "Candle Lamp W505Z2",
              "sub": false
            },
            {
              "activeTime": 1769518361,
              "category": "dj",
              "customName": "Accent Lamp Bottom",
              "id": "bfd223681b909ab54dpuct",
              "isOnline": false,
              "name": "Candle Lamp W505Z2 2",
              "productId": "w57tepkyqlsegcov",
              "productName": "Candle Lamp W505Z2",
              "sub": false
            },
            {
              "activeTime": 1769518240,
              "category": "dj",
              "customName": "Accent Light Top",
              "id": "bf874497d5ca947de05ity",
              "isOnline": true,
              "name": "Candle Lamp W505Z2",
              "productId": "w57tepkyqlsegcov",
              "productName": "Candle Lamp W505Z2",
              "sub": false
            }
          ]
        }
    """.trimIndent()

    private val statusJson = """
        {
          "success": true,
          "result": [
            {"code":"switch_led","value":false},
            {"code":"work_mode","value":"white"},
            {"code":"bright_value_v2","value":450}
          ]
        }
    """.trimIndent()

    private fun engineFor(
        projectResponse: String = projectDevicesJson,
        projectStatus: HttpStatusCode = HttpStatusCode.OK
    ) = MockEngine { request ->
        val url = request.url.toString()
        when {
            url.contains("/v1.0/token") -> respond(tokenJson, HttpStatusCode.OK, jsonHeaders)
            url.contains("/v2.0/cloud/thing/device") -> respond(projectResponse, projectStatus, jsonHeaders)
            url.contains("/status") -> respond(statusJson, HttpStatusCode.OK, jsonHeaders)
            else -> respond("""{"success":false,"code":1106,"msg":"permission deny"}""", HttpStatusCode.OK, jsonHeaders)
        }
    }

    @Test
    fun project_discovery_uses_account_friendly_names_not_product_names() = runTest {
        val devices = TuyaApiClient(engineFor()).getDevices("id", "secret", "eu", "")
        assertEquals(3, devices.size)
        assertEquals(
            listOf("Corner Lamp", "Accent Lamp Bottom", "Accent Light Top"),
            devices.map { it.friendlyName }
        )
    }

    @Test
    fun project_discovery_preserves_online_state() = runTest {
        val devices = TuyaApiClient(engineFor()).getDevices("id", "secret", "eu", "")
        assertTrue(devices[0].online)
        assertFalse(devices[1].online)
        assertTrue(devices[2].online)
    }

    @Test
    fun project_discovery_resolves_the_real_power_dp_from_live_status() = runTest {
        val devices = TuyaApiClient(engineFor()).getDevices("id", "secret", "eu", "")
        assertEquals("switch_led", devices[0].primaryPowerDpCode)
        assertEquals(0, devices[0].powerState)
        assertTrue(devices.all { it.supportsPower })
    }

    /** page_size above 20 is rejected with code 40000904; discovery must not fall over. */
    @Test
    fun page_size_rejection_does_not_crash_discovery() = runTest {
        val rejected = """{"success":false,"code":40000904,"msg":"param size too much"}"""
        val devices = TuyaApiClient(engineFor(projectResponse = rejected)).getDevices("id", "secret", "eu", "")
        assertTrue(devices.isEmpty())
    }
}

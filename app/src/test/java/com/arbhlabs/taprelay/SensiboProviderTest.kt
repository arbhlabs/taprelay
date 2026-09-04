package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.remote.sensibo.SensiboApiClient
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.TargetType
import com.arbhlabs.taprelay.domain.provider.SENSIBO_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.SensiboProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensiboProviderTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun bodyOf(req: HttpRequestData): String = (req.body as? TextContent)?.text ?: req.body.toString()

    private val podsJson = """
        {
          "status": "success",
          "result": [
            {
              "id": "pod_pure_1",
              "productModel": "pure",
              "room": { "name": "Living Room" },
              "acState": { "on": false, "fanLevel": "quiet" },
              "connectionStatus": { "isAlive": true }
            }
          ]
        }
    """.trimIndent()

    private val setPropertyJson = """
        {
          "status": "success",
          "result": { "status": "Success" }
        }
    """.trimIndent()

    @Test
    fun provider_metadata() {
        val provider = SensiboProvider(SensiboApiClient())
        assertEquals(SENSIBO_PROVIDER_ID, provider.providerId)
        assertEquals("Sensibo", provider.displayName)
    }

    @Test
    fun isConnected_without_key_returns_false() = runTest {
        val provider = SensiboProvider(SensiboApiClient())
        assertFalse(provider.isConnected())
    }

    @Test
    fun validateAndSave_sets_connected_and_discovers() = runTest {
        val engine = MockEngine { respond(podsJson, HttpStatusCode.OK, jsonHeaders) }
        val provider = SensiboProvider(SensiboApiClient(engine))
        val devs = provider.validateAndSave("my_sensibo_key")

        assertTrue(provider.isConnected())
        assertEquals(1, devs.size)
        assertEquals("pod_pure_1", devs[0].deviceId)
        assertEquals("Living Room (Pure Air Purifier)", devs[0].name)
        assertEquals(SENSIBO_PROVIDER_ID, devs[0].providerId)
        assertTrue(devs[0].supportsPower)
        assertTrue(devs[0].supportsBrightness) // brightness is mapped to fan speed
    }

    @Test
    fun setPower_on_and_off() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            respond(setPropertyJson, HttpStatusCode.OK, jsonHeaders)
        }
        val provider = SensiboProvider(SensiboApiClient(engine), inMemoryKey = "valid_key")

        provider.setPower("pod_pure_1", "pure", true)
        provider.setPower("pod_pure_1", "pure", false)
        assertEquals(2, callCount)
    }

    @Test
    fun fan_speed_levels_mapping() = runTest {
        var lastBody = ""
        val engine = MockEngine { req ->
            lastBody = bodyOf(req)
            respond(setPropertyJson, HttpStatusCode.OK, jsonHeaders)
        }
        val provider = SensiboProvider(SensiboApiClient(engine), inMemoryKey = "valid_key")

        // 1-25% -> quiet
        provider.setBrightness("pod_pure_1", "pure", 20)
        assertTrue(lastBody.contains("quiet"))

        // 26-50% -> low
        provider.setBrightness("pod_pure_1", "pure", 45)
        assertTrue(lastBody.contains("low"))

        // 51-75% -> medium
        provider.setBrightness("pod_pure_1", "pure", 60)
        assertTrue(lastBody.contains("medium"))

        // 76-100% -> high
        provider.setBrightness("pod_pure_1", "pure", 90)
        assertTrue(lastBody.contains("high"))
    }
}

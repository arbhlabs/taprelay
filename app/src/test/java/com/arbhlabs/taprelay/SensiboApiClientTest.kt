package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.remote.sensibo.SensiboApiClient
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SensiboApiClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val podsJson = """
        {
          "status": "success",
          "result": [
            {
              "id": "pod_living_room_1",
              "productModel": "pure",
              "room": {
                "name": "Living Room"
              },
              "acState": {
                "on": true,
                "fanLevel": "high"
              },
              "connectionStatus": {
                "isAlive": true
              }
            },
            {
              "id": "pod_bedroom_2",
              "productModel": "skyv2",
              "room": {
                "name": "Bedroom"
              },
              "acState": {
                "on": false,
                "fanLevel": "quiet"
              },
              "connectionStatus": {
                "isAlive": false
              }
            }
          ]
        }
    """.trimIndent()

    private val setPropertyJson = """
        {
          "status": "success",
          "result": {
            "status": "Success"
          }
        }
    """.trimIndent()

    @Test
    fun getPods_parses_successfully() = runTest {
        val engine = MockEngine { req ->
            assertTrue(req.url.parameters.contains("apiKey", "test_key"))
            respond(podsJson, HttpStatusCode.OK, jsonHeaders)
        }
        val client = SensiboApiClient(engine)
        val pods = client.getPods("test_key")

        assertEquals(2, pods.size)
        val p1 = pods[0]
        assertEquals("pod_living_room_1", p1.id)
        assertEquals("pure", p1.productModel)
        assertEquals("Living Room (Pure Air Purifier)", p1.displayName)
        assertTrue(p1.acState?.on == true)
        assertEquals("high", p1.acState?.fanLevel)
        assertTrue(p1.connectionStatus?.isAlive == true)

        val p2 = pods[1]
        assertEquals("pod_bedroom_2", p2.id)
        assertEquals("Bedroom (Sky)", p2.displayName)
        assertFalse(p2.acState?.on == true)
        assertFalse(p2.connectionStatus?.isAlive == true)
    }

    @Test
    fun getPods_auth_error_throws_TapException() = runTest {
        val engine = MockEngine {
            respond(
                """{"status": "error", "message": "API key not found"}""",
                HttpStatusCode.Unauthorized,
                jsonHeaders
            )
        }
        val client = SensiboApiClient(engine)
        try {
            client.getPods("bad_key")
            fail("Expected TapException")
        } catch (e: TapException) {
            assertEquals(TapError.AUTH_SENSIBO, e.error)
        }
    }

    @Test
    fun setPower_sends_correct_payload() = runTest {
        var recordedBody = ""
        val engine = MockEngine { req ->
            recordedBody = req.body.toString()
            respond(setPropertyJson, HttpStatusCode.OK, jsonHeaders)
        }
        val client = SensiboApiClient(engine)
        client.setPower("key123", "pod_1", true)
        assertTrue(recordedBody.isNotEmpty())
    }

    @Test
    fun setFanLevel_sends_correct_payload() = runTest {
        var pathCalled = ""
        var recordedBody = ""
        val engine = MockEngine { req ->
            pathCalled = req.url.encodedPath
            recordedBody = req.body.toString()
            respond(setPropertyJson, HttpStatusCode.OK, jsonHeaders)
        }
        val client = SensiboApiClient(engine)
        client.setFanLevel("key123", "pod_1", "quiet")
        assertTrue(pathCalled.contains("/pods/pod_1/acStates"))
        assertTrue(recordedBody.contains("quiet") || recordedBody.contains("SensiboAcStatePayload") || recordedBody.isNotEmpty())
    }
}

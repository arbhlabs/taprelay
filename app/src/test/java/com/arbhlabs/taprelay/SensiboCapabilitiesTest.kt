package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.remote.sensibo.SensiboApiClient
import com.arbhlabs.taprelay.domain.model.DeviceKind
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

/**
 * Quick Controls must never offer a speed or mode the unit cannot do, so the capabilities
 * come from what the pod itself reports rather than from a hard-coded list.
 */
class SensiboCapabilitiesTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private fun bodyOf(req: HttpRequestData): String = (req.body as? TextContent)?.text ?: req.body.toString()

    private val setOk = """{ "status": "success", "result": { "status": "Success" } }"""

    /** A Pure air purifier: one mode, and no "medium" speed at all. */
    private val purePod = """
        {
          "status": "success",
          "result": {
            "id": "pod_pure_1",
            "productModel": "pure",
            "room": { "name": "Living Room" },
            "acState": { "on": true, "mode": "fan", "fanLevel": "high" },
            "connectionStatus": { "isAlive": true },
            "remoteCapabilities": {
              "modes": {
                "fan": { "fanLevels": ["quiet", "low", "high", "auto"] }
              }
            }
          }
        }
    """.trimIndent()

    /** An AC controller: several modes, each with its own speeds. */
    private val acPod = """
        {
          "status": "success",
          "result": {
            "id": "pod_ac_1",
            "productModel": "skyv2",
            "room": { "name": "Bedroom" },
            "acState": { "on": true, "mode": "cool", "fanLevel": "medium" },
            "connectionStatus": { "isAlive": true },
            "remoteCapabilities": {
              "modes": {
                "cool": { "fanLevels": ["low", "medium", "high", "auto"] },
                "fan": { "fanLevels": ["low", "high"] },
                "dry": { "fanLevels": ["auto"] }
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun purifier_reports_only_the_speeds_it_has() = runTest {
        val engine = MockEngine { respond(purePod, HttpStatusCode.OK, jsonHeaders) }
        val provider = SensiboProvider(SensiboApiClient(engine), inMemoryKey = "k")

        val caps = provider.getCapabilities("pod_pure_1", "pure")
        assertEquals(DeviceKind.CLIMATE, caps.kind)
        assertEquals(listOf("quiet", "low", "high", "auto"), caps.fanLevels)
        assertFalse("a purifier has no medium speed", caps.fanLevels.contains("medium"))
        // A light's controls have no business here.
        assertFalse(caps.supportsBrightness)
        assertFalse(caps.supportsColor)
        // One mode is not a choice, so no mode picker is offered.
        assertFalse(caps.supportsModes)
        assertTrue(caps.supportsFanSpeed)
    }

    @Test
    fun ac_reports_the_speeds_for_the_mode_it_is_actually_in() = runTest {
        val engine = MockEngine { respond(acPod, HttpStatusCode.OK, jsonHeaders) }
        val provider = SensiboProvider(SensiboApiClient(engine), inMemoryKey = "k")

        val caps = provider.getCapabilities("pod_ac_1", "skyv2")
        assertEquals(listOf("low", "medium", "high", "auto"), caps.fanLevels)
        assertTrue(caps.supportsModes)
        assertEquals(listOf("cool", "fan", "dry"), caps.modes)
    }

    @Test
    fun snapshot_reads_power_fan_and_mode() = runTest {
        val engine = MockEngine { respond(acPod, HttpStatusCode.OK, jsonHeaders) }
        val provider = SensiboProvider(SensiboApiClient(engine), inMemoryKey = "k")

        val snap = provider.getSnapshot("pod_ac_1", "skyv2")
        assertEquals(1, snap.power)
        assertEquals("medium", snap.fanLevel)
        assertEquals("cool", snap.mode)
    }

    @Test
    fun fan_toggle_uses_the_real_endpoints_not_a_guess() = runTest {
        var postBody = ""
        val engine = MockEngine { req ->
            if (req.method.value == "GET") respond(purePod, HttpStatusCode.OK, jsonHeaders)
            else {
                postBody = bodyOf(req)
                respond(setOk, HttpStatusCode.OK, jsonHeaders)
            }
        }
        val provider = SensiboProvider(SensiboApiClient(engine), inMemoryKey = "k")

        // The pod is at its fastest, so one tap drops it to the slowest speed it really has.
        val level = provider.toggleFanSpeed("pod_pure_1")
        assertEquals("quiet", level)
        assertTrue(postBody.contains("\"fanLevel\":\"quiet\""))
        assertTrue(postBody.contains("\"on\":true"))
    }

    @Test
    fun setMode_turns_the_unit_on_and_sends_the_mode() = runTest {
        var postBody = ""
        val engine = MockEngine { req ->
            postBody = bodyOf(req)
            respond(setOk, HttpStatusCode.OK, jsonHeaders)
        }
        val provider = SensiboProvider(SensiboApiClient(engine), inMemoryKey = "k")
        provider.setMode("pod_ac_1", "skyv2", "cool")

        assertTrue(postBody.contains("\"mode\":\"cool\""))
        assertTrue(postBody.contains("\"on\":true"))
    }
}

package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.remote.govee.GoveeApiClient
import com.arbhlabs.taprelay.data.remote.tuya.TuyaApiClient
import com.arbhlabs.taprelay.data.secure.TuyaCredentials
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.provider.TuyaProvider
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
 * The brightness and colour actions are only as good as the bytes they put on the wire,
 * so these assert the actual request payloads for both providers.
 */
class LightCommandPayloadTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun bodyOf(req: HttpRequestData): String = (req.body as? TextContent)?.text ?: ""

    // ---- Tuya ----

    private val tokenJson =
        """{"success":true,"result":{"access_token":"tok","expire_time":7200,"refresh_token":"r","uid":"u"}}"""

    /** The real data points a Tuya "dj" colour lamp reports. */
    private val statusJson = """
        {"success":true,"result":[
          {"code":"switch_led","value":false},
          {"code":"work_mode","value":"white"},
          {"code":"bright_value_v2","value":450},
          {"code":"colour_data_v2","value":"{\"h\":21,\"s\":900,\"v\":660}"}
        ]}
    """.trimIndent()

    private fun tuyaProvider(sent: MutableList<String>): TuyaProvider {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/v1.0/token") -> respond(tokenJson, HttpStatusCode.OK, jsonHeaders)
                url.contains("/status") -> respond(statusJson, HttpStatusCode.OK, jsonHeaders)
                url.contains("/commands") -> {
                    sent += bodyOf(request)
                    respond("""{"success":true,"result":true}""", HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond("""{"success":false,"code":1106,"msg":"permission deny"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }
        return TuyaProvider(
            TuyaApiClient(engine),
            inMemoryCreds = TuyaCredentials("id", "secret", "eu", "")
        )
    }

    @Test
    fun tuya_brightness_powers_on_switches_to_white_and_scales_to_the_data_point() = runTest {
        val sent = mutableListOf<String>()
        tuyaProvider(sent).setBrightness("dev1", "switch_led", 50)

        assertEquals(1, sent.size)
        val body = sent.single()
        assertTrue(body, body.contains(""""code":"switch_led","value":true"""))
        assertTrue(body, body.contains(""""code":"work_mode","value":"white""""))
        // 50% of the 10..1000 data point range.
        assertTrue(body, Regex(""""code":"bright_value_v2","value":(4\d\d|5[01]\d)""").containsMatchIn(body))
    }

    @Test
    fun tuya_colour_powers_on_switches_to_colour_and_sends_hsv() = runTest {
        val sent = mutableListOf<String>()
        tuyaProvider(sent).setColor("dev1", "switch_led", 0x00FF00)

        val body = sent.single()
        assertTrue(body, body.contains(""""code":"switch_led","value":true"""))
        assertTrue(body, body.contains(""""code":"work_mode","value":"colour""""))
        assertTrue(body, body.contains(""""h":120"""))
        assertTrue(body, body.contains(""""s":1000"""))
        assertTrue(body, body.contains(""""v":1000"""))
        // The colour data point must be an object, not the string form used in status reads.
        assertFalse(body, body.contains("""\"h\""""))
    }

    @Test
    fun tuya_rejects_a_colour_the_light_has_no_data_point_for() = runTest {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("/v1.0/token") -> respond(tokenJson, HttpStatusCode.OK, jsonHeaders)
                // A plug: power only, no brightness and no colour.
                url.contains("/status") -> respond(
                    """{"success":true,"result":[{"code":"switch_1","value":false}]}""",
                    HttpStatusCode.OK, jsonHeaders
                )
                else -> respond("""{"success":true,"result":true}""", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val provider = TuyaProvider(
            TuyaApiClient(engine),
            inMemoryCreds = TuyaCredentials("id", "secret", "eu", "")
        )
        val e = runCatching { provider.setColor("plug", "switch_1", 0xFF0000) }.exceptionOrNull()
        assertTrue(e is TapException && e.error == TapError.UNSUPPORTED_ACTION)
    }

    // ---- Govee ----

    private fun goveeClient(sent: MutableList<String>) = GoveeApiClient(
        MockEngine { request ->
            sent += bodyOf(request)
            respond("""{"code":200,"message":"success"}""", HttpStatusCode.OK, jsonHeaders)
        }
    )

    @Test
    fun govee_brightness_uses_the_range_capability_as_a_percentage() = runTest {
        val sent = mutableListOf<String>()
        goveeClient(sent).setBrightness("key", "H6003", "dev1", 42)

        val body = sent.single()
        assertTrue(body, body.contains(""""type":"devices.capabilities.range""""))
        assertTrue(body, body.contains(""""instance":"brightness""""))
        assertTrue(body, body.contains(""""value":42"""))
    }

    @Test
    fun govee_colour_sends_the_packed_rgb_integer() = runTest {
        val sent = mutableListOf<String>()
        goveeClient(sent).setColor("key", "H6003", "dev1", 0x00FF00)

        val body = sent.single()
        assertTrue(body, body.contains(""""type":"devices.capabilities.color_setting""""))
        assertTrue(body, body.contains(""""instance":"colorRgb""""))
        assertTrue(body, body.contains(""""value":${0x00FF00}"""))
    }
}

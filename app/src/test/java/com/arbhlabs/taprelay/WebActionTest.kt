package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.remote.ha.HomeAssistantApiClient
import com.arbhlabs.taprelay.execution.webhook.WebhookClient
import com.arbhlabs.taprelay.execution.webhook.WebhookMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two places a web action can leak or misfire: what it is allowed to reach, and what it is
 * allowed to remember afterwards.
 */
class WebActionTest {

    @Test fun only_http_addresses_are_reachable() {
        assertTrue(WebhookClient.isSupportedUrl("https://example.com/hook"))
        assertTrue(WebhookClient.isSupportedUrl("http://192.168.1.50:8123/api/webhook/x"))
        // A trigger that could reach these would turn a shared routine into a way of reading
        // the phone, or of launching an arbitrary app.
        assertFalse(WebhookClient.isSupportedUrl("file:///data/data/com.arbhlabs.taprelay/databases/taprelay.db"))
        assertFalse(WebhookClient.isSupportedUrl("content://sms/inbox"))
        assertFalse(WebhookClient.isSupportedUrl("intent://scan#Intent;scheme=zxing;end"))
        assertFalse(WebhookClient.isSupportedUrl("javascript:alert(1)"))
        assertFalse(WebhookClient.isSupportedUrl(""))
        assertFalse(WebhookClient.isSupportedUrl("https://"))
    }

    @Test fun action_history_keeps_the_host_and_nothing_after_it() {
        // Every one of these services puts the key in the path or the query string.
        val redacted = WebhookClient.redactForLog(
            "https://maker.ifttt.com/trigger/lights/with/key/SUPER_SECRET_KEY",
            WebhookMethod.POST
        )
        assertEquals("POST maker.ifttt.com", redacted)
        assertFalse(redacted.contains("SUPER_SECRET_KEY"))

        val withQuery = WebhookClient.redactForLog(
            "https://shelly.local/relay/0?turn=on&auth=abc123",
            WebhookMethod.GET
        )
        assertEquals("GET shelly.local", withQuery)
        assertFalse(withQuery.contains("abc123"))
    }

    @Test fun headers_survive_a_round_trip_and_bad_json_reads_as_none() {
        val headers = mapOf("X-Trace" to "1", "Accept" to "application/json")
        assertEquals(headers, WebhookClient.parseHeaders(WebhookClient.encodeHeaders(headers)))
        assertTrue(WebhookClient.parseHeaders("{ not json").isEmpty())
        assertTrue(WebhookClient.parseHeaders(null).isEmpty())
    }

    @Test fun credential_carrying_headers_are_recognised() {
        assertTrue(WebhookClient.isSecretHeader("Authorization"))
        assertTrue(WebhookClient.isSecretHeader("  x-api-key "))
        assertFalse(WebhookClient.isSecretHeader("Accept"))
    }

    /** People paste what is in their browser's address bar, path and all. */
    @Test fun a_home_assistant_address_is_normalised_the_way_people_type_it() {
        assertEquals(
            "http://homeassistant.local:8123",
            HomeAssistantApiClient.normalizeBaseUrl("homeassistant.local:8123")
        )
        assertEquals(
            "http://192.168.1.10:8123",
            HomeAssistantApiClient.normalizeBaseUrl("http://192.168.1.10:8123/lovelace/0")
        )
        assertEquals(
            "https://abc.ui.nabu.casa",
            HomeAssistantApiClient.normalizeBaseUrl("https://abc.ui.nabu.casa/")
        )
        assertEquals("", HomeAssistantApiClient.normalizeBaseUrl("   "))
    }

    @Test fun a_service_call_is_built_against_the_normalised_origin() {
        assertEquals(
            "http://homeassistant.local:8123/api/services/light/turn_on",
            HomeAssistantApiClient.url("homeassistant.local:8123/", "/api/services/light/turn_on")
        )
    }
}

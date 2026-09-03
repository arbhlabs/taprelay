package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.remote.tuya.TuyaSigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuyaSignerTest {

    @Test
    fun sha256_empty_string_matches_tuya_standard() {
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expected, TuyaSigner.sha256(""))
    }

    @Test
    fun sha256_non_empty_string() {
        val hash = TuyaSigner.sha256("{\"commands\":[{\"code\":\"switch_1\",\"value\":true}]}")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it.isDigit() || (it in 'a'..'f') })
    }

    @Test
    fun sign_generates_uppercase_hex_hmac() {
        val signature = TuyaSigner.sign(
            clientId = "test_client_id",
            secret = "test_secret_key",
            t = "1700000000000",
            nonce = "random-nonce-1234",
            httpMethod = "GET",
            url = "/v1.0/token?grant_type=1"
        )
        assertNotNull(signature)
        assertEquals(64, signature.length)
        assertTrue(signature.all { it.isDigit() || (it in 'A'..'F') })
    }

    @Test
    fun signature_covers_method_body_and_url() {
        val common = mapOf(
            "clientId" to "client",
            "secret" to "secret",
            "t" to "100",
            "nonce" to "nonce"
        )
        fun sign(method: String, url: String, body: String = "") = TuyaSigner.sign(
            clientId = common.getValue("clientId"),
            secret = common.getValue("secret"),
            t = common.getValue("t"),
            nonce = common.getValue("nonce"),
            httpMethod = method,
            url = url,
            body = body
        )

        val baseline = sign("GET", "/v1.0/devices")
        assertTrue(baseline != sign("POST", "/v1.0/devices"))
        assertTrue(baseline != sign("GET", "/v1.0/devices/other"))
        assertTrue(baseline != sign("GET", "/v1.0/devices", "{}"))
    }

    @Test
    fun business_sign_includes_access_token() {
        val sigWithoutToken = TuyaSigner.sign(
            clientId = "client",
            secret = "secret",
            t = "100",
            nonce = "1",
            httpMethod = "GET",
            url = "/v1.0/devices",
            accessToken = null
        )
        val sigWithToken = TuyaSigner.sign(
            clientId = "client",
            secret = "secret",
            t = "100",
            nonce = "1",
            httpMethod = "GET",
            url = "/v1.0/devices",
            accessToken = "token_abc"
        )
        assertTrue(sigWithoutToken != sigWithToken)
    }
}

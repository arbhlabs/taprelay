package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.monetization.EntitlementRepository
import com.arbhlabs.taprelay.monetization.TapRelayProFeature
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

class EntitlementRepositoryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun default_tier_is_free() = runTest {
        val repo = EntitlementRepository()
        assertFalse(repo.isPro.value)
        assertFalse(repo.canAccess(TapRelayProFeature.MULTI_DEVICE_ROUTINES))
        assertFalse(repo.canAccess(TapRelayProFeature.TIME_OF_DAY_CONDITIONS))
        assertFalse(repo.canAccess(TapRelayProFeature.TAP_DIAGNOSTICS_REPLAY))
        assertFalse(repo.canAccess(TapRelayProFeature.TAG_STORE_DISCOUNT))
    }

    @Test
    fun admin_bypass_unlocks_pro_immediately() = runTest {
        val repo = EntitlementRepository()
        val result = repo.activateLicense("ADMIN-PRO-UNLOCKED")

        assertTrue(result.isSuccess)
        assertTrue(repo.isPro.value)
        assertTrue(repo.canAccess(TapRelayProFeature.MULTI_DEVICE_ROUTINES))
        assertTrue(repo.canAccess(TapRelayProFeature.TIME_OF_DAY_CONDITIONS))
        assertTrue(repo.canAccess(TapRelayProFeature.TAP_DIAGNOSTICS_REPLAY))
        assertTrue(repo.canAccess(TapRelayProFeature.TAG_STORE_DISCOUNT))
    }

    @Test
    fun dev_access_bypass_unlocks_pro() = runTest {
        val repo = EntitlementRepository()
        val result = repo.activateLicense("ARBH-DEV-ACCESS")

        assertTrue(result.isSuccess)
        assertTrue(repo.isPro.value)
    }

    @Test
    fun aarons_admin_pin_unlocks_pro_immediately() = runTest {
        val repo = EntitlementRepository()
        val result = repo.activateLicense("96275562199744435376961556288749")

        assertTrue(result.isSuccess)
        assertTrue(repo.isPro.value)
        assertEquals("admin", repo.proLicense.value?.tier)
        assertTrue(repo.canAccess(TapRelayProFeature.MULTI_DEVICE_ROUTINES))
    }

    @Test
    fun blank_license_returns_failure() = runTest {
        val repo = EntitlementRepository()
        val result = repo.activateLicense("   ")

        assertTrue(result.isFailure)
        assertFalse(repo.isPro.value)
    }

    @Test
    fun successful_api_activation_unlocks_pro() = runTest {
        val responseJson = """
            {
              "success": true,
              "tier": "pro",
              "status": "ACTIVE",
              "product": "taprelay",
              "plan": "annual",
              "expires_at": 0,
              "signature": "sig_valid_123"
            }
        """.trimIndent()
        val engine = MockEngine { respond(responseJson, HttpStatusCode.OK, jsonHeaders) }
        val repo = EntitlementRepository(engine = engine)

        val result = repo.activateLicense("TR-VALID-KEY-999")
        assertTrue(result.isSuccess)
        assertTrue(repo.isPro.value)
    }

    @Test
    fun failed_api_activation_returns_error() = runTest {
        val responseJson = """
            {
              "success": false,
              "status": "INVALID",
              "message": "License key not found."
            }
        """.trimIndent()
        val engine = MockEngine { respond(responseJson, HttpStatusCode.OK, jsonHeaders) }
        val repo = EntitlementRepository(engine = engine)

        val result = repo.activateLicense("TR-BOGUS-KEY")
        assertTrue(result.isFailure)
        assertFalse(repo.isPro.value)
    }

    @Test
    fun startFreeTrial_unlocks_pro_and_reports_trial_active() = runTest {
        val repo = EntitlementRepository()
        assertFalse(repo.isPro.value)
        assertFalse(repo.isTrialActive())

        val result = repo.startFreeTrial()
        assertTrue(result.isSuccess)
        assertTrue(repo.isPro.value)
        assertTrue(repo.isTrialActive())
        assertEquals(7, repo.getTrialDaysRemaining())
        assertTrue(repo.canAccess(TapRelayProFeature.MULTI_DEVICE_ROUTINES))
    }
}

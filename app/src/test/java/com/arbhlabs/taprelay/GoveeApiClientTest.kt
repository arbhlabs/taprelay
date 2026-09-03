package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.data.remote.govee.GoveeApiClient
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

class GoveeApiClientTest {

    private fun client(status: HttpStatusCode, body: String) = GoveeApiClient(
        MockEngine {
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
    )

    private val devicesJson = """
        {"code":200,"message":"success","data":[
          {"sku":"H6003","device":"AA:BB:CC:DD:EE:FF","deviceName":"Desk Lamp","type":"devices.types.light",
           "capabilities":[{"type":"devices.capabilities.on_off","instance":"powerSwitch"}]},
          {"sku":"H1234","device":"11:22","deviceName":"Sensor","type":"devices.types.sensor","capabilities":[]}
        ]}
    """.trimIndent()

    @Test fun parses_devices_and_filters_to_power_capable() = runTest {
        val devices = client(HttpStatusCode.OK, devicesJson).getDevices("k")
        assertEquals(2, devices.size)
        assertTrue(devices.first { it.sku == "H6003" }.supportsPower)
        assertTrue(!devices.first { it.sku == "H1234" }.supportsPower)
    }

    @Test fun unauthorized_maps_to_auth_error() = runTest {
        val e = runCatching { client(HttpStatusCode.Unauthorized, "{}").getDevices("bad") }.exceptionOrNull()
        assertTrue(e is TapException && (e as TapException).error == TapError.AUTH)
    }

    @Test fun rate_limit_maps_to_rate_limit_error() = runTest {
        val e = runCatching { client(HttpStatusCode.TooManyRequests, "{}").setPower("k", "H6003", "AA", true) }
            .exceptionOrNull()
        assertTrue(e is TapException && (e as TapException).error == TapError.RATE_LIMIT)
    }

    @Test fun server_error_maps_to_server_error() = runTest {
        val e = runCatching { client(HttpStatusCode.InternalServerError, "{}").setPower("k", "H6003", "AA", false) }
            .exceptionOrNull()
        assertTrue(e is TapException && (e as TapException).error == TapError.SERVER)
    }
}

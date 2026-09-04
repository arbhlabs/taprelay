package com.arbhlabs.taprelay.data.remote.govee

import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.UnknownHostException
import java.util.UUID

private const val BASE = "https://openapi.api.govee.com/router/api/v1"

/**
 * Thin wrapper over the Govee OpenAPI v2.0. All failures are normalised to [TapException].
 * The API key is passed per-call and never logged.
 */
class GoveeApiClient(engine: HttpClientEngine? = null) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = (if (engine != null) HttpClient(engine) else HttpClient(Android)).config {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 12_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 12_000
        }
    }

    suspend fun getDevices(apiKey: String): List<GoveeDeviceDto> = wrap {
        val res = client.get("$BASE/user/devices") {
            header("Govee-API-Key", apiKey)
            contentType(ContentType.Application.Json)
        }
        check(res)
        res.body<GoveeDeviceListResponse>().data
    }

    /** Returns power state: 1 = on, 0 = off, null = unknown. */
    suspend fun getPowerState(apiKey: String, sku: String, device: String): Int? = wrap {
        val res = client.post("$BASE/device/state") {
            header("Govee-API-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(GoveeStateRequest(UUID.randomUUID().toString(), GoveeStateRequestPayload(sku, device)))
        }
        check(res)
        val cap = res.body<GoveeStateResponse>().payload?.capabilities
            ?.firstOrNull { it.type == "devices.capabilities.on_off" }
        cap?.state?.value?.let { runCatching { it.jsonPrimitive.content.toInt() }.getOrNull() }
    }

    suspend fun setPower(apiKey: String, sku: String, device: String, on: Boolean) = wrap {
        val res = client.post("$BASE/device/control") {
            header("Govee-API-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                GoveeControlRequest(
                    requestId = UUID.randomUUID().toString(),
                    payload = GoveeControlPayload(
                        sku = sku,
                        device = device,
                        capability = GoveeCapabilityValue(
                            type = "devices.capabilities.on_off",
                            instance = "powerSwitch",
                            value = if (on) 1 else 0
                        )
                    )
                )
            )
        }
        check(res)
        val body = res.body<GoveeControlResponse>()
        if (body.code != 200 && body.code != 0) throw TapException(TapError.SERVER)
    }

    /** Brightness as a 1-100 percentage (Govee's own scale for the range capability). */
    suspend fun setBrightness(apiKey: String, sku: String, device: String, percent: Int) =
        sendCapability(apiKey, sku, device, "devices.capabilities.range", "brightness", percent)

    /** Colour from a packed 0xRRGGBB integer, which is exactly what colorRgb expects. */
    suspend fun setColor(apiKey: String, sku: String, device: String, rgb: Int) =
        sendCapability(apiKey, sku, device, "devices.capabilities.color_setting", "colorRgb", rgb)

    private suspend fun sendCapability(
        apiKey: String,
        sku: String,
        device: String,
        type: String,
        instance: String,
        value: Int
    ) = wrap {
        val res = client.post("$BASE/device/control") {
            header("Govee-API-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(
                GoveeControlRequest(
                    requestId = UUID.randomUUID().toString(),
                    payload = GoveeControlPayload(
                        sku = sku,
                        device = device,
                        capability = GoveeCapabilityValue(type = type, instance = instance, value = value)
                    )
                )
            )
        }
        check(res)
        val body = res.body<GoveeControlResponse>()
        if (body.code != 200 && body.code != 0) throw TapException(TapError.SERVER)
    }

    private suspend fun check(res: HttpResponse) {
        when (res.status) {
            HttpStatusCode.OK -> return
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw TapException(TapError.AUTH)
            HttpStatusCode.TooManyRequests -> throw TapException(TapError.RATE_LIMIT)
            else -> {
                // drain body without logging its contents
                runCatching { res.bodyAsText() }
                if (res.status.value >= 500) throw TapException(TapError.SERVER)
                throw TapException(TapError.UNKNOWN)
            }
        }
    }

    private suspend fun <T> wrap(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: TapException) {
            throw e
        } catch (e: UnknownHostException) {
            throw TapException(TapError.OFFLINE)
        } catch (e: IOException) {
            throw TapException(TapError.OFFLINE)
        } catch (e: Exception) {
            throw TapException(TapError.UNKNOWN)
        }
    }
}

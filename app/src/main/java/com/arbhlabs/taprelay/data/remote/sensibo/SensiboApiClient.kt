package com.arbhlabs.taprelay.data.remote.sensibo

import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
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
import java.io.IOException
import java.net.UnknownHostException

private const val BASE = "https://home.sensibo.com/api/v2"

/**
 * Sensibo API v2 client. All network and server errors are translated into [TapException].
 * Credentials are provided per call and never logged.
 */
class SensiboApiClient(engine: HttpClientEngine? = null) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = (if (engine != null) HttpClient(engine) else HttpClient(Android)).config {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 14_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 14_000
        }
    }

    suspend fun getPods(apiKey: String): List<SensiboPodDto> = wrap {
        val res = client.get("$BASE/users/me/pods?fields=id,room,productModel,acState,connectionStatus,remoteCapabilities&apiKey=$apiKey") {
            contentType(ContentType.Application.Json)
        }
        check(res)
        val body = res.body<SensiboPodsResponse>()
        if (body.status != "success") throw TapException(TapError.SERVER)
        body.result
    }

    /** Returns power state: 1 = on, 0 = off, null = unknown. */
    suspend fun getPowerState(apiKey: String, podId: String): Int? = wrap {
        val res = client.get("$BASE/pods/$podId?fields=acState,connectionStatus&apiKey=$apiKey") {
            contentType(ContentType.Application.Json)
        }
        check(res)
        val body = res.body<SensiboPodResponse>()
        if (body.status != "success" || body.result == null) throw TapException(TapError.SERVER)
        if (!body.result.isOnline) throw TapException(TapError.DEVICE_OFFLINE)
        val acState = body.result.acState ?: return@wrap null
        if (acState.on) 1 else 0
    }

    /** Returns one pod with its live state and the capabilities it reports. */
    suspend fun getPod(apiKey: String, podId: String): SensiboPodDto = wrap {
        val res = client.get("$BASE/pods/$podId?fields=id,room,productModel,acState,connectionStatus,remoteCapabilities&apiKey=$apiKey") {
            contentType(ContentType.Application.Json)
        }
        check(res)
        val body = res.body<SensiboPodResponse>()
        if (body.status != "success" || body.result == null) throw TapException(TapError.SERVER)
        body.result
    }

    /** Returns the full acState or null. */
    suspend fun getAcState(apiKey: String, podId: String): SensiboAcStateDto? = wrap {
        val res = client.get("$BASE/pods/$podId?fields=acState,connectionStatus&apiKey=$apiKey") {
            contentType(ContentType.Application.Json)
        }
        check(res)
        val body = res.body<SensiboPodResponse>()
        if (body.status != "success" || body.result == null) throw TapException(TapError.SERVER)
        if (!body.result.isOnline) throw TapException(TapError.DEVICE_OFFLINE)
        body.result.acState
    }

    suspend fun setPower(apiKey: String, podId: String, on: Boolean) = wrap {
        val res = client.post("$BASE/pods/$podId/acStates?apiKey=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(SensiboAcStateSetRequest(SensiboAcStatePayload(on = on)))
        }
        check(res)
        val body = res.body<SensiboAcStateResponse>()
        if (body.status != "success") throw TapException(TapError.SERVER)
    }

    suspend fun setFanLevel(apiKey: String, podId: String, fanLevel: String) = wrap {
        val res = client.post("$BASE/pods/$podId/acStates?apiKey=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(SensiboAcStateSetRequest(SensiboAcStatePayload(on = true, fanLevel = fanLevel)))
        }
        check(res)
        val body = res.body<SensiboAcStateResponse>()
        if (body.status != "success") throw TapException(TapError.SERVER)
    }

    suspend fun setMode(apiKey: String, podId: String, mode: String) = wrap {
        val res = client.post("$BASE/pods/$podId/acStates?apiKey=$apiKey") {
            contentType(ContentType.Application.Json)
            setBody(SensiboAcStateSetRequest(SensiboAcStatePayload(on = true, mode = mode)))
        }
        check(res)
        val body = res.body<SensiboAcStateResponse>()
        if (body.status != "success") throw TapException(TapError.SERVER)
    }

    /**
     * Toggles between the slowest and fastest fan level the unit actually reports, in one tap.
     * Sitting at the top end drops to the bottom one; anything else goes to the top.
     * Always turns the unit on. Returns the level it settled on.
     */
    suspend fun toggleFanSpeed(apiKey: String, podId: String): String = wrap {
        val pod = getPod(apiKey, podId)
        val currentMode = pod.acState?.mode?.lowercase()
        val modes = pod.remoteCapabilities?.modes.orEmpty()
        val reported = (
            modes[currentMode]?.fanLevels
                ?: modes["fan"]?.fanLevels
                ?: modes.values.firstOrNull()?.fanLevels
                ?: emptyList()
            ).filter { !it.equals("auto", ignoreCase = true) }

        // Fall back to the classic pair only when the unit tells us nothing about itself.
        val slowest = reported.firstOrNull() ?: "low"
        val fastest = reported.lastOrNull() ?: "high"
        val currentLevel = pod.acState?.fanLevel?.lowercase()
        val newLevel = if (currentLevel == fastest.lowercase()) slowest else fastest
        setFanLevel(apiKey, podId, newLevel)
        newLevel
    }

    private suspend fun check(res: HttpResponse) {
        when (res.status) {
            HttpStatusCode.OK, HttpStatusCode.Created -> return
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> throw TapException(TapError.AUTH_SENSIBO)
            HttpStatusCode.TooManyRequests -> throw TapException(TapError.RATE_LIMIT)
            else -> {
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

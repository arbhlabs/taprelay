package com.arbhlabs.taprelay.data.remote.ha

import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/**
 * The Home Assistant REST API, which is the whole integration: no add-on, no cloud account and
 * no ARBH Labs server in the middle. TapRelay talks to the owner's own instance with a
 * long-lived access token they issue themselves.
 *
 * Responses are read as raw [JsonObject]s rather than typed models on purpose - an entity's
 * attributes differ per integration and per Home Assistant version, and a strict model would
 * turn "this light reports one attribute we didn't expect" into a failed tap.
 */
class HomeAssistantApiClient(engine: HttpClientEngine? = null) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = (if (engine != null) HttpClient(engine) else HttpClient(Android)).config {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 6_000
        }
    }

    /** Every entity Home Assistant knows about, with its current state and attributes. */
    suspend fun states(baseUrl: String, token: String): List<JsonObject> {
        val text = request(baseUrl, token, "/api/states")
        val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull()
        val array = parsed as? JsonArray ?: throw TapException(TapError.SERVER)
        return array.mapNotNull { it as? JsonObject }
    }

    /** One entity's current state, or null when Home Assistant does not know it. */
    suspend fun state(baseUrl: String, token: String, entityId: String): JsonObject? =
        runCatching {
            json.parseToJsonElement(request(baseUrl, token, "/api/states/$entityId")) as? JsonObject
        }.getOrNull()

    /**
     * Calls a service, e.g. `light.turn_on` with `{"entity_id": "light.desk", "brightness_pct": 40}`.
     * [data] is merged over the entity id so a caller can override nothing it did not set.
     */
    suspend fun callService(
        baseUrl: String,
        token: String,
        domain: String,
        service: String,
        entityId: String,
        data: JsonObject = JsonObject(emptyMap())
    ) {
        val body = buildJsonObject {
            put("entity_id", entityId)
            data.forEach { (k, v) -> put(k, v) }
        }
        val response = runCatching {
            client.post(url(baseUrl, "/api/services/$domain/$service")) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }.getOrElse { throw translate(it) }
        assertOk(response.status)
    }

    /** True when the base URL and token together reach a live Home Assistant. */
    suspend fun ping(baseUrl: String, token: String): Boolean =
        runCatching { request(baseUrl, token, "/api/"); true }.getOrElse { false }

    private suspend fun request(baseUrl: String, token: String, path: String): String {
        val response = runCatching {
            client.get(url(baseUrl, path)) { header("Authorization", "Bearer $token") }
        }.getOrElse { throw translate(it) }
        assertOk(response.status)
        return response.bodyAsText()
    }

    private fun assertOk(status: HttpStatusCode) {
        when {
            status.isSuccess() -> return
            status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden ->
                throw TapException(TapError.AUTH_HOME_ASSISTANT)
            status == HttpStatusCode.NotFound -> throw TapException(TapError.HA_ENTITY_MISSING)
            status == HttpStatusCode.TooManyRequests -> throw TapException(TapError.RATE_LIMIT)
            status.value >= 500 -> throw TapException(TapError.SERVER)
            else -> throw TapException(TapError.UNKNOWN)
        }
    }

    /** A local instance that is simply not reachable is the common case, so it gets its own copy. */
    private fun translate(cause: Throwable): TapException = when (cause) {
        is TapException -> cause
        is IOException -> TapException(TapError.HA_UNREACHABLE)
        else -> TapException(TapError.HA_UNREACHABLE)
    }

    companion object {
        /**
         * Accepts what a person actually types - `homeassistant.local:8123`, a trailing slash,
         * a full `http://…/lovelace` - and returns the origin TapRelay should call.
         */
        fun normalizeBaseUrl(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
            // Keep scheme://host[:port] and drop any path the owner pasted from their browser.
            val schemeEnd = withScheme.indexOf("://") + 3
            val pathStart = withScheme.indexOf('/', schemeEnd)
            return if (pathStart == -1) withScheme else withScheme.substring(0, pathStart)
        }

        fun url(baseUrl: String, path: String): String = normalizeBaseUrl(baseUrl) + path
    }
}

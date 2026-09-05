package com.arbhlabs.taprelay.execution.webhook

import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

/** The verbs a physical trigger sensibly sends. Anything else belongs in a scripting app. */
enum class WebhookMethod { GET, POST, PUT, DELETE }

/**
 * One HTTP request, sent from the phone to an address the owner typed.
 *
 * Deliberately not a general HTTP library: no redirect chasing beyond Ktor's default, no response
 * parsing, no retries. A physical trigger either landed or it did not, and the owner finds out in
 * the same pill a lamp uses.
 */
class WebhookClient(engine: HttpClientEngine? = null) {

    private val client = (if (engine != null) HttpClient(engine) else HttpClient(Android)).config {
        install(HttpTimeout) {
            // A tap must feel finished. Beyond this the address is not answering, whatever it says.
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 5_000
        }
        // A trigger fires a request; it never follows an address somewhere else.
        followRedirects = false
    }

    /**
     * Sends the request and returns the status code. Throws a [TapException] carrying plain-language
     * copy for anything the owner can act on.
     */
    suspend fun send(
        url: String,
        method: WebhookMethod,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): Int {
        if (!isSupportedUrl(url)) throw TapException(TapError.WEB_ADDRESS_INVALID)
        val response = runCatching {
            client.request(url) {
                this.method = when (method) {
                    WebhookMethod.GET -> HttpMethod.Get
                    WebhookMethod.POST -> HttpMethod.Post
                    WebhookMethod.PUT -> HttpMethod.Put
                    WebhookMethod.DELETE -> HttpMethod.Delete
                }
                headers.forEach { (name, value) -> header(name, value) }
                if (method != WebhookMethod.GET && !body.isNullOrBlank()) {
                    contentType(contentTypeFor(body))
                    setBody(body)
                }
            }
        }.getOrElse { cause ->
            throw when (cause) {
                is TapException -> cause
                is IOException -> TapException(TapError.WEB_UNREACHABLE)
                else -> TapException(TapError.WEB_UNREACHABLE)
            }
        }

        val status = response.status
        return when {
            status.isSuccess() || status.value in 300..399 -> status.value
            status == HttpStatusCode.TooManyRequests -> throw TapException(TapError.RATE_LIMIT)
            status.value >= 500 -> throw TapException(TapError.SERVER)
            else -> throw TapException(TapError.WEB_REJECTED)
        }
    }

    private fun contentTypeFor(body: String): ContentType =
        if (body.trimStart().startsWith("{") || body.trimStart().startsWith("[")) {
            ContentType.Application.Json
        } else {
            ContentType.Text.Plain
        }

    companion object {
        /**
         * Only http(s). A trigger must never be able to reach a `file:`, `content:` or `intent:`
         * address, which on Android would turn a shared routine into a way of reading the phone.
         */
        fun isSupportedUrl(url: String): Boolean {
            val trimmed = url.trim().lowercase()
            return (trimmed.startsWith("https://") || trimmed.startsWith("http://")) &&
                trimmed.length > trimmed.indexOf("://") + 3
        }

        /**
         * What tap history is allowed to remember about a request.
         *
         * Only the method and the host: a query string is where webhook secrets live in practice
         * (IFTTT, Shelly, Zapier all put the key in the path), and tap history is plain rows in an
         * unencrypted table that a support screenshot may well end up showing.
         */
        fun redactForLog(url: String, method: WebhookMethod): String {
            val host = runCatching {
                val afterScheme = url.substringAfter("://")
                afterScheme.substringBefore('/').substringBefore('?')
            }.getOrNull().orEmpty()
            return if (host.isBlank()) method.name else "${method.name} $host"
        }

        /**
         * Header names whose value is a credential. Stored encrypted rather than in the tags table,
         * and never written to a log line.
         */
        fun isSecretHeader(name: String): Boolean =
            name.trim().lowercase() in setOf("authorization", "x-api-key", "x-auth-token", "apikey", "api-key")

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Header maps are persisted as a flat JSON object; anything else reads as no headers. */
        fun parseHeaders(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            return runCatching {
                (json.parseToJsonElement(raw) as JsonObject).mapNotNull { (k, v) ->
                    (v as? JsonPrimitive)?.content?.let { k to it }
                }.toMap()
            }.getOrDefault(emptyMap())
        }

        fun encodeHeaders(headers: Map<String, String>): String =
            JsonObject(headers.mapValues { JsonPrimitive(it.value) }).toString()
    }
}

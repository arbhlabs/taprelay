package com.arbhlabs.taprelay.pc

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.security.MessageDigest

sealed interface PcRelayResult {
    data class Accepted(val eventId: String, val message: String?) : PcRelayResult
    data class Failed(val message: String, val offline: Boolean = false) : PcRelayResult
}

/**
 * Small HTTP client for the Windows companion. Pairing is deliberately explicit; the token is
 * never logged or put in a TagEntity. Callers supply a stable owner id for diagnostics.
 */
class PcRelayClient(
    private val http: HttpClient,
    private val baseUrl: String,
    private val token: String,
    private val ownerId: String,
    private val protocol: Int = PcRelayProtocol.CURRENT_VERSION
) {
    /** Set once a pre-HMAC PC helper has accepted the bearer fallback; cleared with this client on re-pair. */
    @Volatile private var bearerOnly = false

    suspend fun pair(pairingCode: String): Result<PcRelayProtocol.PairResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                http.post("${baseUrl.trimEnd('/')}/v1/pair") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        PcRelayProtocol.json.encodeToString(
                            PcRelayProtocol.PairRequest.serializer(),
                            PcRelayProtocol.PairRequest(protocol, pairingCode.trim())
                        )
                    )
                }.body<PcRelayProtocol.PairResponse>().also {
                    check(it.protocol == protocol) { "Windows relay protocol is incompatible." }
                }
            }
        }

    suspend fun send(action: String, value: String? = null, eventId: String = UUID.randomUUID().toString()): PcRelayResult =
        withContext(Dispatchers.IO) {
            if (protocol != PcRelayProtocol.CURRENT_VERSION) {
                return@withContext PcRelayResult.Failed("Windows relay protocol is incompatible.")
            }
            if (action !in PcRelayAction.all) {
                return@withContext PcRelayResult.Failed("That Windows action is not supported.")
            }
            try {
                val path = "/v1/action"
                val (wireAction, wireValue) = PcRelayAction.wire(action, value)
                val body = PcRelayProtocol.encodeAction(
                    PcRelayProtocol.ActionRequest(protocol, eventId, ownerId, wireAction, wireValue)
                )
                var response = http.post("${baseUrl.trimEnd('/')}$path") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", if (bearerOnly) "Bearer $token" else hmacHeader("POST", path, body))
                    setBody(body)
                }
                // Existing PC helpers understand the original bearer token only. A freshly
                // upgraded helper accepts the signed request above; retrying just a 401 keeps
                // a phone update from severing a working, already-paired PC. Once that fallback
                // has worked it is used straight away, so every press is one request, not two.
                if (response.status.value == 401 && !bearerOnly) {
                    response = http.post("${baseUrl.trimEnd('/')}$path") {
                        contentType(ContentType.Application.Json)
                        header("Authorization", "Bearer $token")
                        setBody(body)
                    }
                    if (response.status.value in 200..299) bearerOnly = true
                }
                val result = response.body<PcRelayProtocol.ActionResponse>()
                if (result.protocol != protocol) {
                    PcRelayResult.Failed("Windows relay protocol is incompatible.")
                } else if (result.accepted) {
                    PcRelayResult.Accepted(result.eventId, result.message)
                } else {
                    PcRelayResult.Failed(result.message ?: "Windows relay rejected the action.", !result.online)
                }
            } catch (e: Exception) {
                PcRelayResult.Failed("Windows relay is unavailable.", offline = true)
            }
        }

    suspend fun unpair(): Boolean = withContext(Dispatchers.IO) {
        val path = "/v1/unpair"
        val body = "{}"
        runCatching {
            var response = http.post("${baseUrl.trimEnd('/')}$path") {
                header("Authorization", hmacHeader("POST", path, body))
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status.value == 401) {
                response = http.post("${baseUrl.trimEnd('/')}$path") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
            response.status.value in 200..299
        }.getOrDefault(false)
    }

    private fun hmacHeader(method: String, path: String, body: String): String {
        val timestamp = System.currentTimeMillis() / 1000L
        val nonce = UUID.randomUUID().toString()
        val tokenHash = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        val hashHex = tokenHash.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val message = "$method\n$path\n$timestamp\n$nonce\n$body"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(tokenHash, "HmacSHA256"))
        val signature = Base64.encodeToString(mac.doFinal(message.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP or Base64.URL_SAFE)
        return "Hmac ${hashHex.take(16)}:$timestamp:$nonce:$signature"
    }
}

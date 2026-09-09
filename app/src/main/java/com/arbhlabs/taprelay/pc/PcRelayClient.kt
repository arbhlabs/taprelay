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
                val response = http.post("${baseUrl.trimEnd('/')}/v1/action") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $token")
                    setBody(
                        PcRelayProtocol.encodeAction(
                            PcRelayProtocol.ActionRequest(protocol, eventId, ownerId, action, value)
                        )
                    )
                }.body<PcRelayProtocol.ActionResponse>()
                if (response.protocol != protocol) {
                    PcRelayResult.Failed("Windows relay protocol is incompatible.")
                } else if (response.accepted) {
                    PcRelayResult.Accepted(response.eventId, response.message)
                } else {
                    PcRelayResult.Failed(response.message ?: "Windows relay rejected the action.", !response.online)
                }
            } catch (e: Exception) {
                PcRelayResult.Failed("Windows relay is unavailable.", offline = true)
            }
        }
}

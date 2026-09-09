package com.arbhlabs.taprelay.pc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire contract shared by TapRelay and the optional Windows relay companion. */
object PcRelayProtocol {
    const val CURRENT_VERSION = 1
    const val DISCOVERY_PORT = 38117
    const val SERVICE_NAME = "_taprelay._tcp"

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class DiscoveryResponse(
        val protocol: Int = CURRENT_VERSION,
        val name: String,
        val host: String,
        val port: Int,
        val pairingRequired: Boolean = true
    )

    @Serializable
    data class PairRequest(
        val protocol: Int = CURRENT_VERSION,
        val pairingCode: String
    )

    @Serializable
    data class PairResponse(
        val protocol: Int = CURRENT_VERSION,
        val relayId: String,
        val token: String
    )

    @Serializable
    data class ActionRequest(
        val protocol: Int = CURRENT_VERSION,
        val eventId: String,
        val ownerId: String,
        val action: String,
        val value: String? = null
    )

    @Serializable
    data class ActionResponse(
        val protocol: Int = CURRENT_VERSION,
        val eventId: String,
        val accepted: Boolean,
        val online: Boolean = true,
        val message: String? = null
    )

    fun encodeAction(request: ActionRequest): String =
        json.encodeToString(ActionRequest.serializer(), request)

    fun decodeDiscovery(raw: String): DiscoveryResponse = json.decodeFromString(raw)

    fun decodePair(raw: String): PairResponse = json.decodeFromString(raw)

    fun decodeAction(raw: String): ActionResponse = json.decodeFromString(raw)
}

object PcRelayAction {
    const val MEDIA_PLAY_PAUSE = "media.play_pause"
    const val MEDIA_NEXT = "media.next"
    const val MEDIA_PREVIOUS = "media.previous"
    const val MEDIA_VOLUME_UP = "media.volume_up"
    const val MEDIA_VOLUME_DOWN = "media.volume_down"
    const val MEDIA_MUTE = "media.mute"

    val all = setOf(
        MEDIA_PLAY_PAUSE, MEDIA_NEXT, MEDIA_PREVIOUS,
        MEDIA_VOLUME_UP, MEDIA_VOLUME_DOWN, MEDIA_MUTE
    )
}

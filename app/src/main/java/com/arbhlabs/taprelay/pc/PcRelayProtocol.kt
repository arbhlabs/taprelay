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
    const val MEDIA_STOP = "media.stop"
    /** Value: a shortcut such as "ctrl+shift+m", "alt+tab", "f11", "win+d" or "space". */
    const val KEYS_SEND = "keys.send"
    /** Value: a URL, a program path or a file/folder, opened the way double-clicking would. */
    const val OPEN_TARGET = "open.target"
    const val SYSTEM_LOCK = "system.lock"
    const val DISPLAY_OFF = "display.off"
    const val SYSTEM_SLEEP = "system.sleep"

    /** One PC action as the editor shows it; [valueHint] non-null means it needs a value. */
    data class Spec(val id: String, val label: String, val valueLabel: String? = null, val valueHint: String? = null)

    val specs = listOf(
        Spec(MEDIA_PLAY_PAUSE, "Play / Pause"),
        Spec(MEDIA_NEXT, "Next track"),
        Spec(MEDIA_PREVIOUS, "Previous track"),
        Spec(MEDIA_STOP, "Stop"),
        Spec(MEDIA_VOLUME_UP, "Volume up"),
        Spec(MEDIA_VOLUME_DOWN, "Volume down"),
        Spec(MEDIA_MUTE, "Mute"),
        Spec(KEYS_SEND, "Keyboard shortcut", "Shortcut", "e.g. ctrl+shift+m, alt+tab, f11, win+d"),
        Spec(OPEN_TARGET, "Open a link, app or file", "What to open", "e.g. https://youtube.com or C:\\Games\\game.exe"),
        Spec(SYSTEM_LOCK, "Lock the PC"),
        Spec(DISPLAY_OFF, "Turn the screen off"),
        Spec(SYSTEM_SLEEP, "Sleep")
    )

    val all = specs.map { it.id }.toSet()

    fun spec(id: String): Spec? = specs.firstOrNull { it.id == id }

    fun label(id: String): String = spec(id)?.label ?: id
}

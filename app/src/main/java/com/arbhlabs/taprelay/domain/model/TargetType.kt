package com.arbhlabs.taprelay.domain.model

/**
 * What a TapRelay item does when a trigger reaches it.
 *
 * Everything a trigger can cause is one of these, which is why every trigger type - NFC, game
 * controller, place, widget, in-app - reaches all of them without knowing any of them exist.
 */
enum class TargetType {
    DEVICE,
    SCENE,

    /**
     * Not a smart-home target at all: the item writes one log into LastDose. It is a target type
     * rather than a separate feature so every trigger reaches it through the same
     * `trigger -> item -> activation mode` route as a lamp.
     */
    LASTDOSE_LOG,

    /**
     * 0.2.1: one web request to an address the owner supplies.
     *
     * Home Assistant is deliberately *not* a target type. It is a `SmartHomeProvider` like Govee
     * and Tuya, so its entities are DEVICE and SCENE items and every screen that already
     * understands a lamp understood a Home Assistant light the day it was added.
     */
    WEBHOOK,

    /** 0.2.1: opens an app or a link on the phone. */
    LAUNCH,

    /** 0.2.1: changes something about the phone itself, such as Do Not Disturb. */
    PHONE,

    /** An action delivered to the paired Windows companion, such as media control. */
    PC_RELAY,

    /**
     * 0.2.1: a Magic Action - several of the items above, in order, from one trigger.
     *
     * Its steps are references to other items, so the sequence engine contains no knowledge of any
     * integration and gains new ones for free.
     */
    MAGIC_ACTION
}

/**
 * The phone-side actions a [TargetType.PHONE] item can perform, stored in the item's `deviceId`
 * the same way a LastDose item stores its log id there.
 *
 * Every action here works on an ordinary phone with no smart-home account, no API key and no
 * accessibility service. Media and volume go through Android's own `AudioManager`, so they reach
 * whichever app is playing - Spotify, YouTube Music, a podcast player - without TapRelay knowing
 * any of them exist. The torch goes through `CameraManager`. Do Not Disturb is the one that needs
 * a switch, and Android makes the owner grant it once in system settings.
 */
object PhoneAction {
    // Media - dispatched as media-button key events to the active media session.
    const val MEDIA_PLAY_PAUSE = "media_play_pause"
    const val MEDIA_NEXT = "media_next"
    const val MEDIA_PREVIOUS = "media_previous"

    // Volume - the music stream, with the system volume panel shown.
    const val VOLUME_UP = "volume_up"
    const val VOLUME_DOWN = "volume_down"
    const val VOLUME_MUTE_TOGGLE = "volume_mute_toggle"

    // Torch - the rear flashlight.
    const val FLASHLIGHT_ON = "flashlight_on"
    const val FLASHLIGHT_OFF = "flashlight_off"
    const val FLASHLIGHT_TOGGLE = "flashlight_toggle"

    // Do Not Disturb - needs Notification Policy Access.
    const val DND_ON = "dnd_on"
    const val DND_OFF = "dnd_off"
    const val DND_TOGGLE = "dnd_toggle"

    /** Ordered for the picker: the no-permission actions first, Do Not Disturb last. */
    val ALL = listOf(
        MEDIA_PLAY_PAUSE, MEDIA_NEXT, MEDIA_PREVIOUS,
        VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE_TOGGLE,
        FLASHLIGHT_TOGGLE, FLASHLIGHT_ON, FLASHLIGHT_OFF,
        DND_ON, DND_OFF, DND_TOGGLE
    )

    /** The picker groups the actions under these headings, in this order. */
    val CATEGORIES: List<Pair<String, List<String>>> = listOf(
        "Music & media" to listOf(MEDIA_PLAY_PAUSE, MEDIA_NEXT, MEDIA_PREVIOUS),
        "Volume" to listOf(VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE_TOGGLE),
        "Flashlight" to listOf(FLASHLIGHT_TOGGLE, FLASHLIGHT_ON, FLASHLIGHT_OFF),
        "Do Not Disturb" to listOf(DND_ON, DND_OFF, DND_TOGGLE)
    )

    /** True when Android will make the owner grant a switch before this action can run. */
    fun needsDndAccess(key: String): Boolean = key in setOf(DND_ON, DND_OFF, DND_TOGGLE)

    fun label(key: String): String = when (key) {
        MEDIA_PLAY_PAUSE -> "Play / pause"
        MEDIA_NEXT -> "Next track"
        MEDIA_PREVIOUS -> "Previous track"
        VOLUME_UP -> "Volume up"
        VOLUME_DOWN -> "Volume down"
        VOLUME_MUTE_TOGGLE -> "Mute / unmute"
        FLASHLIGHT_ON -> "Flashlight on"
        FLASHLIGHT_OFF -> "Flashlight off"
        FLASHLIGHT_TOGGLE -> "Flashlight toggle"
        DND_ON -> "Do Not Disturb on"
        DND_OFF -> "Do Not Disturb off"
        DND_TOGGLE -> "Do Not Disturb toggle"
        else -> "Phone action"
    }
}

/** How a [TargetType.LAUNCH] item is stored: the `deviceSku` says which of these the id is. */
object LaunchKind {
    const val APP = "app"
    const val LINK = "link"
}

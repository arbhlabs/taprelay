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
 * Only actions Android grants an ordinary app through a documented, user-granted permission are
 * here. Nothing needs an accessibility service or a hidden API.
 */
object PhoneAction {
    const val DND_ON = "dnd_on"
    const val DND_OFF = "dnd_off"
    const val DND_TOGGLE = "dnd_toggle"

    val ALL = listOf(DND_ON, DND_OFF, DND_TOGGLE)

    fun label(key: String): String = when (key) {
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

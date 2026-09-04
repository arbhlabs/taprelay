package com.arbhlabs.taprelay.domain.model

/** Whether a TapRelay mapping targets an individual smart-home device or a multi-device scene. */
enum class TargetType {
    DEVICE,
    SCENE,

    /**
     * Not a smart-home target at all: the item writes one log into LastDose. It is a target type
     * rather than a separate feature so every trigger - NFC, controller, place, in-app - reaches it
     * through the same `trigger -> item -> activation mode` route as a lamp.
     */
    LASTDOSE_LOG
}

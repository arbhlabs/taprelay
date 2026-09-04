package com.arbhlabs.taprelay.domain.model

enum class ActionType {
    TURN_ON,
    TURN_OFF,
    TOGGLE,
    SET_BRIGHTNESS,
    SET_COLOR,
    SET_SCENE,
    RUN_SCENE
}

/**
 * What a tag does to a light's power. SET_BRIGHTNESS, SET_COLOR and SET_SCENE are kept so
 * tags saved by 0.0.5 and 0.0.6 keep working; they all meant "turn it on and apply this".
 */
fun ActionType.powerIntent(): ActionType = when (this) {
    ActionType.SET_BRIGHTNESS, ActionType.SET_COLOR, ActionType.SET_SCENE -> ActionType.TURN_ON
    else -> this
}

package com.arbhlabs.taprelay.domain.model

/** Pure power-state logic, shared by the executor and covered by unit tests. */
object Toggle {
    /** Optimistic target given the action and the last state we believe the device is in. */
    fun target(action: ActionType, lastKnownState: Int): Int = when (action) {
        ActionType.TURN_ON -> 1
        ActionType.TURN_OFF -> 0
        ActionType.TOGGLE -> if (lastKnownState == 1) 0 else 1
        // Asking for a brightness, colour, or fan speed implies turning on.
        ActionType.SET_BRIGHTNESS, ActionType.SET_COLOR, ActionType.SET_SCENE,
        ActionType.RUN_SCENE, ActionType.TOGGLE_FAN_SPEED -> 1
    }

    /** Inverse of an authoritative state reading (used when a live query succeeds). */
    fun inverseOf(state: Int): Int = if (state == 1) 0 else 1
}

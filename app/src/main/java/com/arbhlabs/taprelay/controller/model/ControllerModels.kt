package com.arbhlabs.taprelay.controller.model

import android.view.KeyEvent
import android.view.MotionEvent

data class ControllerDevice(
    val id: Int,
    val name: String,
    val descriptor: String,
    val vendorId: Int = 0,
    val productId: Int = 0,
    val isGamepad: Boolean = true,
    /** 0-100 when the pad reports a battery, null when it does not. */
    val batteryPercent: Int? = null
)

data class ControllerInput(
    val key: String,
    val label: String,
    val isChord: Boolean = false
)

object ControllerKeys {
    // Action Buttons
    const val BUTTON_A = "BUTTON_A"
    const val BUTTON_B = "BUTTON_B"
    const val BUTTON_X = "BUTTON_X"
    const val BUTTON_Y = "BUTTON_Y"

    // Bumpers
    const val BUTTON_L1 = "BUTTON_L1"
    const val BUTTON_R1 = "BUTTON_R1"

    // Triggers
    const val BUTTON_L2 = "BUTTON_L2"
    const val BUTTON_R2 = "BUTTON_R2"

    // Thumbstick Clicks
    const val BUTTON_THUMBL = "BUTTON_THUMBL"
    const val BUTTON_THUMBR = "BUTTON_THUMBR"

    // Menu / Navigation
    const val BUTTON_START = "BUTTON_START"
    const val BUTTON_SELECT = "BUTTON_SELECT"
    const val BUTTON_MODE = "BUTTON_MODE"

    // D-Pad
    const val DPAD_UP = "DPAD_UP"
    const val DPAD_DOWN = "DPAD_DOWN"
    const val DPAD_LEFT = "DPAD_LEFT"
    const val DPAD_RIGHT = "DPAD_RIGHT"
    const val DPAD_CENTER = "DPAD_CENTER"

    fun normalizeKeyCode(keyCode: Int): String? {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> BUTTON_A
            KeyEvent.KEYCODE_BUTTON_B -> BUTTON_B
            KeyEvent.KEYCODE_BUTTON_X -> BUTTON_X
            KeyEvent.KEYCODE_BUTTON_Y -> BUTTON_Y

            KeyEvent.KEYCODE_BUTTON_L1 -> BUTTON_L1
            KeyEvent.KEYCODE_BUTTON_R1 -> BUTTON_R1
            KeyEvent.KEYCODE_BUTTON_L2 -> BUTTON_L2
            KeyEvent.KEYCODE_BUTTON_R2 -> BUTTON_R2

            KeyEvent.KEYCODE_BUTTON_THUMBL -> BUTTON_THUMBL
            KeyEvent.KEYCODE_BUTTON_THUMBR -> BUTTON_THUMBR

            KeyEvent.KEYCODE_BUTTON_START -> BUTTON_START
            KeyEvent.KEYCODE_BUTTON_SELECT -> BUTTON_SELECT
            KeyEvent.KEYCODE_BUTTON_MODE -> BUTTON_MODE

            KeyEvent.KEYCODE_DPAD_UP -> DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> DPAD_RIGHT
            KeyEvent.KEYCODE_DPAD_CENTER -> DPAD_CENTER

            else -> null
        }
    }

    fun isGamepadSpecificKey(keyCode: Int): Boolean {
        return (keyCode in KeyEvent.KEYCODE_BUTTON_A..KeyEvent.KEYCODE_BUTTON_16) ||
            keyCode == KeyEvent.KEYCODE_BUTTON_START ||
            keyCode == KeyEvent.KEYCODE_BUTTON_SELECT ||
            keyCode == KeyEvent.KEYCODE_BUTTON_MODE
    }

    fun isModifier(key: String): Boolean {
        return key == BUTTON_L1 || key == BUTTON_R1 || key == BUTTON_L2 || key == BUTTON_R2
    }

    fun formatLabel(key: String): String {
        if (key.contains("+")) {
            val parts = key.split("+")
            return parts.joinToString(" + ") { singleButtonLabel(it) }
        }
        return singleButtonLabel(key)
    }

    private fun singleButtonLabel(singleKey: String): String {
        return when (singleKey) {
            BUTTON_A -> "A Button"
            BUTTON_B -> "B Button"
            BUTTON_X -> "X Button"
            BUTTON_Y -> "Y Button"
            BUTTON_L1 -> "LB / L1"
            BUTTON_R1 -> "RB / R1"
            BUTTON_L2 -> "LT / L2"
            BUTTON_R2 -> "RT / R2"
            BUTTON_THUMBL -> "Left Stick Click (L3)"
            BUTTON_THUMBR -> "Right Stick Click (R3)"
            BUTTON_START -> "Menu / Start"
            BUTTON_SELECT -> "View / Select"
            BUTTON_MODE -> "Guide / Home"
            DPAD_UP -> "D-Pad Up"
            DPAD_DOWN -> "D-Pad Down"
            DPAD_LEFT -> "D-Pad Left"
            DPAD_RIGHT -> "D-Pad Right"
            DPAD_CENTER -> "D-Pad Center"
            else -> singleKey.replace("BUTTON_", "Button ").replace("DPAD_", "D-Pad ")
        }
    }
}

package com.arbhlabs.taprelay.controller

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.arbhlabs.taprelay.controller.model.ControllerInput
import com.arbhlabs.taprelay.controller.model.ControllerKeys

class ControllerInputProcessor(
    private val debounceMs: Long = 350L
) {
    private val activeModifiers = mutableSetOf<String>()
    private val lastTriggered = mutableMapOf<String, Long>()

    // Hat switch state tracking (to detect single discrete transitions from 0 -> 1 / -1)
    private var prevHatX = 0f
    private var prevHatY = 0f

    // Analog trigger state tracking (to detect single discrete transitions past threshold)
    private var prevLTriggerActive = false
    private var prevRTriggerActive = false

    fun reset() {
        activeModifiers.clear()
        lastTriggered.clear()
        prevHatX = 0f
        prevHatY = 0f
        prevLTriggerActive = false
        prevRTriggerActive = false
    }

    /**
     * Processes a [KeyEvent] and returns a normalized [ControllerInput] if a valid, debounced
     * controller press or chord is identified. Returns null otherwise.
     */
    fun processKeyEvent(event: KeyEvent): ControllerInput? =
        processKeyEvent(event.keyCode, event.action, event.repeatCount)

    fun processKeyEvent(keyCode: Int, action: Int, repeatCount: Int = 0): ControllerInput? {
        val normalized = ControllerKeys.normalizeKeyCode(keyCode) ?: return null

        if (action == KeyEvent.ACTION_UP) {
            activeModifiers.remove(normalized)
            return null
        }

        if (action != KeyEvent.ACTION_DOWN) return null
        if (repeatCount > 0) return null // Ignore hardware key auto-repeat

        // Check if this key is a modifier (e.g. LB / RB / LT / RT)
        if (ControllerKeys.isModifier(normalized)) {
            activeModifiers.add(normalized)
            // If user assigns a bumper/trigger as an action by itself, we still check debouncing
            return checkDebounceAndBuild(normalized)
        }

        // If one or more modifiers are held down, form a chord (e.g. BUTTON_L1+BUTTON_A)
        val inputKey = if (activeModifiers.isNotEmpty()) {
            val sortedModifiers = activeModifiers.sorted().joinToString("+")
            "$sortedModifiers+$normalized"
        } else {
            normalized
        }

        return checkDebounceAndBuild(inputKey)
    }

    /**
     * Processes a [MotionEvent] for gamepads with Hat switch D-pads or analog triggers.
     * Returns a normalized [ControllerInput] if a threshold transition occurred.
     */
    fun processMotionEvent(event: MotionEvent): ControllerInput? {
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val hatResult = processHatAxis(hatX, hatY)
        if (hatResult != null) return hatResult

        val lTriggerVal = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        val rTriggerVal = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )
        return processTriggerAxes(lTriggerVal, rTriggerVal)
    }

    fun processHatAxis(hatX: Float, hatY: Float): ControllerInput? {
        var dpadKey: String? = null
        if (prevHatX == 0f && hatX <= -0.5f) {
            dpadKey = ControllerKeys.DPAD_LEFT
        } else if (prevHatX == 0f && hatX >= 0.5f) {
            dpadKey = ControllerKeys.DPAD_RIGHT
        }

        if (prevHatY == 0f && hatY <= -0.5f) {
            dpadKey = ControllerKeys.DPAD_UP
        } else if (prevHatY == 0f && hatY >= 0.5f) {
            dpadKey = ControllerKeys.DPAD_DOWN
        }

        prevHatX = if (kotlin.math.abs(hatX) >= 0.5f) hatX else 0f
        prevHatY = if (kotlin.math.abs(hatY) >= 0.5f) hatY else 0f

        if (dpadKey != null) {
            val key = if (activeModifiers.isNotEmpty()) {
                val sortedModifiers = activeModifiers.sorted().joinToString("+")
                "$sortedModifiers+$dpadKey"
            } else {
                dpadKey
            }
            return checkDebounceAndBuild(key)
        }
        return null
    }

    fun processTriggerAxes(lTriggerVal: Float, rTriggerVal: Float): ControllerInput? {
        val lActive = lTriggerVal >= 0.6f
        val rActive = rTriggerVal >= 0.6f

        var triggerKey: String? = null
        if (!prevLTriggerActive && lActive) {
            triggerKey = ControllerKeys.BUTTON_L2
            activeModifiers.add(ControllerKeys.BUTTON_L2)
        } else if (prevLTriggerActive && !lActive) {
            activeModifiers.remove(ControllerKeys.BUTTON_L2)
        }

        if (!prevRTriggerActive && rActive) {
            triggerKey = ControllerKeys.BUTTON_R2
            activeModifiers.add(ControllerKeys.BUTTON_R2)
        } else if (prevRTriggerActive && !rActive) {
            activeModifiers.remove(ControllerKeys.BUTTON_R2)
        }

        prevLTriggerActive = lActive
        prevRTriggerActive = rActive

        if (triggerKey != null) {
            return checkDebounceAndBuild(triggerKey)
        }

        return null
    }

    private fun checkDebounceAndBuild(inputKey: String): ControllerInput? {
        val now = System.currentTimeMillis()
        val prev = lastTriggered[inputKey] ?: 0L
        if (now - prev < debounceMs) return null

        lastTriggered[inputKey] = now
        return ControllerInput(
            key = inputKey,
            label = ControllerKeys.formatLabel(inputKey),
            isChord = inputKey.contains("+")
        )
    }

    companion object {
        fun isGameControllerEvent(source: Int, device: InputDevice?): Boolean {
            val hasGamepadSource = (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            val hasJoystickSource = (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (hasGamepadSource || hasJoystickSource) return true

            val deviceSources = device?.sources ?: 0
            val devGamepad = (deviceSources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            val devJoystick = (deviceSources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            return (devGamepad || devJoystick) && device?.isVirtual != true
        }
    }
}

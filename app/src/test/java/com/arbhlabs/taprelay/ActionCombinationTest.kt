package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.model.TargetType
import com.arbhlabs.taprelay.domain.model.powerIntent
import com.arbhlabs.taprelay.domain.provider.SmartHomeProvider
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Power, colour and brightness are independent, so every combination of them has to
 * reach the right provider calls. This records what each combination actually does.
 */
class ActionCombinationTest {

    private class RecordingProvider : SmartHomeProvider {
        override val providerId = "test"
        override val displayName = "Test"
        val calls = mutableListOf<String>()

        override suspend fun isConnected() = true
        override suspend fun discoverDevices(): List<DiscoveredDevice> = emptyList()
        override suspend fun getPowerState(deviceId: String, sku: String): Int? = null
        override suspend fun setPower(deviceId: String, sku: String, on: Boolean) {
            calls += if (on) "on" else "off"
        }
        override suspend fun setBrightness(deviceId: String, sku: String, percent: Int) {
            calls += "brightness=$percent"
        }
        override suspend fun setColor(deviceId: String, sku: String, rgb: Int) {
            calls += "color=${Integer.toHexString(rgb)}"
        }
        override suspend fun setLook(deviceId: String, sku: String, rgb: Int, percent: Int) {
            calls += "look=${Integer.toHexString(rgb)}@$percent"
        }
    }

    private suspend fun run(state: Int, brightness: Int?, color: Int?): List<String> {
        val p = RecordingProvider()
        p.executeAction("dev", TargetType.DEVICE, "sku", ActionType.TOGGLE, state, brightness, color)
        return p.calls
    }

    @Test
    fun power_only_just_switches() = runTest {
        assertEquals(listOf("on"), run(1, null, null))
        assertEquals(listOf("off"), run(0, null, null))
    }

    @Test
    fun brightness_only_sets_brightness() = runTest {
        assertEquals(listOf("brightness=10"), run(1, 10, null))
    }

    @Test
    fun colour_only_sets_colour() = runTest {
        assertEquals(listOf("color=1e5aff"), run(1, null, 0x1E5AFF))
    }

    /** The case the owner asked for: toggle on, blue, 10%. */
    @Test
    fun colour_and_brightness_together_are_one_look() = runTest {
        assertEquals(listOf("look=1e5aff@10"), run(1, 10, 0x1E5AFF))
    }

    /** Turning a light off never colours it first; a dark bulb has no look. */
    @Test
    fun turning_off_ignores_colour_and_brightness() = runTest {
        assertEquals(listOf("off"), run(0, 10, 0x1E5AFF))
    }

    /** Tags saved by 0.0.5 and 0.0.6 used the action itself to mean "turn on and apply". */
    @Test
    fun legacy_actions_map_onto_the_power_intent() {
        assertEquals(ActionType.TURN_ON, ActionType.SET_BRIGHTNESS.powerIntent())
        assertEquals(ActionType.TURN_ON, ActionType.SET_COLOR.powerIntent())
        assertEquals(ActionType.TURN_ON, ActionType.SET_SCENE.powerIntent())
        assertEquals(ActionType.TOGGLE, ActionType.TOGGLE.powerIntent())
        assertEquals(ActionType.TURN_OFF, ActionType.TURN_OFF.powerIntent())
    }

    @Test
    fun a_scene_target_is_left_to_the_scene_path() = runTest {
        val p = RecordingProvider()
        p.executeAction("scene", TargetType.SCENE, "sku", ActionType.RUN_SCENE, 1, 50, 0xFF0000)
        assertEquals(emptyList<String>(), p.calls)
    }
}

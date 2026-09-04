package com.arbhlabs.taprelay

import android.view.KeyEvent
import com.arbhlabs.taprelay.controller.ControllerInputProcessor
import com.arbhlabs.taprelay.controller.model.ControllerKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerInputProcessorTest {

    @Test
    fun normalize_standard_gamepad_buttons() {
        assertEquals(ControllerKeys.BUTTON_A, ControllerKeys.normalizeKeyCode(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(ControllerKeys.BUTTON_B, ControllerKeys.normalizeKeyCode(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(ControllerKeys.BUTTON_X, ControllerKeys.normalizeKeyCode(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(ControllerKeys.BUTTON_Y, ControllerKeys.normalizeKeyCode(KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(ControllerKeys.BUTTON_L1, ControllerKeys.normalizeKeyCode(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(ControllerKeys.BUTTON_R1, ControllerKeys.normalizeKeyCode(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(ControllerKeys.DPAD_UP, ControllerKeys.normalizeKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(ControllerKeys.DPAD_DOWN, ControllerKeys.normalizeKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(ControllerKeys.BUTTON_START, ControllerKeys.normalizeKeyCode(KeyEvent.KEYCODE_BUTTON_START))
    }

    @Test
    fun friendly_labels_format_cleanly() {
        assertEquals("A Button", ControllerKeys.formatLabel(ControllerKeys.BUTTON_A))
        assertEquals("B Button", ControllerKeys.formatLabel(ControllerKeys.BUTTON_B))
        assertEquals("D-Pad Up", ControllerKeys.formatLabel(ControllerKeys.DPAD_UP))
        assertEquals("LB / L1 + A Button", ControllerKeys.formatLabel("BUTTON_L1+BUTTON_A"))
        assertEquals("RB / R1 + D-Pad Up", ControllerKeys.formatLabel("BUTTON_R1+DPAD_UP"))
    }

    @Test
    fun modifier_detection() {
        assertTrue(ControllerKeys.isModifier(ControllerKeys.BUTTON_L1))
        assertTrue(ControllerKeys.isModifier(ControllerKeys.BUTTON_R1))
        assertTrue(ControllerKeys.isModifier(ControllerKeys.BUTTON_L2))
        assertTrue(ControllerKeys.isModifier(ControllerKeys.BUTTON_R2))
        org.junit.Assert.assertFalse(ControllerKeys.isModifier(ControllerKeys.BUTTON_A))
        org.junit.Assert.assertFalse(ControllerKeys.isModifier(ControllerKeys.DPAD_UP))
    }

    @Test
    fun key_event_processing_and_chording() {
        val processor = ControllerInputProcessor(debounceMs = 100L)

        // 1. Single A button press (ACTION_DOWN = 0, ACTION_UP = 1)
        val resultA = processor.processKeyEvent(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN, 0)
        assertNotNull(resultA)
        assertEquals(ControllerKeys.BUTTON_A, resultA?.key)
        assertEquals("A Button", resultA?.label)
        assertEquals(false, resultA?.isChord)

        // Key up for A
        val upA = processor.processKeyEvent(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_UP, 0)
        assertNull(upA)

        // 2. Press modifier LB (BUTTON_L1) down
        processor.processKeyEvent(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.ACTION_DOWN, 0)

        // 3. While LB is held, press A -> chord formed!
        val chordResult = processor.processKeyEvent(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN, 0)
        assertNotNull(chordResult)
        assertEquals("BUTTON_L1+BUTTON_A", chordResult?.key)
        assertEquals("LB / L1 + A Button", chordResult?.label)
        assertEquals(true, chordResult?.isChord)

        // 4. Release LB
        val upLb = processor.processKeyEvent(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.ACTION_UP, 0)
        assertNull(upLb)

        // 5. Subsequent press of A is single again (after debounce window)
        Thread.sleep(110L)
        val singleAgain = processor.processKeyEvent(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN, 0)
        assertNotNull(singleAgain)
        assertEquals(ControllerKeys.BUTTON_A, singleAgain?.key)
    }

    @Test
    fun hat_axis_dpad_processing() {
        val processor = ControllerInputProcessor(debounceMs = 100L)

        // Neutral -> Left (-1.0f)
        val left = processor.processHatAxis(hatX = -1.0f, hatY = 0f)
        assertNotNull(left)
        assertEquals(ControllerKeys.DPAD_LEFT, left?.key)

        // Neutralize back to 0
        processor.processHatAxis(hatX = 0f, hatY = 0f)

        Thread.sleep(110L)
        // Neutral -> Up (-1.0f)
        val up = processor.processHatAxis(hatX = 0f, hatY = -1.0f)
        assertNotNull(up)
        assertEquals(ControllerKeys.DPAD_UP, up?.key)
    }

    @Test
    fun trigger_axis_threshold_processing() {
        val processor = ControllerInputProcessor(debounceMs = 100L)

        // Below 0.6 threshold -> null
        assertNull(processor.processTriggerAxes(lTriggerVal = 0.3f, rTriggerVal = 0f))

        // Exceeds 0.6 threshold -> LT Trigger fired
        val triggerLt = processor.processTriggerAxes(lTriggerVal = 0.85f, rTriggerVal = 0f)
        assertNotNull(triggerLt)
        assertEquals(ControllerKeys.BUTTON_L2, triggerLt?.key)
        assertEquals("LT / L2", triggerLt?.label)
    }

    @Test
    fun repeat_key_events_are_ignored() {
        val processor = ControllerInputProcessor(debounceMs = 100L)
        val repeatEvent = processor.processKeyEvent(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN, repeatCount = 1)
        assertNull(repeatEvent)
    }
}

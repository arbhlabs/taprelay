package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.controller.ControllerInputProcessor
import com.arbhlabs.taprelay.controller.model.ControllerKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * One physical pull of an Xbox trigger is one action.
 *
 * The trigger is an analog axis, so these cases are about the shape of the signal - the climb, the
 * hold, the wobble at the top, and the release - not about a button going down and up.
 */
class AnalogTriggerTest {

    /** Debounce off, so what is under test is the threshold logic rather than a timer. */
    private fun processor() = ControllerInputProcessor(debounceMs = 0L)

    @Test
    fun `one pull produces one action`() {
        val p = processor()
        assertNull(p.processTriggerAxes(0f, 0.2f))
        assertEquals(ControllerKeys.BUTTON_R2, p.processTriggerAxes(0f, 0.9f)?.key)
        assertNull(p.processTriggerAxes(0f, 0f))
    }

    @Test
    fun `holding the trigger down never repeats`() {
        val p = processor()
        assertEquals(ControllerKeys.BUTTON_R2, p.processTriggerAxes(0f, 1f)?.key)
        repeat(50) { assertNull(p.processTriggerAxes(0f, 1f)) }
    }

    @Test
    fun `wobbling around the activation point does not re-fire`() {
        val p = processor()
        assertEquals(ControllerKeys.BUTTON_R2, p.processTriggerAxes(0f, 0.7f)?.key)
        // Between the release and activation thresholds: still held as far as the pad is concerned.
        assertNull(p.processTriggerAxes(0f, 0.62f))
        assertNull(p.processTriggerAxes(0f, 0.68f))
        assertNull(p.processTriggerAxes(0f, 0.4f))
        assertNull(p.processTriggerAxes(0f, 0.55f))
    }

    @Test
    fun `a partial pull is not an action`() {
        val p = processor()
        assertNull(p.processTriggerAxes(0f, 0.3f))
        assertNull(p.processTriggerAxes(0f, 0.5f))
        assertNull(p.processTriggerAxes(0f, 0.64f))
    }

    @Test
    fun `releasing re-arms for a deliberate second pull`() {
        val p = processor()
        assertEquals(ControllerKeys.BUTTON_R2, p.processTriggerAxes(0f, 1f)?.key)
        assertNull(p.processTriggerAxes(0f, 0.1f))
        assertEquals(ControllerKeys.BUTTON_R2, p.processTriggerAxes(0f, 1f)?.key)
    }

    @Test
    fun `left and right triggers are independent`() {
        val p = processor()
        assertEquals(ControllerKeys.BUTTON_L2, p.processTriggerAxes(1f, 0f)?.key)
        assertEquals(ControllerKeys.BUTTON_R2, p.processTriggerAxes(1f, 1f)?.key)
        assertNull(p.processTriggerAxes(1f, 1f))
        assertNull(p.processTriggerAxes(0f, 0f))
        assertEquals(ControllerKeys.BUTTON_L2, p.processTriggerAxes(1f, 0f)?.key)
    }

    @Test
    fun `reset drops a held trigger so a reconnect starts clean`() {
        val p = processor()
        assertEquals(ControllerKeys.BUTTON_R2, p.processTriggerAxes(0f, 1f)?.key)
        p.reset()
        assertEquals(ControllerKeys.BUTTON_R2, p.processTriggerAxes(0f, 1f)?.key)
    }
}

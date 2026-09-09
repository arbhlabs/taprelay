package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.controller.AdjustmentConfig
import com.arbhlabs.taprelay.controller.AdjustmentEvent
import com.arbhlabs.taprelay.controller.AdjustmentStick
import com.arbhlabs.taprelay.controller.PostActivationAdjustmentSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostActivationAdjustmentTest {
    private var clock = 1_000L

    private fun session(enabled: Boolean = true): PostActivationAdjustmentSession =
        PostActivationAdjustmentSession(
            AdjustmentConfig(enabled = enabled, stick = AdjustmentStick.RIGHT, durationMs = 2_000L),
            nowMs = { clock }
        )

    @Test
    fun onlyOnArmsAndOffDoesNot() {
        val adjustment = session()
        adjustment.start(poweredOn = false)
        assertNull(adjustment.onStick(0f, 1f, 50))
        adjustment.start(poweredOn = true)
        assertEquals(AdjustmentEvent.BrightnessDelta(5), adjustment.onStick(0f, 1f, 50))
    }

    @Test
    fun deadZoneDebounceAndExpiryAreApplied() {
        val adjustment = session()
        adjustment.start(poweredOn = true)
        assertNull(adjustment.onStick(0f, 0.1f, 50))
        assertEquals(AdjustmentEvent.BrightnessDelta(-5), adjustment.onStick(0f, -1f, 50))
        assertNull(adjustment.onStick(0f, -1f, 50))
        clock += 100L
        assertEquals(AdjustmentEvent.BrightnessDelta(5), adjustment.onStick(0f, 1f, 50))
        clock += 2_000L
        assertNull(adjustment.onStick(0f, 1f, 50))
    }

    @Test
    fun swatchesAreDiscreteAndInvalidIndexesAreIgnored() {
        val adjustment = session()
        adjustment.start(poweredOn = true)
        assertEquals(AdjustmentEvent.ColorSwatch(0xFFFFFFFF.toInt()), adjustment.onSwatch(0))
        assertNull(adjustment.onSwatch(99))
    }
}

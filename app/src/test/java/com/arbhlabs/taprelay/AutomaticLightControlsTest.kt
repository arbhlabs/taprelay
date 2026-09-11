package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.controller.AutomaticLightControls
import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticLightControlsTest {
    @Test
    fun hue_wraps_in_both_directions() {
        assertEquals(5f, AutomaticLightControls.wrapHue(365f), 0.001f)
        assertEquals(355f, AutomaticLightControls.wrapHue(-5f), 0.001f)
    }

    @Test
    fun dpad_brightness_walks_visible_notches_and_stops_at_the_ends() {
        assertEquals(70, AutomaticLightControls.stepBrightness(50, up = true))
        assertEquals(35, AutomaticLightControls.stepBrightness(50, up = false))
        // Off-ladder values (a saved 60%, a 53%) snap to the neighbouring notch.
        assertEquals(70, AutomaticLightControls.stepBrightness(60, up = true))
        assertEquals(50, AutomaticLightControls.stepBrightness(60, up = false))
        assertEquals(70, AutomaticLightControls.stepBrightness(100, up = false))
        assertEquals(5, AutomaticLightControls.stepBrightness(1, up = true))
        // At either end the value is unchanged, which the caller reports as a limit.
        assertEquals(100, AutomaticLightControls.stepBrightness(100, up = true))
        assertEquals(1, AutomaticLightControls.stepBrightness(1, up = false))
    }

    @Test
    fun dpad_hue_steps_wrap_both_ways() {
        assertEquals(30f, AutomaticLightControls.stepHue(0f, forward = true), 0.001f)
        assertEquals(330f, AutomaticLightControls.stepHue(0f, forward = false), 0.001f)
    }

    @Test
    fun adjustment_window_is_exactly_twenty_seconds() {
        assertEquals(20_000L, AutomaticLightControls.WINDOW_MS)
    }

    @Test
    fun hue_round_trip_keeps_primary_colours() {
        assertEquals(0xFF0000, AutomaticLightControls.hsvToRgb(AutomaticLightControls.rgbToHue(0xFF0000)))
        assertEquals(0x00FF00, AutomaticLightControls.hsvToRgb(AutomaticLightControls.rgbToHue(0x00FF00)))
        assertEquals(0x0000FF, AutomaticLightControls.hsvToRgb(AutomaticLightControls.rgbToHue(0x0000FF)))
    }
}

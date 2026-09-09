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
    fun hue_round_trip_keeps_primary_colours() {
        assertEquals(0xFF0000, AutomaticLightControls.hsvToRgb(AutomaticLightControls.rgbToHue(0xFF0000)))
        assertEquals(0x00FF00, AutomaticLightControls.hsvToRgb(AutomaticLightControls.rgbToHue(0x00FF00)))
        assertEquals(0x0000FF, AutomaticLightControls.hsvToRgb(AutomaticLightControls.rgbToHue(0x0000FF)))
    }
}

package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.ColorMath
import com.arbhlabs.taprelay.domain.model.LightPresets
import com.arbhlabs.taprelay.domain.model.Toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightSettingsTest {

    @Test
    fun brightness_percent_is_clamped_to_a_usable_range() {
        assertEquals(1, Brightness.clampPercent(0))
        assertEquals(1, Brightness.clampPercent(-40))
        assertEquals(100, Brightness.clampPercent(255))
        assertEquals(60, Brightness.clampPercent(60))
    }

    /** Govee's range capability is already a percentage, so this is a pass-through. */
    @Test
    fun govee_brightness_is_the_percentage_itself() {
        assertEquals(1, Brightness.toGovee(1))
        assertEquals(100, Brightness.toGovee(100))
        assertEquals(42, Brightness.toGovee(42))
    }

    /** Tuya's bright_value_v2 data point runs 10..1000, never 0. */
    @Test
    fun tuya_brightness_spans_the_full_data_point_range() {
        assertEquals(10, Brightness.toTuya(1))
        assertEquals(1000, Brightness.toTuya(100))
        assertTrue(Brightness.toTuya(50) in 490..510)
        assertTrue(Brightness.toTuya(0) >= 10)
        assertTrue(Brightness.toTuya(500) <= 1000)
    }

    @Test
    fun rgb_converts_to_tuya_hue_saturation_and_value() {
        val (h, s, v) = ColorMath.toTuyaHsv(0xFF0000)
        assertEquals(0, h)
        assertEquals(1000, s)
        assertEquals(1000, v)

        val (gh, gs, gv) = ColorMath.toTuyaHsv(0x00FF00)
        assertEquals(120, gh)
        assertEquals(1000, gs)
        assertEquals(1000, gv)

        val (bh, _, _) = ColorMath.toTuyaHsv(0x0000FF)
        assertEquals(240, bh)
    }

    @Test
    fun pure_white_has_no_saturation_and_black_has_no_value() {
        val (_, whiteS, whiteV) = ColorMath.toTuyaHsv(0xFFFFFF)
        assertEquals(0, whiteS)
        assertEquals(1000, whiteV)

        val (_, _, blackV) = ColorMath.toTuyaHsv(0x000000)
        assertEquals(0, blackV)
    }

    @Test
    fun every_preset_round_trips_to_a_valid_tuya_colour() {
        LightPresets.ALL.forEach { preset ->
            val (h, s, v) = ColorMath.toTuyaHsv(preset.rgb)
            assertTrue("${preset.name} hue", h in 0..360)
            assertTrue("${preset.name} saturation", s in 0..1000)
            assertTrue("${preset.name} value", v in 0..1000)
        }
    }

    @Test
    fun presets_are_named_and_unknown_colours_fall_back() {
        assertEquals("Red", LightPresets.nameFor(0xFF0000))
        assertEquals("Custom", LightPresets.nameFor(0x123456))
    }

    /** Asking for a brightness or a colour implies the light should be on. */
    @Test
    fun brightness_and_colour_actions_target_the_on_state() {
        assertEquals(1, Toggle.target(ActionType.SET_BRIGHTNESS, 0))
        assertEquals(1, Toggle.target(ActionType.SET_COLOR, 0))
        assertEquals(1, Toggle.target(ActionType.SET_BRIGHTNESS, 1))
    }
}

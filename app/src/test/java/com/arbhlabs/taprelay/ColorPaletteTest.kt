package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.domain.model.ColorMath
import com.arbhlabs.taprelay.domain.model.LightPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPaletteTest {

    @Test
    fun every_preset_colour_is_distinct() {
        val rgbs = LightPresets.ALL.map { it.rgb }
        assertEquals(rgbs.size, rgbs.toSet().size)
        assertTrue("expected a broad palette", LightPresets.ALL.size >= 24)
    }

    @Test
    fun kelvin_runs_from_warm_to_cool() {
        val warm = ColorMath.kelvinToRgb(2200)
        val cool = ColorMath.kelvinToRgb(6500)
        // A warm white is red-heavy and blue-poor; a cool white evens out.
        assertTrue(ColorMath.red(warm) >= ColorMath.blue(warm) + 40)
        assertTrue(ColorMath.blue(cool) >= ColorMath.blue(warm))
    }

    @Test
    fun a_generated_white_round_trips_to_its_own_kelvin() {
        for (k in listOf(2000, 2700, 3000, 3500, 4500, 6500)) {
            assertEquals(k, ColorMath.nearestKelvin(ColorMath.kelvinToRgb(k)))
        }
    }

    @Test
    fun a_saturated_hue_is_not_mistaken_for_a_white() {
        assertNull(ColorMath.nearestKelvin(0x1E3AFF)) // Blue
        assertNull(ColorMath.nearestKelvin(0x28D828)) // Green
        assertNull(ColorMath.nearestKelvin(0xFF00C8)) // Magenta
    }

    @Test
    fun names_resolve_for_presets_whites_and_custom() {
        assertEquals("Warm white", LightPresets.nameFor(ColorMath.kelvinToRgb(2700)))
        assertEquals("Blue", LightPresets.nameFor(0x1E3AFF))
        assertTrue(LightPresets.nameFor(ColorMath.kelvinToRgb(3150)).endsWith("K"))
        assertEquals("Custom", LightPresets.nameFor(0x123456))
    }

    @Test
    fun the_default_is_a_warm_white() {
        assertNotNull(ColorMath.nearestKelvin(LightPresets.DEFAULT_RGB))
    }
}

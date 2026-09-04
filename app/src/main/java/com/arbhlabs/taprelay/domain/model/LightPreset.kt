package com.arbhlabs.taprelay.domain.model

/** A named colour a tag can set, stored as a plain 0xRRGGBB integer. */
data class LightPreset(val name: String, val rgb: Int)

object LightPresets {
    val ALL = listOf(
        LightPreset("Warm white", 0xFFC58F),
        LightPreset("Daylight", 0xFFFFFF),
        LightPreset("Red", 0xFF0000),
        LightPreset("Orange", 0xFF7A00),
        LightPreset("Amber", 0xFFC400),
        LightPreset("Green", 0x00FF3C),
        LightPreset("Teal", 0x00E5C0),
        LightPreset("Cyan", 0x00C8FF),
        LightPreset("Blue", 0x1E5AFF),
        LightPreset("Violet", 0x8A2BE2),
        LightPreset("Magenta", 0xFF00A0),
        LightPreset("Pink", 0xFF74C4)
    )

    fun nameFor(rgb: Int): String = ALL.firstOrNull { it.rgb == rgb }?.name ?: "Custom"
}

/**
 * Colour maths shared by the providers. Govee takes a packed RGB integer; Tuya takes
 * hue 0-360, saturation 0-1000 and value 0-1000.
 */
object ColorMath {
    fun red(rgb: Int) = (rgb shr 16) and 0xFF
    fun green(rgb: Int) = (rgb shr 8) and 0xFF
    fun blue(rgb: Int) = rgb and 0xFF

    /** Returns hue in 0..360, saturation in 0..1000 and value in 0..1000. */
    fun toTuyaHsv(rgb: Int): Triple<Int, Int, Int> {
        val r = red(rgb) / 255.0
        val g = green(rgb) / 255.0
        val b = blue(rgb) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val hue = when {
            delta == 0.0 -> 0.0
            max == r -> 60 * (((g - b) / delta) % 6)
            max == g -> 60 * (((b - r) / delta) + 2)
            else -> 60 * (((r - g) / delta) + 4)
        }
        val h = ((hue % 360) + 360) % 360
        val s = if (max == 0.0) 0.0 else delta / max
        return Triple(h.toInt(), (s * 1000).toInt(), (max * 1000).toInt())
    }
}

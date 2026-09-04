package com.arbhlabs.taprelay.domain.model

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** A named colour a tag can set, stored as a plain 0xRRGGBB integer. */
data class LightPreset(val name: String, val rgb: Int)

object LightPresets {

    /** White points, generated on the black-body curve so the kelvin slider agrees with them. */
    private val WHITE_KELVINS = listOf(2000, 2700, 3000, 3500, 4500, 6500)
    private val WHITE_NAMES = mapOf(
        2000 to "Candlelight",
        2700 to "Warm white",
        3000 to "Soft white",
        3500 to "Neutral white",
        4500 to "Cool white",
        6500 to "Daylight"
    )

    val WHITES: List<LightPreset> =
        WHITE_KELVINS.map { k -> LightPreset(WHITE_NAMES.getValue(k), ColorMath.kelvinToRgb(k)) }

    /** A full trip round the hue wheel at vivid saturation. */
    val COLORS: List<LightPreset> = listOf(
        LightPreset("Red", 0xFF1414),
        LightPreset("Coral", 0xFF4D38),
        LightPreset("Orange", 0xFF7A00),
        LightPreset("Amber", 0xFFA000),
        LightPreset("Gold", 0xFFC400),
        LightPreset("Yellow", 0xFFE500),
        LightPreset("Lime", 0xB6FF1A),
        LightPreset("Green", 0x28D828),
        LightPreset("Emerald", 0x00C853),
        LightPreset("Mint", 0x3DED97),
        LightPreset("Teal", 0x00C7B1),
        LightPreset("Aqua", 0x00E0D0),
        LightPreset("Cyan", 0x00C8FF),
        LightPreset("Sky", 0x3AA0FF),
        LightPreset("Azure", 0x1E6BFF),
        LightPreset("Blue", 0x1E3AFF),
        LightPreset("Indigo", 0x4B2FE0),
        LightPreset("Violet", 0x8A2BE2),
        LightPreset("Purple", 0xA93BE0),
        LightPreset("Magenta", 0xFF00C8),
        LightPreset("Rose", 0xFF3D8A),
        LightPreset("Pink", 0xFF74C4)
    )

    /** Whites first, then colours — the order the picker shows them in. */
    val ALL: List<LightPreset> = WHITES + COLORS

    /** The colour a fresh tag starts on before the user picks anything. */
    val DEFAULT_RGB: Int = ColorMath.kelvinToRgb(2700)

    /** The kelvin range the white-temperature slider covers. */
    const val MIN_KELVIN = 1800
    const val MAX_KELVIN = 6500

    fun nameFor(rgb: Int): String {
        ALL.firstOrNull { it.rgb == rgb }?.let { return it.name }
        ColorMath.nearestKelvin(rgb)?.let { return "White ${it}K" }
        return "Custom"
    }
}

/**
 * Colour maths shared by the providers. Govee takes a packed RGB integer; Tuya takes
 * hue 0-360, saturation 0-1000 and value 0-1000.
 */
object ColorMath {
    fun red(rgb: Int) = (rgb shr 16) and 0xFF
    fun green(rgb: Int) = (rgb shr 8) and 0xFF
    fun blue(rgb: Int) = rgb and 0xFF

    fun pack(r: Int, g: Int, b: Int): Int =
        (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

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

    /**
     * Black-body colour for a colour temperature in kelvin (Tanner Helland's approximation).
     * A warm-to-cool white slider maps straight onto this and is stored as a plain RGB, so it
     * needs no new provider capability.
     */
    fun kelvinToRgb(kelvin: Int): Int {
        val t = kelvin.coerceIn(1000, 15000) / 100.0
        val r = if (t <= 66.0) 255.0 else 329.698727446 * (t - 60.0).pow(-0.1332047592)
        val g = if (t <= 66.0) 99.4708025861 * ln(t) - 161.1195681661
        else 288.1221695283 * (t - 60.0).pow(-0.0755148492)
        val b = when {
            t >= 66.0 -> 255.0
            t <= 19.0 -> 0.0
            else -> 138.5177312231 * ln(t - 10.0) - 305.0447927307
        }
        fun c(x: Double) = x.coerceIn(0.0, 255.0).roundToInt()
        return pack(c(r), c(g), c(b))
    }

    /**
     * If [rgb] sits on (or very near) the white curve, the kelvin that produced it, else null.
     * Used only to name a stored colour; a saturated hue returns null and reads as "Custom".
     */
    fun nearestKelvin(rgb: Int): Int? {
        var best = -1
        var bestScore = Int.MAX_VALUE
        var k = 1800
        while (k <= 6800) {
            val c = kelvinToRgb(k)
            val dr = abs(red(c) - red(rgb))
            val dg = abs(green(c) - green(rgb))
            val db = abs(blue(c) - blue(rgb))
            val score = dr + dg + db
            if (score < bestScore && maxOf(dr, dg, db) <= 16) {
                bestScore = score
                best = k
            }
            k += 50
        }
        return if (best > 0 && bestScore <= 30) best else null
    }
}

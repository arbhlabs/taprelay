package com.arbhlabs.taprelay.haptics

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** How hard the pad buzzes. On/off motors (no amplitude control) feel strength as duration. */
enum class ControllerHapticStrength(val label: String, val amplitudeScale: Float, val onOffDurationScale: Float) {
    SUBTLE("Subtle", .45f, .7f),
    LIGHT("Light", .7f, .85f),
    NORMAL("Normal", 1f, 1f),
    STRONG("Strong", 1.35f, 1.2f),
    MAX("Max", 1.8f, 1.4f)
}

enum class ControllerHapticLength(val label: String, val scale: Float) {
    SHORT("Short", .6f), NORMAL("Normal", 1f), LONG("Long", 1.5f), EXTRA_LONG("Extra long", 2.2f)
}

enum class ControllerHapticStyle(val label: String, val hint: String) {
    SIGNATURES("Signatures", "Each mapping gets its own distinct pattern."),
    CLASSIC("Classic", "One rise-and-thud for every action."),
    SINGLE("Single tap", "One short, firm knock."),
    DOUBLE("Double tap", "Two quick knocks.")
}

/** Everything the owner can tune about controller rumble. Pure, so shaping is unit-tested. */
data class ControllerHapticSettings(
    val strength: ControllerHapticStrength = ControllerHapticStrength.NORMAL,
    val length: ControllerHapticLength = ControllerHapticLength.NORMAL,
    val style: ControllerHapticStyle = ControllerHapticStyle.SIGNATURES,
    val onSuccess: Boolean = true,
    val onFailure: Boolean = true,
    /** The D-pad light window's "limit reached" tick and "window closed" knocks. */
    val adjustmentCues: Boolean = true
) {
    fun plays(success: Boolean): Boolean = if (success) onSuccess else onFailure

    /** The fixed success pattern for the chosen style, or null when signatures pick it. */
    fun successPattern(): HapticChannelPattern? = when (style) {
        ControllerHapticStyle.SIGNATURES -> null
        ControllerHapticStyle.CLASSIC -> CLASSIC
        ControllerHapticStyle.SINGLE -> channel(listOf(0, 60), listOf(0, 230))
        ControllerHapticStyle.DOUBLE -> channel(listOf(0, 55, 70, 55), listOf(0, 230, 0, 230))
    }

    /** Applies strength and length to a waveform. Index 0 is the start delay and stays as-is. */
    fun shape(timings: List<Long>, amplitudes: List<Int>, amplitudeControl: Boolean): Pair<LongArray, IntArray> {
        val durationScale = length.scale * (if (amplitudeControl) 1f else strength.onOffDurationScale)
        val shapedTimings = timings.mapIndexed { i, ms ->
            if (i == 0) ms else (ms * durationScale).roundToLong().coerceAtLeast(if (amplitudes.getOrElse(i) { 0 } > 0) MIN_PULSE_MS else 0L)
        }.toLongArray()
        val shapedAmplitudes = amplitudes.map { value ->
            if (value <= 0) 0 else (value * strength.amplitudeScale).roundToInt().coerceIn(MIN_AMPLITUDE, 255)
        }.toIntArray()
        return shapedTimings to shapedAmplitudes
    }

    companion object {
        const val MIN_PULSE_MS = 12L
        const val MIN_AMPLITUDE = 20
        val CLASSIC = channel(listOf(0, 40, 20, 130, 90), listOf(0, 170, 0, 255, 90))
        /** Three unmistakable knocks, whatever the style: a failure must never feel like success. */
        val FAILURE = channel(listOf(0, 105, 75, 105, 75, 130), listOf(0, 255, 0, 255, 0, 255))

        private fun channel(timings: List<Long>, amplitudes: List<Int>) =
            HapticChannelPattern(HapticSide.CENTRE, timings, amplitudes)
    }
}

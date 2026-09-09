package com.arbhlabs.taprelay.controller

import kotlin.math.abs

enum class AdjustmentStick { LEFT, RIGHT }

data class AdjustmentConfig(
    val enabled: Boolean = false,
    val stick: AdjustmentStick = AdjustmentStick.RIGHT,
    val durationMs: Long = 8_000L,
    val deadZone: Float = 0.22f,
    val debounceMs: Long = 90L,
    val brightnessStep: Int = 5,
    val colorSwatches: List<Int> = listOf(0xFFFFFFFF.toInt(), 0xFFFFB300.toInt(), 0xFF42A5F5.toInt())
) {
    val safeDurationMs: Long get() = durationMs.coerceIn(500L, 60_000L)
    val safeDeadZone: Float get() = deadZone.coerceIn(0.05f, 0.8f)
}

sealed interface AdjustmentEvent {
    data class BrightnessDelta(val delta: Int) : AdjustmentEvent
    data class ColorSwatch(val rgb: Int) : AdjustmentEvent
}

/**
 * Pure state machine for the short controller adjustment window after a successful ON action.
 * It cannot arm for OFF/toggle actions, coalesces noisy stick reports, and naturally expires.
 */
class PostActivationAdjustmentSession(
    private val config: AdjustmentConfig,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private var expiresAt = 0L
    private var lastEmitAt = Long.MIN_VALUE
    private var armed = false

    fun start(poweredOn: Boolean) {
        armed = config.enabled && poweredOn
        expiresAt = if (armed) nowMs() + config.safeDurationMs else 0L
        lastEmitAt = Long.MIN_VALUE
    }

    fun stop() {
        armed = false
        expiresAt = 0L
    }

    fun isActive(): Boolean = armed && nowMs() < expiresAt

    fun onStick(x: Float, y: Float, currentBrightness: Int): AdjustmentEvent? {
        if (!isActive() || (lastEmitAt != Long.MIN_VALUE && nowMs() - lastEmitAt < config.debounceMs)) return null
        val axis = if (config.stick == AdjustmentStick.LEFT) x else y
        if (abs(axis) < config.safeDeadZone) return null
        lastEmitAt = nowMs()
        val delta = if (axis > 0f) config.brightnessStep else -config.brightnessStep
        return AdjustmentEvent.BrightnessDelta((currentBrightness + delta).coerceIn(1, 100) - currentBrightness)
    }

    fun onSwatch(index: Int): AdjustmentEvent.ColorSwatch? {
        if (!isActive() || (lastEmitAt != Long.MIN_VALUE && nowMs() - lastEmitAt < config.debounceMs)) return null
        val rgb = config.colorSwatches.getOrNull(index) ?: return null
        lastEmitAt = nowMs()
        return AdjustmentEvent.ColorSwatch(rgb)
    }
}

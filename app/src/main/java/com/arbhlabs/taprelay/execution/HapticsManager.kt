package com.arbhlabs.taprelay.execution

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Pixel-grade predefined haptics. Respects the OS haptics setting automatically. */
class HapticsManager(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun play(effect: Int) {
        if (!vibrator.hasVibrator()) return
        runCatching { vibrator.vibrate(VibrationEffect.createPredefined(effect)) }
    }

    fun vibrateClick() = play(VibrationEffect.EFFECT_CLICK)
    fun vibrateSuccess() = play(VibrationEffect.EFFECT_HEAVY_CLICK)
    fun vibrateError() = play(VibrationEffect.EFFECT_DOUBLE_CLICK)
    fun vibrateTick() = play(VibrationEffect.EFFECT_TICK)
}

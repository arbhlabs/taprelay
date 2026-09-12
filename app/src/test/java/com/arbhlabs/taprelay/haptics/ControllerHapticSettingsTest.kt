package com.arbhlabs.taprelay.haptics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerHapticSettingsTest {
    private val timings = listOf(0L, 100L, 50L, 100L)
    private val amplitudes = listOf(0, 200, 0, 100)

    @Test fun defaultsLeaveTheWaveformUntouched() {
        val (t, a) = ControllerHapticSettings().shape(timings, amplitudes, amplitudeControl = true)
        assertArrayEquals(longArrayOf(0, 100, 50, 100), t)
        assertArrayEquals(intArrayOf(0, 200, 0, 100), a)
    }

    @Test fun strengthScalesAmplitudeAndClampsToTheMotorRange() {
        val (_, max) = ControllerHapticSettings(strength = ControllerHapticStrength.MAX).shape(timings, amplitudes, true)
        assertArrayEquals(intArrayOf(0, 255, 0, 180), max)
        val (_, subtle) = ControllerHapticSettings(strength = ControllerHapticStrength.SUBTLE).shape(listOf(0L, 10L), listOf(0, 10), true)
        assertEquals(ControllerHapticSettings.MIN_AMPLITUDE, subtle[1]) // never silently inaudible
        assertEquals(0, subtle[0])
    }

    @Test fun lengthScalesEverySegmentButTheStartDelay() {
        val (t, _) = ControllerHapticSettings(length = ControllerHapticLength.LONG).shape(listOf(5L, 100L, 50L), listOf(0, 200, 0), true)
        assertArrayEquals(longArrayOf(5, 150, 75), t)
        val (short, _) = ControllerHapticSettings(length = ControllerHapticLength.SHORT).shape(listOf(0L, 10L), listOf(0, 200), true)
        assertEquals(ControllerHapticSettings.MIN_PULSE_MS, short[1])
    }

    @Test fun onOffMotorsFeelStrengthAsDuration() {
        val (t, _) = ControllerHapticSettings(strength = ControllerHapticStrength.MAX).shape(listOf(0L, 100L), listOf(0, 200), amplitudeControl = false)
        assertEquals(140L, t[1])
    }

    @Test fun styleAndEventSwitches() {
        assertNull(ControllerHapticSettings().successPattern())
        assertNotNull(ControllerHapticSettings(style = ControllerHapticStyle.DOUBLE).successPattern())
        val quietFailures = ControllerHapticSettings(onFailure = false)
        assertFalse(quietFailures.plays(success = false))
        assertEquals(true, quietFailures.plays(success = true))
    }
}

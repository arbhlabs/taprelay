package com.arbhlabs.taprelay.execution.phone

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import com.arbhlabs.taprelay.domain.model.PhoneAction

/**
 * Phone-side actions that work on a phone with nothing else attached to it.
 *
 * Media and volume go through [AudioManager], which routes a media-button press to whatever app is
 * currently playing - so one controller button pauses Spotify, YouTube Music or a podcast without
 * TapRelay integrating with any of them. The torch goes through [CameraManager]. None of these
 * needs a permission or an account.
 *
 * Do Not Disturb is the exception: Android gates it behind Notification Policy Access, a switch the
 * owner grants once in system settings. When it has not been granted the action fails with copy
 * that says what to do, rather than silently doing nothing.
 */
class PhoneController(private val context: Context) {

    private val notifications: NotificationManager?
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private val audio: AudioManager?
        get() = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val cameras: CameraManager?
        get() = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    // The torch has no "read current state" call, so we follow it. Registered lazily and kept for
    // the process lifetime; the callback is cheap and there is only ever one PhoneController.
    @Volatile private var torchOn = false
    private var torchCallbackRegistered = false
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) torchOn = enabled
        }
    }

    private val torchCameraId: String? by lazy {
        runCatching {
            val cm = cameras ?: return@runCatching null
            cm.cameraIdList.firstOrNull { id ->
                val c = cm.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    // ---- Do Not Disturb -------------------------------------------------------------------

    val hasDndAccess: Boolean
        get() = notifications?.isNotificationPolicyAccessGranted == true

    /** The system screen where the owner grants Do Not Disturb access. */
    fun dndAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** True when Do Not Disturb is currently on in any of its filtering modes. */
    fun isDndOn(): Boolean {
        val filter = notifications?.currentInterruptionFilter ?: return false
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    fun run(action: String): PhoneResult = when (action) {
        PhoneAction.MEDIA_PLAY_PAUSE -> media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play / pause sent")
        PhoneAction.MEDIA_NEXT -> media(KeyEvent.KEYCODE_MEDIA_NEXT, "Skipped forward")
        PhoneAction.MEDIA_PREVIOUS -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Skipped back")
        PhoneAction.VOLUME_UP -> volume(AudioManager.ADJUST_RAISE, "Volume up")
        PhoneAction.VOLUME_DOWN -> volume(AudioManager.ADJUST_LOWER, "Volume down")
        PhoneAction.VOLUME_MUTE_TOGGLE -> volume(AudioManager.ADJUST_TOGGLE_MUTE, "Mute toggled")
        PhoneAction.FLASHLIGHT_ON -> torch(TorchIntent.ON)
        PhoneAction.FLASHLIGHT_OFF -> torch(TorchIntent.OFF)
        PhoneAction.FLASHLIGHT_TOGGLE -> torch(TorchIntent.TOGGLE)
        PhoneAction.DND_ON, PhoneAction.DND_OFF, PhoneAction.DND_TOGGLE -> dnd(action)
        else -> PhoneResult.Failed("That phone action is no longer available.")
    }

    // ---- Media --------------------------------------------------------------------------

    private fun media(keyCode: Int, successCopy: String): PhoneResult {
        val am = audio ?: return PhoneResult.Failed("This phone can't do that.")
        return try {
            val now = android.os.SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
            // The event is delivered whether or not anything is listening; only the copy changes.
            val playing = runCatching { am.isMusicActive }.getOrDefault(false)
            PhoneResult.Done(
                if (playing || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) successCopy
                else "$successCopy · nothing is playing"
            )
        } catch (e: Exception) {
            PhoneResult.Failed("Couldn't reach your media player.")
        }
    }

    // ---- Volume ------------------------------------------------------------------------

    private fun volume(direction: Int, successCopy: String): PhoneResult {
        val am = audio ?: return PhoneResult.Failed("This phone can't do that.")
        return try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            PhoneResult.Done(successCopy)
        } catch (e: SecurityException) {
            // A fixed-volume device (some TVs, docked modes) refuses stream changes.
            PhoneResult.Failed("This phone won't let an app change the volume here.")
        } catch (e: Exception) {
            PhoneResult.Failed("Couldn't change the volume.")
        }
    }

    // ---- Torch ------------------------------------------------------------------------

    private enum class TorchIntent { ON, OFF, TOGGLE }

    private fun torch(intent: TorchIntent): PhoneResult {
        val cm = cameras ?: return PhoneResult.Failed("This phone has no flashlight.")
        val id = torchCameraId ?: return PhoneResult.Failed("This phone has no flashlight.")
        ensureTorchCallback(cm)
        val target = when (intent) {
            TorchIntent.ON -> true
            TorchIntent.OFF -> false
            TorchIntent.TOGGLE -> !torchOn
        }
        return try {
            cm.setTorchMode(id, target)
            torchOn = target
            PhoneResult.Done(if (target) "Flashlight on" else "Flashlight off")
        } catch (e: Exception) {
            // In use by the camera app, or the torch is unavailable right now.
            PhoneResult.Failed("The flashlight is busy right now.")
        }
    }

    private fun ensureTorchCallback(cm: CameraManager) {
        if (torchCallbackRegistered) return
        runCatching {
            cm.registerTorchCallback(torchCallback, null)
            torchCallbackRegistered = true
        }
    }

    // ---- Do Not Disturb ---------------------------------------------------------------

    private fun dnd(action: String): PhoneResult {
        val manager = notifications ?: return PhoneResult.Failed("This phone can't do that.")
        if (!manager.isNotificationPolicyAccessGranted) return PhoneResult.NeedsPermission
        val turnOn = when (action) {
            PhoneAction.DND_ON -> true
            PhoneAction.DND_OFF -> false
            PhoneAction.DND_TOGGLE -> !isDndOn()
            else -> return PhoneResult.Failed("That phone action is no longer available.")
        }
        return try {
            manager.setInterruptionFilter(
                if (turnOn) {
                    // Priority rather than total silence: alarms still ring, which is what
                    // somebody putting a bedtime tag by their bed actually wants.
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                }
            )
            PhoneResult.Done(if (turnOn) "Do Not Disturb on" else "Do Not Disturb off")
        } catch (e: SecurityException) {
            PhoneResult.NeedsPermission
        } catch (e: Exception) {
            PhoneResult.Failed("Couldn't change Do Not Disturb.")
        }
    }
}

sealed interface PhoneResult {
    data class Done(val summary: String) : PhoneResult
    data class Failed(val message: String) : PhoneResult

    /** Distinct from a failure: the owner can fix it, and the app can take them to the switch. */
    data object NeedsPermission : PhoneResult
}

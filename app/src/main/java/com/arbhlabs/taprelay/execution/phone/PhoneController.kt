package com.arbhlabs.taprelay.execution.phone

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.arbhlabs.taprelay.domain.model.PhoneAction

/**
 * Phone-side actions, which today means Do Not Disturb.
 *
 * Android gates this behind Notification Policy Access - a switch the owner grants once in system
 * settings. There is no way around that and no reason to want one: an app that could silence a
 * phone without asking is exactly what the permission exists to prevent. When access has not been
 * granted the action fails with copy that says what to do, rather than silently doing nothing.
 */
class PhoneController(private val context: Context) {

    private val notifications: NotificationManager?
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

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

    fun run(action: String): PhoneResult {
        val manager = notifications ?: return PhoneResult.Failed("This phone can't do that.")
        if (!manager.isNotificationPolicyAccessGranted) {
            return PhoneResult.NeedsPermission
        }
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

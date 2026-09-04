package com.arbhlabs.taprelay.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arbhlabs.taprelay.TapRelayApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android forgets every geofence when the device restarts, and again when the app is replaced,
 * so both are rebuilt from the database here. Without this a place trigger silently stops
 * working after a reboot, which is the classic way these features rot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        val app = context.applicationContext as? TapRelayApplication ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(9_000L) { app.services.geofenceManager.syncAll() }
            } finally {
                pending.finish()
            }
        }
    }
}

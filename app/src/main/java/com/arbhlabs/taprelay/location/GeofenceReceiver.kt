package com.arbhlabs.taprelay.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.data.local.entity.PlaceTransition
import com.arbhlabs.taprelay.data.local.entity.PlaceTriggerEntity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Where a crossed boundary becomes a smart-home action.
 *
 * The broadcast is held open with `goAsync()` until the provider call finishes, so the process is
 * not torn down mid-request. Nothing here can show UI: Android forbids background activity
 * starts, so a place always executes its item's action rather than opening a surface.
 */
class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "geofence event error code=${event.errorCode}")
            return
        }

        val transition = event.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            transition != Geofence.GEOFENCE_TRANSITION_EXIT
        ) return
        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        if (ids.isEmpty()) return

        val app = context.applicationContext as? TapRelayApplication ?: return
        val services = app.services
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                // A broadcast gets roughly ten seconds; a cloud call is well inside that.
                withTimeoutOrNull(TIMEOUT_MS) {
                    for (id in ids) {
                        val rowId = id.removePrefix(PREFIX).toLongOrNull() ?: continue
                        val trigger = services.placeTriggerDao.getById(rowId) ?: continue
                        if (!trigger.enabled) continue
                        // GPS drift on a boundary can produce a burst of crossings; one action
                        // per place per cooldown is what the owner actually meant.
                        val since = System.currentTimeMillis() - (trigger.lastFiredAt ?: 0L)
                        if (since < COOLDOWN_MS) {
                            Log.i(TAG, "place=${trigger.id} ignored, fired ${since}ms ago")
                            continue
                        }
                        val arriving = transition == Geofence.GEOFENCE_TRANSITION_ENTER
                        val tagId = trigger.tagIdFor(arriving) ?: continue

                        Log.i(
                            TAG,
                            "place=${trigger.id} transition=${nameOf(transition)} firing item"
                        )
                        runAction(services, tagId)
                        services.placeTriggerDao.markFired(trigger.id, System.currentTimeMillis())
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Suspends until the executor has reported an outcome, so `goAsync` is held long enough. */
    private suspend fun runAction(
        services: com.arbhlabs.taprelay.di.ServiceLocator,
        tagId: String
    ) = suspendCancellableCoroutine { cont ->
        services.actionExecutor.replay(tagId) { fb ->
            if (!fb.pending && cont.isActive) cont.resume(Unit)
        }
    }

    private fun nameOf(transition: Int) = when (transition) {
        Geofence.GEOFENCE_TRANSITION_ENTER -> "arrive"
        Geofence.GEOFENCE_TRANSITION_EXIT -> "leave"
        else -> "other"
    }

    private companion object {
        const val TAG = "TapRelayGeofence"
        const val PREFIX = "taprelay-place-"
        const val TIMEOUT_MS = 9_000L
        const val COOLDOWN_MS = 30_000L
    }
}

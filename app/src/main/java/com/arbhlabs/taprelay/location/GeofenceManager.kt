package com.arbhlabs.taprelay.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.arbhlabs.taprelay.data.local.dao.PlaceTriggerDao
import com.arbhlabs.taprelay.data.local.entity.PlaceTransition
import com.arbhlabs.taprelay.data.local.entity.PlaceTriggerEntity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Owns the real geofences. The database is the source of truth; this class makes Play Services
 * agree with it — on first registration, whenever a place changes, and again after a reboot,
 * because Android drops every geofence when the device restarts.
 */
class GeofenceManager(
    private val context: Context,
    private val dao: PlaceTriggerDao
) {
    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /** Fine location is enough to create geofences; background is what lets them fire while away. */
    fun hasForegroundLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(): Boolean {
        if (!hasForegroundLocation()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Rebuilds every registered geofence from the database. Safe to call repeatedly — it always
     * removes first, so a place that was edited or switched off cannot linger in Play Services.
     */
    @SuppressLint("MissingPermission")
    suspend fun syncAll(): Boolean = withContext(Dispatchers.IO) {
        if (!hasBackgroundLocation()) {
            Log.i(TAG, "background location not granted; geofences left unregistered")
            runCatching { await { client.removeGeofences(pendingIntent) } }
            return@withContext false
        }
        val triggers = dao.getEnabled()
        runCatching { await { client.removeGeofences(pendingIntent) } }
        if (triggers.isEmpty()) return@withContext true

        val fences = triggers.map { it.toGeofence() }
        val request = GeofencingRequest.Builder()
            // Fire straight away if the phone is already inside the circle when it is created,
            // so "when I get home" set up at home behaves the way it reads.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(fences)
            .build()

        runCatching { await { client.addGeofences(request, pendingIntent) } }
            .onFailure { Log.w(TAG, "addGeofences failed: ${it.message}") }
            .isSuccess
    }

    /**
     * A single fresh fix for "use my current location". Returns null rather than throwing when
     * permission is missing or the fix times out, so the caller can just say so.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): android.location.Location? = withContext(Dispatchers.IO) {
        if (!hasForegroundLocation()) return@withContext null
        runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            await { client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null) }
                ?: await { client.lastLocation }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    suspend fun removeAll() = withContext(Dispatchers.IO) {
        runCatching { await { client.removeGeofences(pendingIntent) } }
        Unit
    }

    private fun PlaceTriggerEntity.toGeofence(): Geofence {
        val transitions = when (transition) {
            PlaceTransition.ARRIVE -> Geofence.GEOFENCE_TRANSITION_ENTER
            PlaceTransition.LEAVE -> Geofence.GEOFENCE_TRANSITION_EXIT
            PlaceTransition.BOTH ->
                Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
        }
        return Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(latitude, longitude, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transitions)
            // A short dwell before EXIT cuts the false alarms you get from GPS drift indoors.
            .setLoiteringDelay(LOITER_MS)
            .setNotificationResponsiveness(RESPONSIVENESS_MS)
            .build()
    }

    /** Bridges a Play Services Task to a coroutine without pulling in kotlinx-play-services. */
    private suspend fun <T> await(block: () -> com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
            block()
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.cancel(it) }
        }

    private companion object {
        const val TAG = "TapRelayGeofence"
        const val REQUEST_CODE = 4711
        const val LOITER_MS = 30_000
        const val RESPONSIVENESS_MS = 60_000
    }
}

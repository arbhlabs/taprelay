package com.arbhlabs.taprelay

import android.app.Application
import com.arbhlabs.taprelay.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TapRelayApplication : Application() {

    lateinit var services: ServiceLocator
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        services = ServiceLocator(this)

        // Feedback preferences live on the shared managers, so the app, Remote Mode and the
        // NFC trampoline all honour them without each screen having to remember.
        scope.launch {
            services.preferences.phoneHaptics.collectLatest {
                services.haptics.enabled = it
                services.hapticSignatures.phoneFallbackEnabled = it
            }
        }
        scope.launch {
            services.preferences.controllerRumble.collectLatest {
                services.controllerManager.rumbleEnabled = it
                services.hapticSignatures.controllerEnabled = it
            }
        }
        scope.launch {
            services.preferences.automaticHapticSignatures.collectLatest {
                services.hapticSignatures.automaticEnabled = it
            }
        }

        // Location is usually granted after a place is created, and Android drops geofences on
        // reboot, so the registration is rebuilt from the database on every launch.
        scope.launch { runCatching { services.geofenceManager.syncAll() } }
    }
}

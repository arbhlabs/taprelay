package com.arbhlabs.taprelay

import android.app.Activity
import android.app.Application
import android.os.Bundle
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

        // Whether any TapRelay screen is in front - the light window only needs its own D-pad
        // pill when one is not, since TapRelay's screens already receive the D-pad.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumedScreens++ }
            override fun onActivityPaused(activity: Activity) { resumedScreens = (resumedScreens - 1).coerceAtLeast(0) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

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

    companion object {
        /** Count of resumed TapRelay activities; main thread only. */
        @Volatile
        var resumedScreens = 0
            private set
    }
}

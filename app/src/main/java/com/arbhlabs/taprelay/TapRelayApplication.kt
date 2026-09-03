package com.arbhlabs.taprelay

import android.app.Application
import com.arbhlabs.taprelay.di.ServiceLocator

class TapRelayApplication : Application() {

    lateinit var services: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        services = ServiceLocator(this)
    }
}

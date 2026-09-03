package com.arbhlabs.taprelay.di

import android.content.Context
import androidx.room.Room
import com.arbhlabs.taprelay.data.local.AppDatabase
import com.arbhlabs.taprelay.data.prefs.AppPreferences
import com.arbhlabs.taprelay.data.remote.govee.GoveeApiClient
import com.arbhlabs.taprelay.data.secure.SecureKeyStorage
import com.arbhlabs.taprelay.data.secure.TinkAeadManager
import com.arbhlabs.taprelay.domain.provider.GOVEE_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.GoveeCloudProvider
import com.arbhlabs.taprelay.domain.provider.SmartHomeProvider
import com.arbhlabs.taprelay.domain.repository.TagRepository
import com.arbhlabs.taprelay.execution.ActionExecutor
import com.arbhlabs.taprelay.execution.HapticsManager

/** Manual dependency container. Single instance, created in [com.arbhlabs.taprelay.TapRelayApplication]. */
class ServiceLocator(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        appContext, AppDatabase::class.java, "taprelay.db"
    ).fallbackToDestructiveMigration().build()

    val preferences = AppPreferences(appContext)

    private val aead = TinkAeadManager(appContext)
    val secureKeyStorage = SecureKeyStorage(appContext, aead)

    val tagRepository = TagRepository(database.tagDao())

    private val goveeApi = GoveeApiClient()
    val goveeProvider = GoveeCloudProvider(goveeApi, secureKeyStorage)

    val providers: Map<String, SmartHomeProvider> = mapOf(
        GOVEE_PROVIDER_ID to goveeProvider
    )

    val haptics = HapticsManager(appContext)

    val actionExecutor = ActionExecutor(
        tags = tagRepository,
        providers = { providers },
        haptics = haptics
    )
}

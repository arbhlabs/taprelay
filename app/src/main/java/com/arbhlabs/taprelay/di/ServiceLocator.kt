package com.arbhlabs.taprelay.di

import android.content.Context
import androidx.room.Room
import com.arbhlabs.taprelay.data.local.AppDatabase
import com.arbhlabs.taprelay.data.prefs.AppPreferences
import com.arbhlabs.taprelay.data.remote.govee.GoveeApiClient
import com.arbhlabs.taprelay.data.remote.sensibo.SensiboApiClient
import com.arbhlabs.taprelay.data.secure.SecureKeyStorage
import com.arbhlabs.taprelay.data.secure.TinkAeadManager
import com.arbhlabs.taprelay.data.remote.tuya.TuyaApiClient
import com.arbhlabs.taprelay.domain.provider.GOVEE_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.GoveeCloudProvider
import com.arbhlabs.taprelay.domain.provider.SENSIBO_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.SensiboProvider
import com.arbhlabs.taprelay.domain.provider.SmartHomeProvider
import com.arbhlabs.taprelay.domain.provider.TUYA_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.TuyaProvider
import com.arbhlabs.taprelay.domain.repository.TagRepository
import com.arbhlabs.taprelay.execution.ActionExecutor
import com.arbhlabs.taprelay.execution.HapticsManager
import com.arbhlabs.taprelay.monetization.EntitlementRepository

/** Manual dependency container. Single instance, created in [com.arbhlabs.taprelay.TapRelayApplication]. */
class ServiceLocator(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        appContext, AppDatabase::class.java, "taprelay.db"
    ).addMigrations(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
        AppDatabase.MIGRATION_4_5
    )
     // No destructive fallback: a missing migration must fail loudly rather than
     // silently deleting every tag mapping the owner has set up.
     .build()

    val preferences = AppPreferences(appContext)

    private val aead = TinkAeadManager(appContext)
    val secureKeyStorage = SecureKeyStorage(appContext, aead)

    val tagRepository = TagRepository(database.tagDao())
    val tapLogDao = database.tapLogDao()

    val entitlementRepository = EntitlementRepository(appContext, secureKeyStorage)

    private val goveeApi = GoveeApiClient()
    val goveeProvider = GoveeCloudProvider(goveeApi, secureKeyStorage)

    private val tuyaApi = TuyaApiClient()
    val tuyaProvider = TuyaProvider(tuyaApi, secureKeyStorage)

    private val sensiboApi = SensiboApiClient()
    val sensiboProvider = SensiboProvider(sensiboApi, secureKeyStorage)

    val providers: Map<String, SmartHomeProvider> = mapOf(
        GOVEE_PROVIDER_ID to goveeProvider,
        TUYA_PROVIDER_ID to tuyaProvider,
        SENSIBO_PROVIDER_ID to sensiboProvider
    )

    val haptics = HapticsManager(appContext)

    val actionExecutor = ActionExecutor(
        tags = tagRepository,
        providers = { providers },
        haptics = haptics,
        tapLogDao = tapLogDao,
        entitlements = entitlementRepository
    )
}

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
import com.arbhlabs.taprelay.trigger.TriggerRouter

/** Manual dependency container. Single instance, created in [com.arbhlabs.taprelay.TapRelayApplication]. */
class ServiceLocator(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        appContext, AppDatabase::class.java, "taprelay.db"
    ).addMigrations(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
        AppDatabase.MIGRATION_4_5,
        AppDatabase.MIGRATION_5_6,
        AppDatabase.MIGRATION_6_7,
        AppDatabase.MIGRATION_7_8,
        AppDatabase.MIGRATION_8_9,
        AppDatabase.MIGRATION_9_10
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

    /** Cross-app logging into LastDose. Absent LastDose, every call simply reports unavailable. */
    val lastDoseClient = com.arbhlabs.taprelay.execution.lastdose.LastDoseClient(appContext)

    val actionExecutor = ActionExecutor(
        tags = tagRepository,
        providers = { providers },
        haptics = haptics,
        tapLogDao = tapLogDao,
        entitlements = entitlementRepository,
        lastDose = lastDoseClient
    )

    /** trigger -> item -> activation mode -> execute / open item / Quick Controls. */
    val triggerRouter = TriggerRouter(
        tags = tagRepository,
        executor = actionExecutor
    )

    val controllerMappingDao = database.controllerMappingDao()
    val placeTriggerDao = database.placeTriggerDao()

    val geofenceManager = com.arbhlabs.taprelay.location.GeofenceManager(
        context = appContext,
        dao = placeTriggerDao
    )

    val controllerManager = com.arbhlabs.taprelay.controller.ControllerManager(
        context = appContext,
        mappingDao = controllerMappingDao,
        triggerRouter = triggerRouter,
        haptics = haptics
    )
}

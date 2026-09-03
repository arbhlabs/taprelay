package com.arbhlabs.taprelay.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPrefs by preferencesDataStore(name = "app_prefs")

class AppPreferences(private val context: Context) {
    private val onboardedKey = booleanPreferencesKey("onboarding_complete")

    val onboardingComplete: Flow<Boolean> =
        context.appPrefs.data.map { it[onboardedKey] ?: false }

    suspend fun setOnboardingComplete(value: Boolean) {
        context.appPrefs.edit { it[onboardedKey] = value }
    }
}

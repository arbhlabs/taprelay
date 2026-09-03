package com.arbhlabs.taprelay.data.secure

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_keys")

class SecureKeyStorage(
    private val context: Context,
    private val aeadManager: TinkAeadManager
) {
    private val GOVEE_API_KEY = stringPreferencesKey("govee_api_key")

    fun getGoveeApiKey(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[GOVEE_API_KEY]?.let { encryptedKey ->
                try {
                    aeadManager.decrypt(encryptedKey)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    suspend fun saveGoveeApiKey(apiKey: String) {
        val encryptedKey = aeadManager.encrypt(apiKey)
        context.dataStore.edit { preferences ->
            preferences[GOVEE_API_KEY] = encryptedKey
        }
    }

    suspend fun clearGoveeApiKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(GOVEE_API_KEY)
        }
    }
}

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
    private val TUYA_ACCESS_ID = stringPreferencesKey("tuya_access_id")
    private val TUYA_ACCESS_SECRET = stringPreferencesKey("tuya_access_secret")
    private val TUYA_REGION = stringPreferencesKey("tuya_region")
    private val TUYA_UID = stringPreferencesKey("tuya_uid")

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

    fun getTuyaCredentials(): Flow<TuyaCredentials?> {
        return context.dataStore.data.map { preferences ->
            val encId = preferences[TUYA_ACCESS_ID] ?: return@map null
            val encSec = preferences[TUYA_ACCESS_SECRET] ?: return@map null
            val encReg = preferences[TUYA_REGION]
            val encUid = preferences[TUYA_UID]
            try {
                TuyaCredentials(
                    accessId = aeadManager.decrypt(encId),
                    accessSecret = aeadManager.decrypt(encSec),
                    region = encReg?.let { aeadManager.decrypt(it) } ?: "us",
                    uid = encUid?.let { aeadManager.decrypt(it) } ?: ""
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun saveTuyaCredentials(creds: TuyaCredentials) {
        val encId = aeadManager.encrypt(creds.accessId)
        val encSec = aeadManager.encrypt(creds.accessSecret)
        val encReg = aeadManager.encrypt(creds.region)
        val encUid = aeadManager.encrypt(creds.uid)
        context.dataStore.edit { preferences ->
            preferences[TUYA_ACCESS_ID] = encId
            preferences[TUYA_ACCESS_SECRET] = encSec
            preferences[TUYA_REGION] = encReg
            preferences[TUYA_UID] = encUid
        }
    }

    suspend fun clearTuyaCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(TUYA_ACCESS_ID)
            preferences.remove(TUYA_ACCESS_SECRET)
            preferences.remove(TUYA_REGION)
            preferences.remove(TUYA_UID)
        }
    }
}

data class TuyaCredentials(
    val accessId: String,
    val accessSecret: String,
    val region: String = "us",
    val uid: String = ""
)

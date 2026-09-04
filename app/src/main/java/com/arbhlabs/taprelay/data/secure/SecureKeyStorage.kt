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
    private val SENSIBO_API_KEY = stringPreferencesKey("sensibo_api_key")
    private val PRO_KEY = stringPreferencesKey("pro_key")
    private val PRO_TIER = stringPreferencesKey("pro_tier")
    private val PRO_EXPIRES = stringPreferencesKey("pro_expires")
    private val PRO_SIG = stringPreferencesKey("pro_sig")

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

    fun getSensiboApiKey(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[SENSIBO_API_KEY]?.let { encryptedKey ->
                try {
                    aeadManager.decrypt(encryptedKey)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    suspend fun saveSensiboApiKey(apiKey: String) {
        val encryptedKey = aeadManager.encrypt(apiKey)
        context.dataStore.edit { preferences ->
            preferences[SENSIBO_API_KEY] = encryptedKey
        }
    }

    suspend fun clearSensiboApiKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(SENSIBO_API_KEY)
        }
    }

    fun getProLicense(): Flow<ProLicense?> {
        return context.dataStore.data.map { preferences ->
            val keyEnc = preferences[PRO_KEY] ?: return@map null
            val tierEnc = preferences[PRO_TIER]
            val expEnc = preferences[PRO_EXPIRES]
            val sigEnc = preferences[PRO_SIG] ?: return@map null
            try {
                ProLicense(
                    licenseKey = aeadManager.decrypt(keyEnc),
                    tier = tierEnc?.let { aeadManager.decrypt(it) } ?: "pro",
                    expiresAt = expEnc?.let { aeadManager.decrypt(it).toLongOrNull() } ?: 0L,
                    signature = aeadManager.decrypt(sigEnc)
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun saveProLicense(license: ProLicense) {
        val keyEnc = aeadManager.encrypt(license.licenseKey)
        val tierEnc = aeadManager.encrypt(license.tier)
        val expEnc = aeadManager.encrypt(license.expiresAt.toString())
        val sigEnc = aeadManager.encrypt(license.signature)
        context.dataStore.edit { preferences ->
            preferences[PRO_KEY] = keyEnc
            preferences[PRO_TIER] = tierEnc
            preferences[PRO_EXPIRES] = expEnc
            preferences[PRO_SIG] = sigEnc
        }
    }

    suspend fun clearProLicense() {
        context.dataStore.edit { preferences ->
            preferences.remove(PRO_KEY)
            preferences.remove(PRO_TIER)
            preferences.remove(PRO_EXPIRES)
            preferences.remove(PRO_SIG)
        }
    }
}

data class ProLicense(
    val licenseKey: String,
    val tier: String = "pro",
    val expiresAt: Long = 0L,
    val signature: String = ""
)

data class TuyaCredentials(
    val accessId: String,
    val accessSecret: String,
    val region: String = "us",
    val uid: String = ""
)

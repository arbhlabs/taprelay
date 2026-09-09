package com.arbhlabs.taprelay.data.secure

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
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
    private val HA_BASE_URL = stringPreferencesKey("ha_base_url")
    private val HA_TOKEN = stringPreferencesKey("ha_token")
    private val PRO_KEY = stringPreferencesKey("pro_key")
    private val PRO_TIER = stringPreferencesKey("pro_tier")
    private val PRO_EXPIRES = stringPreferencesKey("pro_expires")
    private val PRO_SIG = stringPreferencesKey("pro_sig")
    private val PC_RELAY_URL = stringPreferencesKey("pc_relay_url")
    private val PC_RELAY_TOKEN = stringPreferencesKey("pc_relay_token")
    private val PC_RELAY_OWNER = stringPreferencesKey("pc_relay_owner")

    fun getPcRelayCredentials(): Flow<PcRelayCredentials?> =
        context.dataStore.data.map { preferences ->
            val url = preferences[PC_RELAY_URL] ?: return@map null
            val token = preferences[PC_RELAY_TOKEN] ?: return@map null
            val owner = preferences[PC_RELAY_OWNER] ?: return@map null
            runCatching {
                PcRelayCredentials(
                    baseUrl = aeadManager.decrypt(url),
                    token = aeadManager.decrypt(token),
                    ownerId = aeadManager.decrypt(owner)
                )
            }.getOrNull()
        }.flowOn(Dispatchers.IO)

    suspend fun savePcRelayCredentials(credentials: PcRelayCredentials) {
        context.dataStore.edit { preferences ->
            preferences[PC_RELAY_URL] = aeadManager.encrypt(credentials.baseUrl)
            preferences[PC_RELAY_TOKEN] = aeadManager.encrypt(credentials.token)
            preferences[PC_RELAY_OWNER] = aeadManager.encrypt(credentials.ownerId)
        }
    }

    suspend fun clearPcRelayCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(PC_RELAY_URL)
            preferences.remove(PC_RELAY_TOKEN)
            preferences.remove(PC_RELAY_OWNER)
        }
    }

    fun getGoveeApiKey(): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[GOVEE_API_KEY]?.let { encryptedKey ->
                try {
                    aeadManager.decrypt(encryptedKey)
                } catch (e: Exception) {
                    null
                }
            }
        }.flowOn(Dispatchers.IO)
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
        }.flowOn(Dispatchers.IO)
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
        }.flowOn(Dispatchers.IO)
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

    /**
     * The owner's own Home Assistant instance. The long-lived access token is full control of
     * their home, so it is encrypted exactly like the Tuya secret and never leaves the phone.
     */
    fun getHomeAssistantCredentials(): Flow<HomeAssistantCredentials?> {
        return context.dataStore.data.map { preferences ->
            val encUrl = preferences[HA_BASE_URL] ?: return@map null
            val encToken = preferences[HA_TOKEN] ?: return@map null
            try {
                HomeAssistantCredentials(
                    baseUrl = aeadManager.decrypt(encUrl),
                    token = aeadManager.decrypt(encToken)
                )
            } catch (e: Exception) {
                null
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun saveHomeAssistantCredentials(creds: HomeAssistantCredentials) {
        val encUrl = aeadManager.encrypt(creds.baseUrl)
        val encToken = aeadManager.encrypt(creds.token)
        context.dataStore.edit { preferences ->
            preferences[HA_BASE_URL] = encUrl
            preferences[HA_TOKEN] = encToken
        }
    }

    suspend fun clearHomeAssistantCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(HA_BASE_URL)
            preferences.remove(HA_TOKEN)
        }
    }

    /**
     * The credential a single web-request item sends, kept out of the tags table.
     *
     * The tags row holds the header *name* so the editor can show "Authorization" without
     * decrypting anything; only the value lives here. That is also what keeps a shared or
     * exported item from carrying somebody's key with it.
     */
    fun getWebhookSecret(tagId: String): Flow<String?> {
        val key = stringPreferencesKey(webhookSecretKey(tagId))
        return context.dataStore.data.map { preferences ->
            preferences[key]?.let { runCatching { aeadManager.decrypt(it) }.getOrNull() }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun saveWebhookSecret(tagId: String, secret: String) {
        val encrypted = aeadManager.encrypt(secret)
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey(webhookSecretKey(tagId))] = encrypted
        }
    }

    suspend fun clearWebhookSecret(tagId: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(webhookSecretKey(tagId)))
        }
    }

    private fun webhookSecretKey(tagId: String) = "webhook_secret_$tagId"

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
        }.flowOn(Dispatchers.IO)
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

/** The address of the owner's own Home Assistant and the token they issued for TapRelay. */
data class HomeAssistantCredentials(
    val baseUrl: String,
    val token: String
)

data class PcRelayCredentials(
    val baseUrl: String,
    val token: String,
    val ownerId: String
)

data class TuyaCredentials(
    val accessId: String,
    val accessSecret: String,
    val region: String = "us",
    val uid: String = ""
)

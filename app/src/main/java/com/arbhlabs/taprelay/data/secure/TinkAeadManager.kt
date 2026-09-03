package com.arbhlabs.taprelay.data.secure

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.util.Base64

class TinkAeadManager(context: Context) {
    private val aead: Aead

    init {
        AeadConfig.register()
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle

        aead = keysetHandle.getPrimitive(Aead::class.java)
    }

    fun encrypt(plaintext: String): String {
        val encryptedBytes = aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA)
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    fun decrypt(ciphertextBase64: String): String {
        val decodedBytes = Base64.getDecoder().decode(ciphertextBase64)
        val decryptedBytes = aead.decrypt(decodedBytes, ASSOCIATED_DATA)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    companion object {
        private const val KEYSET_NAME = "taprelay_keyset"
        private const val PREF_FILE_NAME = "taprelay_keyset_prefs"
        private const val MASTER_KEY_URI = "android-keystore://taprelay_master_key"
        private val ASSOCIATED_DATA = "TapRelayKeyBinding".toByteArray(Charsets.UTF_8)
    }
}

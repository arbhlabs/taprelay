package com.arbhlabs.taprelay.data.remote.tuya

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TuyaSigner {
    private const val HMAC_SHA256 = "HmacSHA256"
    private const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    fun sha256(content: String): String {
        if (content.isEmpty()) return EMPTY_SHA256
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sign(
        clientId: String,
        secret: String,
        t: String,
        nonce: String = "",
        httpMethod: String,
        url: String,
        body: String = "",
        accessToken: String? = null
    ): String {
        val contentHash = sha256(body)
        val stringToSign = buildString {
            append(httpMethod.uppercase())
            append('\n')
            append(contentHash)
            append("\n\n") // No signed custom headers.
            append(url)
        }
        val strToHash = buildString {
            append(clientId)
            if (!accessToken.isNullOrEmpty()) append(accessToken)
            append(t)
            if (nonce.isNotEmpty()) append(nonce)
            append(stringToSign)
        }
        val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_SHA256)
        val mac = Mac.getInstance(HMAC_SHA256).apply { init(keySpec) }
        val rawHmac = mac.doFinal(strToHash.toByteArray(Charsets.UTF_8))
        return rawHmac.joinToString("") { "%02X".format(it) }
    }
}

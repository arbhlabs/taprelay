package com.arbhlabs.taprelay.nfc

import android.net.Uri
import java.util.UUID

object NfcPayloadParser {
    const val HOST = "taprelay.app"
    private const val UUID_PATTERN =
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"
    private val UUID_V4 = Regex("^$UUID_PATTERN$")
    private val URI_REGEX = Regex("^https://taprelay\\.app/t/($UUID_PATTERN)$")

    /** Extracts the opaque tag id from a scanned URI, or null if it is not a valid TapRelay tag. */
    fun parseUuidFromUri(uri: Uri?): String? {
        if (uri == null) return null
        return parseUuidFromString(uri.toString())
    }

    fun parseUuidFromString(value: String?): String? {
        if (value == null) return null
        return URI_REGEX.matchEntire(value.trim())?.groupValues?.get(1)?.lowercase()
    }

    fun isValidTagId(id: String?): Boolean = id != null && UUID_V4.matches(id)

    fun newTagId(): String = UUID.randomUUID().toString()

    fun urlFor(tagId: String): String = "https://$HOST/t/$tagId"
}

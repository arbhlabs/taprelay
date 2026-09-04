package com.arbhlabs.taprelay.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.os.PatternMatcher
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import java.nio.charset.StandardCharsets

/** What TapRelay found when it looked at a physical tag. */
sealed interface TagInspection {
    /** Writable and either unformatted or holds no real records — safe to provision silently. */
    data object Blank : TagInspection
    /** Already carries a valid TapRelay identity. */
    data class Provisioned(val tagId: String) : TagInspection
    /** Writable, but already holds unrelated NDEF data — overwrite needs confirmation. */
    data object ForeignData : TagInspection
    /** Physically present but locked / not writable. */
    data object NotWritable : TagInspection
    /** Not an NDEF-capable tag TapRelay can use. */
    data object Unsupported : TagInspection
}

class NfcManager(private val activity: Activity) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val isSupported get() = adapter != null
    val isEnabled get() = adapter?.isEnabled == true

    fun enableReader(onTag: (Tag) -> Unit) {
        // NOTE: FLAG_READER_SKIP_NDEF_CHECK is deliberately NOT set. Skipping the check leaves
        // the Ndef / NdefFormatable techs off the Tag, which broke provisioning of blank stickers.
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        adapter?.enableReaderMode(activity, { tag -> onTag(tag) }, flags, extras)
    }

    fun disableReader() = adapter?.disableReaderMode(activity)

    /**
     * Enables selective foreground dispatch for TapRelay tags only.
     * When active, unrelated NFC tags (such as LastDose medication tags)
     * are NOT intercepted and fall through directly to the system intent dispatcher.
     */
    fun enableForegroundDispatch(targetActivity: Activity) {
        val intent = Intent(targetActivity, targetActivity.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            targetActivity,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val filter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            addDataScheme("https")
            addDataAuthority(NfcPayloadParser.HOST, null)
            addDataPath("/t/", PatternMatcher.PATTERN_PREFIX)
        }
        adapter?.enableForegroundDispatch(targetActivity, pendingIntent, arrayOf(filter), null)
    }

    fun disableForegroundDispatch(targetActivity: Activity) {
        adapter?.disableForegroundDispatch(targetActivity)
    }

    /**
     * Identifies if a tag belongs to LastDose (carrying arbhlabs.com:ldtag or com.lastdose.app AAR).
     */
    fun isLastDoseTag(tag: Tag): Boolean {
        val ndef = Ndef.get(tag) ?: return false
        return try {
            ndef.connect()
            val message = ndef.cachedNdefMessage ?: runCatching { ndef.ndefMessage }.getOrNull()
            runCatching { ndef.close() }
            message?.let { isLastDoseMessage(it) } ?: false
        } catch (e: Exception) {
            runCatching { ndef.close() }
            false
        }
    }

    fun isLastDoseMessage(message: NdefMessage): Boolean {
        val records = message.records ?: return false
        for (rec in records) {
            val typeStr = runCatching { String(rec.type, StandardCharsets.US_ASCII) }.getOrNull()?.lowercase() ?: ""
            val payloadStr = runCatching { String(rec.payload, StandardCharsets.UTF_8) }.getOrNull()?.lowercase() ?: ""
            if (typeStr.contains("ldtag") ||
                typeStr.contains("arbhlabs.com:ldtag") ||
                payloadStr.contains("com.lastdose.app") ||
                payloadStr.contains("arbhlabs.com:ldtag")
            ) {
                return true
            }
        }
        return false
    }

    /** Classify a tag for the Add Tag flow. Never throws. */
    fun inspect(tag: Tag): TagInspection {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                val message = ndef.cachedNdefMessage ?: runCatching { ndef.ndefMessage }.getOrNull()
                val writable = ndef.isWritable
                runCatching { ndef.close() }
                val id = message?.let { idFromMessage(it) }
                when {
                    id != null -> TagInspection.Provisioned(id)
                    isEmptyMessage(message) && writable -> TagInspection.Blank
                    !writable -> TagInspection.NotWritable
                    else -> TagInspection.ForeignData
                }
            } catch (e: Exception) {
                runCatching { ndef.close() }
                TagInspection.Unsupported
            }
        }
        return if (NdefFormatable.get(tag) != null) TagInspection.Blank else TagInspection.Unsupported
    }

    /** Reads a TapRelay tag id from an already-written tag, or null. Never throws. */
    fun readTagId(tag: Tag): String? = when (val i = inspect(tag)) {
        is TagInspection.Provisioned -> i.tagId
        else -> null
    }

    /**
     * Writes the opaque App Link for [tagId] and reads it back to confirm.
     * Throws [TapException] on any failure.
     */
    fun writeTagId(tag: Tag, tagId: String, lock: Boolean) {
        val message = NdefMessage(
            arrayOf(
                NdefRecord.createUri(NfcPayloadParser.urlFor(tagId)),
                NdefRecord.createApplicationRecord("com.arbhlabs.taprelay")
            )
        )
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) throw TapException(TapError.TAG_READ_ONLY)
                if (ndef.maxSize < message.byteArrayLength) throw TapException(TapError.TAG_TOO_SMALL)
                ndef.writeNdefMessage(message)
                val readBack = runCatching { ndef.ndefMessage }.getOrNull()
                if (lock && ndef.canMakeReadOnly()) ndef.makeReadOnly()
                runCatching { ndef.close() }
                verify(readBack, tagId)
                return
            }
            val formatable = NdefFormatable.get(tag) ?: throw TapException(TapError.TAG_UNSUPPORTED)
            formatable.connect()
            if (lock) formatable.formatReadOnly(message) else formatable.format(message)
            runCatching { formatable.close() }
        } catch (e: TapException) {
            throw e
        } catch (e: TagLostException) {
            throw TapException(TapError.WRITE_INTERRUPTED)
        } catch (e: Exception) {
            throw TapException(TapError.WRITE_INTERRUPTED)
        }
    }

    private fun verify(readBack: NdefMessage?, expectedId: String) {
        if (readBack == null) return // some tags can't be re-read while still connected; trust the write
        if (idFromMessage(readBack) != expectedId) throw TapException(TapError.WRITE_INTERRUPTED)
    }

    private fun idFromMessage(message: NdefMessage): String? =
        message.records?.firstNotNullOfOrNull { rec ->
            NfcPayloadParser.parseUuidFromString(runCatching { rec.toUri()?.toString() }.getOrNull())
        }

    private fun isEmptyMessage(message: NdefMessage?): Boolean {
        val records = message?.records ?: return true
        return records.isEmpty() || records.all { it.tnf == NdefRecord.TNF_EMPTY }
    }
}

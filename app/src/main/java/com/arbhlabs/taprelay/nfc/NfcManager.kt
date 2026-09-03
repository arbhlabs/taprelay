package com.arbhlabs.taprelay.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException

class NfcManager(private val activity: Activity) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val isSupported get() = adapter != null
    val isEnabled get() = adapter?.isEnabled == true

    fun enableReader(onTag: (Tag) -> Unit) {
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }
        adapter?.enableReaderMode(activity, { tag -> onTag(tag) }, flags, extras)
    }

    fun disableReader() = adapter?.disableReaderMode(activity)

    /** Reads a TapRelay tag id from an already-written tag, or null. Never throws. */
    fun readTagId(tag: Tag): String? = runCatching {
        val ndef = Ndef.get(tag) ?: return null
        ndef.connect()
        val msg = ndef.cachedNdefMessage ?: ndef.ndefMessage
        ndef.close()
        msg?.records?.firstNotNullOfOrNull { rec ->
            NfcPayloadParser.parseUuidFromString(uriOf(rec))
        }
    }.getOrNull()

    private fun uriOf(record: NdefRecord): String? = runCatching { record.toUri()?.toString() }.getOrNull()

    /** Writes the opaque App Link for [tagId]. Throws [TapException] on any failure. */
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
                if (lock && ndef.canMakeReadOnly()) ndef.makeReadOnly()
                ndef.close()
                return
            }
            val formatable = NdefFormatable.get(tag) ?: throw TapException(TapError.TAG_MALFORMED)
            formatable.connect()
            if (lock) formatable.formatReadOnly(message) else formatable.format(message)
            formatable.close()
        } catch (e: TapException) {
            throw e
        } catch (e: TagLostException) {
            throw TapException(TapError.WRITE_INTERRUPTED)
        } catch (e: Exception) {
            throw TapException(TapError.WRITE_INTERRUPTED)
        }
    }
}

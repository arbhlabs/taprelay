package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.nfc.TagInspection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NfcInspectionTest {

    @Test
    fun tag_inspection_types_exist() {
        val provisioned = TagInspection.Provisioned("test-uuid")
        val blank = TagInspection.Blank
        val foreign = TagInspection.ForeignData
        val notWritable = TagInspection.NotWritable
        val unsupported = TagInspection.Unsupported

        assertEquals("test-uuid", provisioned.tagId)
        assertEquals(TagInspection.Blank, blank)
        assertEquals(TagInspection.ForeignData, foreign)
        assertEquals(TagInspection.NotWritable, notWritable)
        assertEquals(TagInspection.Unsupported, unsupported)
    }

    @Test
    fun error_messages_are_user_friendly() {
        assertEquals("This tag is locked and can't be written.", TapError.TAG_READ_ONLY.message)
        assertEquals("This tag is not supported by TapRelay.", TapError.TAG_UNSUPPORTED.message)
        assertEquals("Your Smart Life connection needs to be reconnected.", TapError.AUTH_TUYA.message)

        // Ensure zero jargon
        val banned = listOf("http", "json", "rest", "ndef", "mac", "uuid", "api", "401", "403", "500")
        for (msg in listOf(TapError.TAG_READ_ONLY.message, TapError.TAG_UNSUPPORTED.message, TapError.AUTH_TUYA.message)) {
            val lower = msg.lowercase()
            for (w in banned) {
                assertFalse("Error message '' contains ''", lower.contains(w))
            }
        }
    }
}

package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.nfc.NfcPayloadParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcPayloadParserTest {

    private val valid = "7b9e8a12-4c31-4a2f-9812-b45f78c910de"

    @Test fun parses_valid_app_link() {
        assertEquals(valid, NfcPayloadParser.parseUuidFromString("https://taprelay.app/t/$valid"))
    }

    @Test fun rejects_wrong_host() {
        assertNull(NfcPayloadParser.parseUuidFromString("https://evil.example/t/$valid"))
    }

    @Test fun rejects_http_scheme() {
        assertNull(NfcPayloadParser.parseUuidFromString("http://taprelay.app/t/$valid"))
    }

    @Test fun rejects_non_v4_uuid() {
        assertNull(NfcPayloadParser.parseUuidFromString("https://taprelay.app/t/00000000-0000-0000-0000-000000000000"))
    }

    @Test fun rejects_sql_injection_payload() {
        assertNull(NfcPayloadParser.parseUuidFromString("https://taprelay.app/t/$valid';DROP TABLE tags;--"))
    }

    @Test fun rejects_path_traversal() {
        assertNull(NfcPayloadParser.parseUuidFromString("https://taprelay.app/t/../../secret"))
    }

    @Test fun generated_ids_are_valid_and_roundtrip() {
        val id = NfcPayloadParser.newTagId()
        assertTrue(NfcPayloadParser.isValidTagId(id))
        assertEquals(id, NfcPayloadParser.parseUuidFromString(NfcPayloadParser.urlFor(id)))
    }

    @Test fun blank_and_null_are_rejected() {
        assertNull(NfcPayloadParser.parseUuidFromString(null))
        assertFalse(NfcPayloadParser.isValidTagId(null))
        assertFalse(NfcPayloadParser.isValidTagId(""))
    }
}

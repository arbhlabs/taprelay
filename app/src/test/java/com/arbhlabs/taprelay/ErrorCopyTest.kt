package com.arbhlabs.taprelay

import com.arbhlabs.taprelay.domain.model.TapError
import org.junit.Assert.assertFalse
import org.junit.Test

/** Guardrail: user-facing error copy must never leak technical jargon. */
class ErrorCopyTest {

    private val banned = listOf(
        "http", "json", "rest", "header", "mac", "uuid", "ndef", "api", "endpoint",
        "token", "oauth", "null", "exception", "429", "401", "500", "webhook", "intent"
    )

    @Test fun no_jargon_in_any_error_message() {
        for (err in TapError.entries) {
            val lower = err.message.lowercase()
            for (word in banned) {
                assertFalse(
                    "TapError.${err.name} leaks \"$word\": \"${err.message}\"",
                    Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(lower)
                )
            }
        }
    }
}

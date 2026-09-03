package com.arbhlabs.taprelay.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.execution.TapFeedback
import com.arbhlabs.taprelay.nfc.NfcPayloadParser
import com.arbhlabs.taprelay.ui.components.TapRelayPill
import com.arbhlabs.taprelay.ui.theme.TapRelayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Translucent, no-history activity that receives the App Link when a programmed tag is
 * tapped while TapRelay is not in the foreground. Fires the action and shows the pill.
 */
class NfcTrampolineActivity : ComponentActivity() {

    private var feedback by mutableStateOf<TapFeedback?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TapRelayTheme {
                TapRelayPill(feedback = feedback, onDismiss = { finish() }, modifier = Modifier.fillMaxSize())
            }
        }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val id = NfcPayloadParser.parseUuidFromUri(intent?.data)
        if (id == null) { finish(); return }
        val services = (application as TapRelayApplication).services
        services.actionExecutor.executeByTagId(id) { fb -> runOnUiThread { feedback = fb } }
        lifecycleScope.launch {
            delay(2600)
            finish()
        }
    }
}

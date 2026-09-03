package com.arbhlabs.taprelay.ui

import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.execution.TapFeedback
import com.arbhlabs.taprelay.nfc.NfcManager
import com.arbhlabs.taprelay.nfc.NfcPayloadParser
import com.arbhlabs.taprelay.ui.theme.TapRelayTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val vm: TapRelayViewModel by viewModels()
    private lateinit var nfc: NfcManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfc = NfcManager(this)
        setContent {
            TapRelayTheme { TapRelayApp(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        if (nfc.isSupported) nfc.enableReader { tag -> lifecycleScope.launch { handleTag(tag) } }
    }

    override fun onPause() {
        super.onPause()
        if (nfc.isSupported) nfc.disableReader()
    }

    private suspend fun handleTag(tag: Tag) {
        val w = vm.wizard.value
        val existingId = withContext(Dispatchers.IO) { nfc.readTagId(tag) }

        if (w.active && w.step == WizardStep.SCAN && !w.editingExisting) {
            if (!nfc.isEnabled) { vm.onWizardWriteError(TapError.NFC_DISABLED.message); return }
            val id = existingId ?: NfcPayloadParser.newTagId()
            try {
                withContext(Dispatchers.IO) { nfc.writeTagId(tag, id, lock = false) }
                vm.services().haptics.vibrateSuccess()
                vm.onTagWritten(id)
            } catch (e: TapException) {
                vm.services().haptics.vibrateError()
                vm.onWizardWriteError(e.error.message)
            }
            return
        }

        // Foreground scan of an already-programmed tag
        if (existingId != null) {
            vm.onForegroundScan(existingId)
        } else if (!w.active) {
            vm.showPill(TapFeedback(TapError.TAG_MALFORMED.message, isError = true))
        }
    }

    private fun TapRelayViewModel.services() = (application as TapRelayApplication).services
}

package com.arbhlabs.taprelay.ui

import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.execution.TapFeedback
import com.arbhlabs.taprelay.nfc.NfcManager
import com.arbhlabs.taprelay.nfc.NfcPayloadParser
import com.arbhlabs.taprelay.nfc.TagInspection
import com.arbhlabs.taprelay.ui.theme.TapRelayTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.sync.Mutex

class MainActivity : ComponentActivity() {

    private val vm: TapRelayViewModel by viewModels()
    private lateinit var nfc: NfcManager
    private val tagMutex = Mutex()

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
        vm.setNfcReady(nfc.isSupported && nfc.isEnabled)
        if (nfc.isSupported) nfc.enableReader { tag -> lifecycleScope.launch { handleTag(tag) } }
    }

    override fun onPause() {
        super.onPause()
        if (nfc.isSupported) nfc.disableReader()
    }

    private suspend fun handleTag(tag: Tag) {
        if (!tagMutex.tryLock()) return
        try {
            val w = vm.wizard.value
            // If user is inside later wizard steps (choosing device/action/name), ignore tag touches
            if (w.active && w.step != WizardStep.SCAN) return

            val provisioning = w.active && w.step == WizardStep.SCAN

            if (provisioning) {
                // Ignore repeat dispatches while a write is already in progress.
                if (w.scanPhase == ScanPhase.WRITING) return
                if (!nfc.isEnabled) { vm.onWizardWriteError(TapError.NFC_DISABLED.message); return }
                vm.onTagDetected()
                vm.services().haptics.vibrateTick()

                when (val inspection = withContext(Dispatchers.IO) { nfc.inspect(tag) }) {
                    is TagInspection.Provisioned -> vm.onExistingTapRelayTag(inspection.tagId)
                    TagInspection.Blank -> writeNewTag(tag)
                    TagInspection.ForeignData ->
                        if (w.allowOverwrite) writeNewTag(tag) else vm.onNeedOverwriteConfirm()
                    TagInspection.NotWritable ->
                        vm.onWizardWriteError(TapError.TAG_READ_ONLY.message)
                    TagInspection.Unsupported ->
                        vm.onWizardWriteError(TapError.TAG_UNSUPPORTED.message)
                }
                return
            }

            // Normal scan mode
            val existingId = withContext(Dispatchers.IO) { nfc.readTagId(tag) }
            if (existingId != null) {
                vm.onForegroundScan(existingId)
            } else if (!w.active) {
                vm.showPill(TapFeedback("This tag isn't configured for TapRelay.", isError = true))
            }
        } finally {
            tagMutex.unlock()
        }
    }

    private suspend fun writeNewTag(tag: Tag) {
        vm.onWriting()
        val id = NfcPayloadParser.newTagId()
        try {
            withContext(Dispatchers.IO) { nfc.writeTagId(tag, id, lock = false) }
            vm.services().haptics.vibrateSuccess()
            vm.onTagWritten(id)
        } catch (e: TapException) {
            vm.services().haptics.vibrateError()
            vm.onWizardWriteError(e.error.message)
        }
    }

    private fun TapRelayViewModel.services() = (application as TapRelayApplication).services
}

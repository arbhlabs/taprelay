package com.arbhlabs.taprelay.ui

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
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
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import com.arbhlabs.taprelay.controller.ControllerManager
import com.arbhlabs.taprelay.trigger.ActivationPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex

class MainActivity : ComponentActivity() {

    private val vm: TapRelayViewModel by viewModels()
    private lateinit var nfc: NfcManager
    private lateinit var controllerManager: ControllerManager
    private val tagMutex = Mutex()
    private var isReaderActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfc = NfcManager(this)
        controllerManager = (application as TapRelayApplication).services.controllerManager
        controllerManager.onFeedback = { fb -> vm.showPill(fb) }
        // While TapRelay is on screen a surface is shown in place, not as a separate activity.
        controllerManager.presenter = inAppPresenter

        setContent {
            TapRelayTheme { TapRelayApp(vm) }
        }

        // Dynamically switch between selective ForegroundDispatch (normal app use)
        // and ReaderMode (when actively writing/inspecting tags in the Add Tag wizard).
        lifecycleScope.launch {
            vm.wizard.collect { w ->
                val needsReader = w.active && w.step == WizardStep.SCAN
                updateNfcMode(needsReader)
            }
        }

        // Keep screen awake if user toggles Desk Remote Mode
        lifecycleScope.launch {
            vm.keepScreenAwake.collect { keepAwake ->
                if (keepAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controllerManager.presenter = inAppPresenter
        controllerManager.startListening()
        handleActivationIntent(intent)
        vm.setNfcReady(nfc.isSupported && nfc.isEnabled)
        val needsReader = vm.wizard.value.let { it.active && it.step == WizardStep.SCAN }
        updateNfcMode(needsReader)
    }

    override fun onPause() {
        super.onPause()
        controllerManager.stopListening()
        if (nfc.isSupported) {
            if (isReaderActive) {
                nfc.disableReader()
                isReaderActive = false
            }
            nfc.disableForegroundDispatch(this)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (controllerManager.handleKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (controllerManager.handleGenericMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /** Shows the requested surface inside the running app. */
    private val inAppPresenter = object : ActivationPresenter {
        override fun openItem(tagId: String) = vm.openItem(tagId)
        override fun openQuickControls(tagId: String) = vm.openQuickControls(tagId)
    }

    private fun handleActivationIntent(intent: Intent?) {
        val tagId = intent?.getStringExtra(EXTRA_OPEN_ITEM) ?: return
        intent.removeExtra(EXTRA_OPEN_ITEM)
        vm.openItem(tagId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleActivationIntent(intent)
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val tagId = NfcPayloadParser.parseUuidFromUri(intent.data)
            if (tagId != null) {
                vm.onForegroundScan(tagId)
            }
        }
    }

    private fun updateNfcMode(needsReader: Boolean) {
        if (!nfc.isSupported) return
        if (needsReader) {
            if (!isReaderActive) {
                nfc.disableForegroundDispatch(this)
                nfc.enableReader { tag -> lifecycleScope.launch { handleTag(tag) } }
                isReaderActive = true
            }
        } else {
            if (isReaderActive) {
                nfc.disableReader()
                isReaderActive = false
            }
            nfc.enableForegroundDispatch(this)
        }
    }

    private suspend fun handleTag(tag: Tag) {
        if (!tagMutex.tryLock()) return
        try {
            val w = vm.wizard.value
            // If user is inside later wizard steps (choosing device/action/name), ignore tag touches
            if (w.active && w.step != WizardStep.SCAN) return

            // If a LastDose tag is scanned, disregard completely so it is never overwritten or treated as an error.
            if (withContext(Dispatchers.IO) { nfc.isLastDoseTag(tag) }) {
                return
            }

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

            // Normal scan mode (when reader mode was active)
            val existingId = withContext(Dispatchers.IO) { nfc.readTagId(tag) }
            if (existingId != null) {
                vm.onForegroundScan(existingId)
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

    companion object {
        const val EXTRA_OPEN_ITEM = "com.arbhlabs.taprelay.extra.OPEN_ITEM"

        fun openItemIntent(context: Context, tagId: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_ITEM, tagId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

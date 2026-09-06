package com.arbhlabs.taprelay.controller

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.execution.TapFeedback
import com.arbhlabs.taprelay.trigger.ActivationPresenter
import com.arbhlabs.taprelay.ui.MainActivity
import com.arbhlabs.taprelay.ui.quick.QuickControlsActivity

/**
 * Lets a mapped controller button fire from any app, or none, instead of only the app that owns
 * focus. Android hands hardware key events to an accessibility service before the foreground
 * window, so this forwards each gamepad press to the same [ControllerManager] the in-app screen
 * uses and consumes it when a mapping fires.
 *
 * Deliberate limits, matching what the platform actually delivers here:
 *  - Key events only. Analog sticks, analog triggers and any D-pad a pad reports as a HAT axis
 *    arrive as MotionEvents, which are never delivered to an accessibility service. Map to the
 *    face buttons, bumpers, stick clicks or Menu/View for global use.
 *  - A consumed button does nothing else in whatever app is in front. That is the point, but it
 *    means a button you use in a game should not also be mapped here.
 *  - Entirely opt-in: nothing here runs until the owner enables the service in Android's
 *    Accessibility settings.
 */
class GlobalControllerService : AccessibilityService() {

    private val controllerManager: ControllerManager?
        get() = (applicationContext as? TapRelayApplication)?.services?.controllerManager

    /** From here the foreground app is unknown, so surfaces are launched as their own task. */
    private val presenter = object : ActivationPresenter {
        override fun openItem(tagId: String) {
            runCatching { startActivity(MainActivity.openItemIntent(this@GlobalControllerService, tagId)) }
        }

        override fun openQuickControls(tagId: String) {
            runCatching { startActivity(QuickControlsActivity.intent(this@GlobalControllerService, tagId)) }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "connected; controller manager ${if (controllerManager == null) "missing" else "ready"}")
        controllerManager?.apply {
            presenter = this@GlobalControllerService.presenter
            onFeedback = { fb -> showFeedback(fb) }
            startListening()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // The in-app screen may have re-pointed these at itself while it was on top; take them
        // back so a press that lands here is always presented and reported by this service.
        controllerManager?.let { manager ->
            manager.presenter = presenter
            manager.onFeedback = { fb -> showFeedback(fb) }
            val consumed = manager.handleKeyEvent(event)
            Log.i(TAG, "onKeyEvent code=${event.keyCode} action=${event.action} source=${event.source} consumed=$consumed")
            return consumed
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* key events only */ }

    override fun onInterrupt() { /* nothing to stop */ }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        controllerManager?.stopListening()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    private fun showFeedback(fb: TapFeedback) {
        if (fb.pending) return
        val text = fb.title.replace(" • ", " — ").ifBlank { if (fb.isError) "Action failed" else "Done" }
        android.os.Handler(mainLooper).post {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "TapRelayGlobalCtrl"

        @Volatile
        private var instance: GlobalControllerService? = null

        /** True once Android has bound the service, i.e. the owner enabled and it is running. */
        fun isRunning(): Boolean = instance != null

        /** Whether the service is listed as enabled in Settings, even before it binds. */
        fun isEnabledInSettings(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val target = "${context.packageName}/${GlobalControllerService::class.java.name}"
            val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(flat) }
            while (splitter.hasNext()) {
                if (splitter.next().equals(target, ignoreCase = true)) return true
            }
            return false
        }
    }
}

package com.arbhlabs.taprelay.controller

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.controller.model.ControllerKeys
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
 *
 * Feedback is quiet by design: a one-time toast confirms the service bound, a mapped press shows
 * its action's own result, and a press with no mapping says so once. A button nobody mapped is
 * not consumed - it reaches the app in front untouched.
 */
class GlobalControllerService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val controllerManager: ControllerManager?
        get() = (applicationContext as? TapRelayApplication)?.services?.controllerManager

    /** From here the foreground app is unknown, so surfaces are launched as their own task. */
    private val presenter = object : ActivationPresenter {
        override fun openItem(tagId: String) {
            runCatching {
                startActivity(
                    MainActivity.openItemIntent(this@GlobalControllerService, tagId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        override fun openQuickControls(tagId: String) {
            runCatching {
                startActivity(
                    QuickControlsActivity.intent(this@GlobalControllerService, tagId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val managerReady = controllerManager != null
        Log.i(TAG, "connected; controller manager ${if (managerReady) "ready" else "missing"}")
        controllerManager?.apply {
            presenter = this@GlobalControllerService.presenter
            onFeedback = { fb -> showFeedback(fb) }
            onUnmappedInput = { label -> unmappedToast(label) }
            // Android can re-deliver onServiceConnected to the same bound service (seen every few
            // seconds on the Pixel 7); listen once per instance so the ref count stays balanced.
            if (!listening) {
                startListening()
                listening = true
            }
        }
        // Announce once per process, not on every reconnect - that read as a toast per press.
        if (!managerReady) {
            toast("TapRelay: service on, but the app isn't ready — reopen TapRelay once")
        } else if (!announced) {
            announced = true
            toast("TapRelay: controller works in any app now")
        }
    }

    private var listening = false

    /** One "no mapping" note per button per process, so an unmapped button never nags. */
    private fun unmappedToast(label: String) {
        if (unmappedAnnounced.add(label)) toast("Controller: $label — no mapping in TapRelay")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val manager = controllerManager
        val normalized = ControllerKeys.normalizeKeyCode(event.keyCode)
        if (manager == null) {
            if (normalized != null && event.action == KeyEvent.ACTION_DOWN) {
                toast("Controller press seen, but TapRelay isn't running — reopen it once")
            }
            return false
        }

        // The in-app screen may have re-pointed these at itself while it was on top; take them
        // back so a press that lands here is always presented and reported by this service.
        manager.presenter = presenter
        manager.onFeedback = { fb -> showFeedback(fb) }
        manager.onUnmappedInput = { label -> unmappedToast(label) }

        // consumeUnmapped = false: a button with no TapRelay mapping passes straight through to
        // whatever app is in front, so the service never steals a game or app button.
        val consumed = manager.handleKeyEvent(event, consumeUnmapped = false)
        Log.i(
            TAG,
            "onKeyEvent code=${event.keyCode} norm=$normalized action=${event.action} " +
                "source=${event.source} device=${event.device?.name} consumed=$consumed"
        )
        return consumed
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* key events only */ }

    override fun onInterrupt() { /* nothing to stop */ }

    override fun onUnbind(intent: Intent?): Boolean {
        controllerManager?.apply {
            onUnmappedInput = null
            if (listening) stopListening()
        }
        listening = false
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    private fun showFeedback(fb: TapFeedback) {
        if (fb.pending) return
        val text = fb.title.replace(" • ", " — ").ifBlank { if (fb.isError) "Action failed" else "Done" }
        toast(text)
    }

    private fun toast(text: String) {
        mainHandler.post { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val TAG = "TapRelayGlobalCtrl"

        @Volatile private var announced = false
        private val unmappedAnnounced = java.util.Collections.synchronizedSet(HashSet<String>())

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

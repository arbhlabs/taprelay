package com.arbhlabs.taprelay.controller

import android.util.Log
import com.arbhlabs.taprelay.controller.model.ControllerKeys
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.DeviceCapabilities
import com.arbhlabs.taprelay.domain.model.DeviceKind
import com.arbhlabs.taprelay.domain.provider.SmartHomeProvider
import com.arbhlabs.taprelay.domain.repository.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

data class AutomaticLightControlState(
    val tagId: String? = null,
    val name: String? = null,
    val supportsBrightness: Boolean = false,
    val supportsColor: Boolean = false,
    val loading: Boolean = false,
    val status: String? = null,
    /** True only during the short window after the light was turned on. */
    val adjusting: Boolean = false
) {
    val active: Boolean get() = tagId != null && (supportsBrightness || supportsColor)
    val description: String?
        get() {
            if (!active) return null
            val controls = when {
                supportsBrightness && supportsColor -> "up/down brightness • left/right colour"
                supportsBrightness -> "up/down brightness"
                else -> "left/right colour"
            }
            return if (adjusting) "D-pad now: $controls" else "Turn on, then D-pad for 20 s: $controls"
        }
}

/**
 * Capability-driven, transient controller controls for the most recently selected/mapped light.
 *
 * Turning the light on opens a [WINDOW_MS] adjustment window in which the D-pad steps brightness
 * and colour in discrete notches. The D-pad is only borrowed: outside the window every D-pad press
 * goes to its normal mapping, and [onAdjustmentEnded] fires once when the window closes so the
 * caller can tell the hand holding the pad. Each step updates a desired value immediately; at
 * most one coalesced cloud write is launched per [MIN_SEND_INTERVAL_MS].
 */
class AutomaticLightControls(
    private val tags: TagRepository,
    private val providers: () -> Map<String, SmartHomeProvider>,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(AutomaticLightControlState())
    val state: StateFlow<AutomaticLightControlState> = _state.asStateFlow()

    private var selected: TagEntity? = null
    private var selectionJob: Job? = null
    private var sendJob: Job? = null
    private var lastSentAt = 0L
    private var brightness = Brightness.DEFAULT_PERCENT.toFloat()
    private var hue = 0f
    private var desiredBrightness: Int? = null
    private var desiredColor: Int? = null

    /** Fired once, on the main thread, when a window closes by timing out or the light going off. */
    var onAdjustmentEnded: (() -> Unit)? = null
    /** Fired, on the main thread, each time a window opens or restarts. */
    var onAdjustmentStarted: (() -> Unit)? = null
    /** Fired when a D-pad press is already at 1% or 100%, so the press is felt, not ignored. */
    var onAdjustmentLimit: (() -> Unit)? = null
    /**
     * The brightness this session last sent to each light. Providers cannot report a lamp's live
     * brightness, so without this every window would start from a guess and the first press
     * would jump to an unrelated level.
     */
    private val lastBrightness = HashMap<String, Int>()
    private var windowJob: Job? = null
    private var adjustingUntil = 0L

    val isAdjusting: Boolean
        get() = selected != null && System.currentTimeMillis() < adjustingUntil

    fun select(tagId: String) {
        if (_state.value.tagId == tagId && !_state.value.loading) return
        // Another item took over the pad; its own action feedback is the cue, so no extra buzz.
        endAdjustment(notify = false)
        selectionJob?.cancel()
        sendJob?.cancel()
        desiredBrightness = null
        desiredColor = null
        _state.value = AutomaticLightControlState(tagId = tagId, loading = true)
        selectionJob = scope.launch {
            val tag = withContext(Dispatchers.IO) { tags.getTagById(tagId) }
            if (tag == null || !tag.enabled || tag.isNonDevice) {
                clear()
                return@launch
            }
            val caps = withContext(Dispatchers.IO) { capabilitiesFor(tag) }
            if (!caps.supportsBrightness && !caps.supportsColor) {
                selected = null
                _state.value = AutomaticLightControlState(
                    tagId = tagId,
                    name = tag.friendlyName,
                    status = "No automatic light controls for this device"
                )
                return@launch
            }
            selected = tag
            brightness = (lastBrightness[tagId] ?: tag.brightnessPercent ?: Brightness.DEFAULT_PERCENT).toFloat()
            hue = rgbToHue(tag.colorRgb ?: DEFAULT_COLOR)
            _state.value = AutomaticLightControlState(
                tagId = tagId,
                name = tag.friendlyName,
                supportsBrightness = caps.supportsBrightness,
                supportsColor = caps.supportsColor
            )
        }
    }

    fun clear() {
        endAdjustment(notify = false)
        selectionJob?.cancel()
        sendJob?.cancel()
        selected = null
        desiredBrightness = null
        desiredColor = null
        _state.value = AutomaticLightControlState()
    }

    /**
     * Called after a mapped action for [tagId] settled successfully. Opens (or restarts) the
     * adjustment window when the light is now on; closes it when the same light went off.
     */
    fun onActivationSettled(tagId: String) {
        scope.launch {
            selectionJob?.join()
            val tag = withContext(Dispatchers.IO) { tags.getTagById(tagId) } ?: return@launch
            if (selected?.tagId != tagId || !_state.value.active) return@launch
            if (tag.lastKnownState == 1) {
                // Turning on applies the item's own brightness when it has one, so that is where
                // the lamp really is now; otherwise it kept what this session last set.
                tag.brightnessPercent?.let { lastBrightness[tagId] = Brightness.clampPercent(it) }
                lastBrightness[tagId]?.let { brightness = it.toFloat() }
                startWindow()
            } else if (isAdjusting) {
                endAdjustment(notify = true)
            }
        }
    }

    private fun startWindow() {
        // One timer at most: a repeat "on" restarts the same window instead of stacking another.
        windowJob?.cancel()
        adjustingUntil = System.currentTimeMillis() + WINDOW_MS
        _state.value = _state.value.copy(adjusting = true)
        onAdjustmentStarted?.invoke()
        windowJob = scope.launch {
            delay(WINDOW_MS)
            windowJob = null
            endAdjustment(notify = true)
        }
    }

    /** Closes the window; [notify] asks for the single "back to normal" cue. No-op when closed. */
    fun endAdjustment(notify: Boolean) {
        val wasOpen = adjustingUntil != 0L
        windowJob?.cancel()
        windowJob = null
        adjustingUntil = 0L
        if (!wasOpen) return
        _state.value = _state.value.copy(adjusting = false)
        if (notify) onAdjustmentEnded?.invoke()
    }

    /**
     * One discrete D-pad step. Returns true only when the press was used for the light, so an
     * unsupported direction - or any press outside the window - keeps its normal mapping.
     */
    fun onDpad(inputKey: String): Boolean {
        if (!isAdjusting) return false
        val tag = selected ?: return false
        val current = _state.value
        Log.i(TAG, "dpad $inputKey tag=${tag.friendlyName} brightness=${brightness.roundToInt()} " +
            "supportsBrightness=${current.supportsBrightness} supportsColor=${current.supportsColor}")
        when (inputKey) {
            ControllerKeys.DPAD_UP, ControllerKeys.DPAD_DOWN -> {
                if (!current.supportsBrightness) return false
                val now = brightness.roundToInt()
                val next = stepBrightness(now, up = inputKey == ControllerKeys.DPAD_UP)
                if (next == now) {
                    // Already at the end: keep the press (it is still a light press) and say so.
                    onAdjustmentLimit?.invoke()
                    return true
                }
                brightness = next.toFloat()
                desiredBrightness = next
                lastBrightness[tag.tagId] = next
            }
            ControllerKeys.DPAD_LEFT, ControllerKeys.DPAD_RIGHT -> {
                if (!current.supportsColor) return false
                hue = stepHue(hue, forward = inputKey == ControllerKeys.DPAD_RIGHT)
                desiredColor = hsvToRgb(hue)
            }
            else -> return false
        }
        scheduleSend(tag)
        return true
    }

    private fun scheduleSend(tag: TagEntity) {
        if (sendJob?.isActive == true) return
        sendJob = scope.launch {
            val wait = (lastSentAt + MIN_SEND_INTERVAL_MS - System.currentTimeMillis()).coerceAtLeast(0L)
            if (wait > 0) delay(wait)
            val b = desiredBrightness
            val c = desiredColor
            desiredBrightness = null
            desiredColor = null
            lastSentAt = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) { apply(tag, b, c) }
            _state.value = _state.value.copy(
                status = if (result) {
                    listOfNotNull(b?.let { "$it%" }, c?.let { "Colour" }).joinToString(" • ")
                } else "Light did not respond"
            )
            // An event may have arrived while the request was in flight. Send only the newest
            // desired values after the same rate limit, rather than replaying intermediate noise.
            sendJob = null
            if (desiredBrightness != null || desiredColor != null) scheduleSend(tag)
        }
    }

    private suspend fun apply(tag: TagEntity, b: Int?, c: Int?): Boolean {
        var successes = 0
        for (target in tag.allTargets) {
            val provider = providers()[target.providerId] ?: continue
            val result = runCatching {
                when {
                    b != null && c != null -> provider.setLook(target.deviceId, target.sku, c, b)
                    b != null -> provider.setBrightness(target.deviceId, target.sku, b)
                    c != null -> provider.setColor(target.deviceId, target.sku, c)
                }
            }
            // A failed cloud call used to vanish here; it is the first thing to check when a
            // step "does nothing".
            result.exceptionOrNull()?.let {
                Log.w(TAG, "send failed provider=${target.providerId} brightness=$b colour=$c", it)
            } ?: Log.i(TAG, "sent provider=${target.providerId} brightness=$b colour=$c")
            if (result.isSuccess) successes++
        }
        if (successes > 0) {
            tags.updateStateAndTimestamp(tag.tagId, 1, System.currentTimeMillis())
        }
        return successes > 0
    }

    private suspend fun capabilitiesFor(tag: TagEntity): DeviceCapabilities =
        tag.allTargets.groupBy { it.providerId }.flatMap { (providerId, targets) ->
            val provider = providers()[providerId] ?: return DeviceCapabilities.UNKNOWN
            provider.getCapabilities(targets.map { it.deviceId to it.sku })
        }.reduceOrNull(::intersect) ?: DeviceCapabilities.UNKNOWN

    private fun intersect(a: DeviceCapabilities, b: DeviceCapabilities) = DeviceCapabilities(
        kind = if (a.kind == b.kind) a.kind else DeviceKind.SWITCH,
        supportsPower = a.supportsPower && b.supportsPower,
        supportsBrightness = a.supportsBrightness && b.supportsBrightness,
        supportsColor = a.supportsColor && b.supportsColor,
        supportsColorTemperature = a.supportsColorTemperature && b.supportsColorTemperature,
        fanLevels = a.fanLevels.filter { it in b.fanLevels },
        modes = a.modes.filter { it in b.modes }
    )

    companion object {
        private const val TAG = "TapRelayLightAdjust"

        /** How long the D-pad stays borrowed after the light turns on. */
        const val WINDOW_MS = 20_000L
        /**
         * Brightness notches, spaced by how a bulb looks rather than by equal percentages: a
         * 10% step near full is invisible, while near the bottom it is a big jump.
         */
        val BRIGHTNESS_LEVELS = intArrayOf(1, 5, 10, 20, 35, 50, 70, 100)
        const val HUE_STEP = 30f
        private const val MIN_SEND_INTERVAL_MS = 400L
        private const val DEFAULT_COLOR = 0xFFFF0000.toInt()

        /** The next notch above/below [current]; returns [current] unchanged at either end. */
        internal fun stepBrightness(current: Int, up: Boolean): Int {
            val c = current.coerceIn(1, 100)
            return if (up) BRIGHTNESS_LEVELS.firstOrNull { it > c } ?: c
            else BRIGHTNESS_LEVELS.lastOrNull { it < c } ?: c
        }

        internal fun stepHue(current: Float, forward: Boolean): Float =
            wrapHue(current + if (forward) HUE_STEP else -HUE_STEP)

        internal fun wrapHue(value: Float): Float = ((value % 360f) + 360f) % 360f

        internal fun rgbToHue(rgb: Int): Float {
            val r = ((rgb shr 16) and 0xff) / 255f
            val g = ((rgb shr 8) and 0xff) / 255f
            val b = (rgb and 0xff) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val d = max - min
            if (d == 0f) return 0f
            return wrapHue(60f * when (max) {
                r -> ((g - b) / d) % 6f
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            })
        }

        internal fun hsvToRgb(hue: Float): Int {
            val h = wrapHue(hue)
            val c = 1f
            val x = c * (1f - abs((h / 60f) % 2f - 1f))
            val (r, g, b) = when {
                h < 60f -> Triple(c, x, 0f)
                h < 120f -> Triple(x, c, 0f)
                h < 180f -> Triple(0f, c, x)
                h < 240f -> Triple(0f, x, c)
                h < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return ((r * 255).roundToInt() shl 16) or
                ((g * 255).roundToInt() shl 8) or (b * 255).roundToInt()
        }
    }
}

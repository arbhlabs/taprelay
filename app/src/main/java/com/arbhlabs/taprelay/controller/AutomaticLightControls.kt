package com.arbhlabs.taprelay.controller

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
    val status: String? = null
) {
    val active: Boolean get() = tagId != null && (supportsBrightness || supportsColor)
    val description: String?
        get() = when {
            !active -> null
            supportsBrightness && supportsColor -> "Left stick: up/down brightness • left/right colour"
            supportsBrightness -> "Left stick: up/down brightness"
            else -> "Left stick: left/right colour"
        }
}

/**
 * Capability-driven, transient controller controls for the most recently selected/mapped light.
 * Stick events update a desired value immediately; at most one coalesced cloud write is launched
 * per [MIN_SEND_INTERVAL_MS]. Explicit mappings for a stick direction are respected by callers.
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

    fun select(tagId: String) {
        if (_state.value.tagId == tagId && !_state.value.loading) return
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
            brightness = (tag.brightnessPercent ?: Brightness.DEFAULT_PERCENT).toFloat()
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
        selectionJob?.cancel()
        sendJob?.cancel()
        selected = null
        desiredBrightness = null
        desiredColor = null
        _state.value = AutomaticLightControlState()
    }

    /** Returns true only when a supported axis was handled. */
    fun onAxes(x: Float, y: Float, allowHorizontal: Boolean, allowVertical: Boolean): Boolean {
        val tag = selected ?: return false
        val current = _state.value
        var changed = false
        val nx = normalized(x)
        val ny = normalized(y)

        if (allowVertical && current.supportsBrightness && ny != 0f) {
            // Android Y is positive down, so pushing up increases brightness.
            brightness = (brightness - ny * BRIGHTNESS_PER_EVENT).coerceIn(1f, 100f)
            desiredBrightness = brightness.roundToInt()
            changed = true
        }
        if (allowHorizontal && current.supportsColor && nx != 0f) {
            hue = wrapHue(hue + nx * HUE_PER_EVENT)
            desiredColor = hsvToRgb(hue)
            changed = true
        }
        if (changed) scheduleSend(tag)
        return changed
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
            val ok = runCatching {
                when {
                    b != null && c != null -> provider.setLook(target.deviceId, target.sku, c, b)
                    b != null -> provider.setBrightness(target.deviceId, target.sku, b)
                    c != null -> provider.setColor(target.deviceId, target.sku, c)
                }
            }.isSuccess
            if (ok) successes++
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

    private fun normalized(value: Float): Float {
        val magnitude = abs(value)
        if (magnitude <= DEAD_ZONE) return 0f
        return ((magnitude - DEAD_ZONE) / (1f - DEAD_ZONE)).coerceIn(0f, 1f) * if (value < 0) -1 else 1
    }

    companion object {
        const val LEFT_STICK_UP = "LEFT_STICK_UP"
        const val LEFT_STICK_DOWN = "LEFT_STICK_DOWN"
        const val LEFT_STICK_LEFT = "LEFT_STICK_LEFT"
        const val LEFT_STICK_RIGHT = "LEFT_STICK_RIGHT"
        private const val DEAD_ZONE = 0.20f
        private const val BRIGHTNESS_PER_EVENT = 4.5f
        private const val HUE_PER_EVENT = 12f
        private const val MIN_SEND_INTERVAL_MS = 400L
        private const val DEFAULT_COLOR = 0xFFFF0000.toInt()

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

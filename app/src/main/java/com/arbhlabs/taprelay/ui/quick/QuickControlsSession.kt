package com.arbhlabs.taprelay.ui.quick

import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.di.ServiceLocator
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.ColorMath
import com.arbhlabs.taprelay.domain.model.DeviceCapabilities
import com.arbhlabs.taprelay.domain.model.DeviceKind
import com.arbhlabs.taprelay.domain.model.DeviceLabels
import com.arbhlabs.taprelay.domain.model.DeviceSnapshot
import com.arbhlabs.taprelay.domain.model.LightPresets
import com.arbhlabs.taprelay.domain.model.TagTarget
import com.arbhlabs.taprelay.domain.model.TapError
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.model.TargetType
import com.arbhlabs.taprelay.domain.provider.SmartHomeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QuickControlsUiState(
    val tagId: String? = null,
    val tag: TagEntity? = null,
    val loadingCapabilities: Boolean = true,
    val capabilities: DeviceCapabilities? = null,
    val snapshot: DeviceSnapshot = DeviceSnapshot(),
    val brightnessPercent: Int = Brightness.DEFAULT_PERCENT,
    val colorRgb: Int? = null,
    val whiteKelvin: Int = 2700,
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null
) {
    val open: Boolean get() = tagId != null
}

/**
 * Drives one Quick Controls surface. It owns no device code of its own: every button and
 * slider goes through the same [SmartHomeProvider] the tap executor uses, and the controls
 * that get drawn come from what the provider says the device can actually do.
 */
class QuickControlsSession(
    private val services: ServiceLocator,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(QuickControlsUiState(tagId = null))
    val state: StateFlow<QuickControlsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun open(tagId: String) {
        loadJob?.cancel()
        _state.value = QuickControlsUiState(tagId = tagId, loadingCapabilities = true)
        loadJob = scope.launch {
            val tag = withContext(Dispatchers.IO) { services.tagRepository.getTagById(tagId) }
            if (tag == null) {
                _state.value = _state.value.copy(
                    loadingCapabilities = false,
                    error = TapError.TAG_UNREGISTERED.message
                )
                return@launch
            }
            _state.value = _state.value.copy(
                tag = tag,
                brightnessPercent = tag.brightnessPercent ?: Brightness.DEFAULT_PERCENT,
                colorRgb = tag.colorRgb,
                whiteKelvin = tag.colorRgb?.let { ColorMath.nearestKelvin(it) } ?: 2700,
                snapshot = DeviceSnapshot(power = tag.lastKnownState, fanLevel = tag.fanLevel)
            )
            if (tag.targetType == TargetType.SCENE) {
                _state.value = _state.value.copy(
                    loadingCapabilities = false,
                    capabilities = DeviceCapabilities.SCENE
                )
                return@launch
            }
            loadCapabilities(tag)
        }
    }

    fun close() {
        loadJob?.cancel()
        loadJob = null
        _state.value = QuickControlsUiState(tagId = null, loadingCapabilities = false)
    }

    fun clearStatus() {
        _state.value = _state.value.copy(status = null, error = null)
    }

    private suspend fun loadCapabilities(tag: TagEntity) {
        val caps = withContext(Dispatchers.IO) {
            runCatching {
                tag.allTargets
                    .groupBy { it.providerId }
                    .flatMap { (providerId, targets) ->
                        services.providers[providerId]
                            ?.getCapabilities(targets.map { it.deviceId to it.sku })
                            .orEmpty()
                    }
                    .reduceOrNull(::intersect)
            }.getOrNull()
        }
        val snap = withContext(Dispatchers.IO) {
            val primary = tag.allTargets.first()
            runCatching { provider(primary)?.getSnapshot(primary.deviceId, primary.sku) }.getOrNull()
        }
        _state.value = _state.value.copy(
            loadingCapabilities = false,
            capabilities = caps ?: DeviceCapabilities.UNKNOWN,
            snapshot = snap ?: _state.value.snapshot
        )
    }

    /** A group only offers what every device in it can really do. */
    private fun intersect(a: DeviceCapabilities, b: DeviceCapabilities): DeviceCapabilities =
        DeviceCapabilities(
            kind = if (a.kind == b.kind) a.kind else DeviceKind.SWITCH,
            supportsPower = a.supportsPower && b.supportsPower,
            supportsBrightness = a.supportsBrightness && b.supportsBrightness,
            supportsColor = a.supportsColor && b.supportsColor,
            supportsColorTemperature = a.supportsColorTemperature && b.supportsColorTemperature,
            fanLevels = a.fanLevels.filter { it in b.fanLevels },
            modes = a.modes.filter { it in b.modes }
        )

    private fun provider(target: TagTarget): SmartHomeProvider? = services.providers[target.providerId]

    // ---- Controls ----

    fun setPower(on: Boolean) {
        markPower(if (on) 1 else 0)
        apply(if (on) "On" else "Off") { p, t -> p.setPower(t.deviceId, t.sku, on) }
    }

    fun togglePower() {
        val current = _state.value.snapshot.power ?: _state.value.tag?.lastKnownState ?: 0
        setPower(current != 1)
    }

    fun setBrightness(percent: Int) {
        _state.value = _state.value.copy(brightnessPercent = Brightness.clampPercent(percent))
    }

    fun commitBrightness() {
        val percent = _state.value.brightnessPercent
        markPower(1)
        apply("$percent%") { p, t -> p.setBrightness(t.deviceId, t.sku, percent) }
    }

    fun setColor(rgb: Int) {
        _state.value = _state.value.copy(colorRgb = rgb)
        markPower(1)
        apply(LightPresets.nameFor(rgb)) { p, t -> p.setColor(t.deviceId, t.sku, rgb) }
    }

    fun setWhiteKelvin(kelvin: Int) {
        val k = kelvin.coerceIn(LightPresets.MIN_KELVIN, LightPresets.MAX_KELVIN)
        _state.value = _state.value.copy(whiteKelvin = k, colorRgb = ColorMath.kelvinToRgb(k))
    }

    fun commitWhiteKelvin() {
        val kelvin = _state.value.whiteKelvin
        val rgb = ColorMath.kelvinToRgb(kelvin)
        markPower(1)
        apply(kelvin.toString() + "K") { p, t -> p.setColor(t.deviceId, t.sku, rgb) }
    }

    fun setFanLevel(level: String) {
        _state.value = _state.value.copy(snapshot = _state.value.snapshot.copy(fanLevel = level))
        markPower(1)
        apply("Fan " + DeviceLabels.fanLevel(level)) { p, t -> p.setFanLevel(t.deviceId, t.sku, level) }
    }

    fun setMode(mode: String) {
        _state.value = _state.value.copy(snapshot = _state.value.snapshot.copy(mode = mode))
        markPower(1)
        apply(DeviceLabels.mode(mode)) { p, t -> p.setMode(t.deviceId, t.sku, mode) }
    }

    fun runScene() = apply("Scene started") { p, t ->
        p.executeAction(
            targetId = t.deviceId,
            targetType = TargetType.SCENE,
            sku = t.sku,
            action = ActionType.RUN_SCENE,
            targetState = 1
        )
    }

    private fun markPower(state: Int) {
        _state.value = _state.value.copy(snapshot = _state.value.snapshot.copy(power = state))
    }

    /**
     * Runs one control across every device the item drives, then records the outcome on the tag
     * so the rest of the app sees the same state a tap would have produced.
     */
    private fun apply(label: String, block: suspend (SmartHomeProvider, TagTarget) -> Unit) {
        val tag = _state.value.tag ?: return
        // What to fall back to if nothing reaches the device.
        val previous = _state.value.snapshot
        _state.value = _state.value.copy(busy = true, error = null)
        services.haptics.vibrateClick()
        scope.launch {
            var succeeded = 0
            var failure: String? = null
            withContext(Dispatchers.IO) {
                for (target in tag.allTargets) {
                    val p = provider(target)
                    if (p == null) {
                        if (failure == null) failure = TapError.PROVIDER_UNAVAILABLE.message
                        continue
                    }
                    try {
                        block(p, target)
                        succeeded++
                    } catch (e: TapException) {
                        if (failure == null) failure = e.error.message
                    } catch (e: Exception) {
                        if (failure == null) failure = TapError.UNKNOWN.message
                    }
                }
            }
            if (succeeded > 0) {
                val power = _state.value.snapshot.power ?: tag.lastKnownState
                withContext(Dispatchers.IO) {
                    services.tagRepository.updateStateAndTimestamp(
                        tag.tagId, power, System.currentTimeMillis()
                    )
                }
                services.haptics.vibrateSuccess()
                _state.value = _state.value.copy(busy = false, status = label, error = null)
            } else {
                services.haptics.vibrateError()
                _state.value = _state.value.copy(
                    busy = false,
                    snapshot = previous,
                    status = null,
                    error = failure ?: TapError.UNKNOWN.message
                )
            }
        }
    }
}

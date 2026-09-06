package com.arbhlabs.taprelay.haptics

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.PhoneAction
import com.arbhlabs.taprelay.domain.model.TargetType
import com.arbhlabs.taprelay.execution.TapOutcome
import com.arbhlabs.taprelay.trigger.TriggerSource
import kotlin.math.roundToInt

data class HapticActivationContext(
    val source: TriggerSource,
    val inputDeviceId: Int = -1,
    val physicalInput: String? = null,
    /** Stable controller-mapping identity. Falls back to the item id for non-controller triggers. */
    val mappingIdentity: String? = null
)

/**
 * ARBH Labs HapticSignatures runtime. Assignment is pure and deterministic; this class only
 * discovers Android capabilities, persists assignments, adapts them, and renders without ever
 * participating in action success/failure.
 */
class HapticSignatureEngine(
    context: Context,
    private val repository: HapticSignatureRepository = SharedPreferencesHapticSignatureRepository(context),
    private val allocator: HapticPatternAllocator = HapticPatternAllocator()
) {
    private val appContext = context.applicationContext
    private val inputManager = appContext.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val phoneManager: VibratorManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
    } else null
    private val phoneVibrator: Vibrator = phoneManager?.defaultVibrator ?: run {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    @Volatile var automaticEnabled = true
    @Volatile var controllerEnabled = true
    @Volatile var phoneFallbackEnabled = true
    @Volatile var intensity = HapticIntensity.NORMAL

    fun capabilities(inputDeviceId: Int): ControllerHapticCapabilities {
        val device = runCatching { inputManager.getInputDevice(inputDeviceId) }.getOrNull()
            ?: return ControllerHapticCapabilities(inputDeviceId, 0, 0, "Disconnected controller", emptyList(), emptyMap(), 0, if (phoneVibrator.hasVibrator()) HapticFallbackStrategy.PHONE else HapticFallbackStrategy.NONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = runCatching { device.vibratorManager }.getOrNull()
            val ids = manager?.vibratorIds?.toList().orEmpty()
            val amplitudes = ids.associateWith { id -> runCatching { manager!!.getVibrator(id).hasAmplitudeControl() }.getOrDefault(false) }
            return ControllerHapticCapabilities(
                inputDeviceId, device.vendorId, device.productId, device.name, ids, amplitudes,
                ids.size, when { ids.size > 1 -> HapticFallbackStrategy.MULTI_CHANNEL; ids.size == 1 -> HapticFallbackStrategy.SINGLE_EXTERNAL; phoneVibrator.hasVibrator() -> HapticFallbackStrategy.PHONE; else -> HapticFallbackStrategy.NONE }
            )
        }
        @Suppress("DEPRECATION") val vibrator = device.vibrator
        val usable = if (runCatching { vibrator.hasVibrator() }.getOrDefault(false)) 1 else 0
        return ControllerHapticCapabilities(inputDeviceId, device.vendorId, device.productId, device.name, if (usable == 1) listOf(0) else emptyList(), if (usable == 1) mapOf(0 to vibrator.hasAmplitudeControl()) else emptyMap(), usable, if (usable == 1) HapticFallbackStrategy.SINGLE_EXTERNAL else if (phoneVibrator.hasVibrator()) HapticFallbackStrategy.PHONE else HapticFallbackStrategy.NONE)
    }

    /** Concise, safe development report. It makes no claim about unprobed motor identities. */
    fun capabilityReport(inputDeviceId: Int): String = capabilities(inputDeviceId).let { c ->
        "${c.deviceName} VID=${c.vendorId} PID=${c.productId} vibrators=${c.vibratorIds} count=${c.usableChannels} amplitude=${c.amplitudeControlById} identitiesValidated=${c.channelIdentitiesValidated}"
    }

    fun play(tag: TagEntity, outcome: TapOutcome, context: HapticActivationContext) {
        if (!automaticEnabled) return
        runCatching {
            val external = if (context.source == TriggerSource.CONTROLLER && controllerEnabled) capabilities(context.inputDeviceId) else null
            val renderCaps = HapticRenderCapabilities(
                channels = external?.usableChannels ?: 1,
                amplitudeControl = external?.amplitudeControlById?.values?.any { it } ?: phoneVibrator.hasAmplitudeControl(),
                spatialChannelsValidated = external?.channelIdentitiesValidated == true
            )
            val descriptor = descriptor(tag, outcome, context)
            val capabilityKey = "${renderCaps.channels}:${renderCaps.amplitudeControl}:${renderCaps.spatialChannelsValidated}"
            val assignedId = repository.assignedPatternId(descriptor.mappingId, capabilityKey)
            val pattern = CandidatePatterns.all.find { it.id == assignedId }
                ?: allocator.allocate(descriptor, repository.assignments(capabilityKey).values, renderCaps).also {
                    repository.save(descriptor.mappingId, capabilityKey, it.id)
                }
            val adapted = adapt(pattern, descriptor)
            if (external != null && external.usableChannels > 0) renderExternal(context.inputDeviceId, external, adapted)
            else if (phoneFallbackEnabled && phoneVibrator.hasVibrator()) render(phoneVibrator, adapted.channels.first(), phoneVibrator.hasAmplitudeControl())
        }
    }

    fun resetAssignments() = repository.reset()

    private fun descriptor(tag: TagEntity, outcome: TapOutcome, context: HapticActivationContext): HapticSemanticDescriptor {
        val actionClass = when {
            tag.isLastDose -> HapticActionClass.LASTDOSE
            tag.isMagicAction -> HapticActionClass.MACRO
            tag.isLaunch -> HapticActionClass.OPEN_APP
            tag.isWebhook || tag.isPhone -> HapticActionClass.MOMENTARY
            tag.actionType == ActionType.TOGGLE || tag.actionType == ActionType.TOGGLE_FAN_SPEED -> HapticActionClass.TOGGLE
            tag.targetType == TargetType.DEVICE || tag.targetType == TargetType.SCENE -> HapticActionClass.SMART_DEVICE
            else -> HapticActionClass.OTHER
        }
        val transition = when {
            tag.isLastDose -> HapticTransition.LOG
            outcome.summary.substringAfterLast('•').trim().startsWith("On", true) -> HapticTransition.ON
            outcome.summary.substringAfterLast('•').trim().startsWith("Off", true) -> HapticTransition.OFF
            tag.isPhone && tag.deviceId in setOf(PhoneAction.DND_ON, PhoneAction.FLASHLIGHT_ON, PhoneAction.VOLUME_UP) -> HapticTransition.ON
            tag.isPhone && tag.deviceId in setOf(PhoneAction.DND_OFF, PhoneAction.FLASHLIGHT_OFF, PhoneAction.VOLUME_DOWN) -> HapticTransition.OFF
            tag.isPhone && tag.deviceId in setOf(PhoneAction.DND_TOGGLE, PhoneAction.FLASHLIGHT_TOGGLE, PhoneAction.VOLUME_MUTE_TOGGLE) -> HapticTransition.TOGGLE
            tag.actionType == ActionType.TOGGLE -> HapticTransition.TOGGLE
            else -> HapticTransition.OTHER
        }
        return HapticSemanticDescriptor(
            mappingId = context.mappingIdentity ?: "${context.source}:${tag.tagId}:${context.physicalInput.orEmpty()}",
            source = when (context.source) { TriggerSource.NFC -> HapticActivationSource.NFC; TriggerSource.CONTROLLER -> HapticActivationSource.GAMEPAD; TriggerSource.IN_APP -> HapticActivationSource.UI },
            physicalInput = context.physicalInput,
            actionClass = actionClass,
            targetIdentity = tag.tagId,
            transition = transition,
            result = if (outcome.success) HapticResult.SUCCESS else HapticResult.FAILURE
        )
    }

    internal fun adapt(pattern: HapticPattern, descriptor: HapticSemanticDescriptor): HapticPattern {
        if (descriptor.result == HapticResult.FAILURE) return HapticPattern("failure", pattern.family, HapticEnvelope.WARNING, listOf(HapticChannelPattern(HapticSide.CENTRE, listOf(0, 105, 75, 105, 75, 130), listOf(0, 255, 0, 255, 0, 255))))
        val channel = pattern.channels.first()
        val on = descriptor.transition == HapticTransition.ON
        val off = descriptor.transition == HapticTransition.OFF
        if (!on && !off) return pattern
        val nonZero = channel.amplitudes.filter { it > 0 }.sorted()
        var index = 0
        val shaped = channel.amplitudes.map { amplitude ->
            if (amplitude == 0) 0 else (if (on) nonZero[index++] else nonZero.reversed()[index++])
        }
        return pattern.copy(envelope = if (on) HapticEnvelope.RISING else HapticEnvelope.FALLING, channels = listOf(channel.copy(amplitudes = shaped)))
    }

    private fun renderExternal(deviceId: Int, caps: ControllerHapticCapabilities, pattern: HapticPattern) {
        val device: InputDevice = inputManager.getInputDevice(deviceId) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = device.vibratorManager
            manager.cancel()
            val parallel = CombinedVibration.startParallel()
            caps.vibratorIds.forEachIndexed { index, id ->
                val channel = pattern.channels.getOrElse(index) { pattern.channels.first() }
                parallel.addVibrator(id, effect(channel, caps.amplitudeControlById[id] == true))
            }
            manager.vibrate(parallel.combine())
        } else {
            @Suppress("DEPRECATION") val vibrator = device.vibrator
            vibrator.cancel(); render(vibrator, pattern.channels.first(), vibrator.hasAmplitudeControl())
        }
    }

    private fun render(vibrator: Vibrator, channel: HapticChannelPattern, amplitudeControl: Boolean) {
        vibrator.cancel(); vibrator.vibrate(effect(channel, amplitudeControl))
    }

    private fun effect(channel: HapticChannelPattern, amplitudeControl: Boolean): VibrationEffect {
        val timings = channel.timingsMs.toLongArray()
        if (!amplitudeControl) return VibrationEffect.createWaveform(timings, -1)
        val amplitudes = channel.amplitudes.map { value -> (value * intensity.scale).roundToInt().coerceIn(0, 255) }.toIntArray()
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }
}

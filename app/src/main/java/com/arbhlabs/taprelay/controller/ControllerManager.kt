package com.arbhlabs.taprelay.controller

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.VibrationEffect
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.arbhlabs.taprelay.controller.model.ControllerDevice
import com.arbhlabs.taprelay.controller.model.ControllerInput
import com.arbhlabs.taprelay.controller.model.ControllerKeys
import com.arbhlabs.taprelay.controller.AdjustmentConfig
import com.arbhlabs.taprelay.controller.AdjustmentEvent
import com.arbhlabs.taprelay.controller.PostActivationAdjustmentSession
import com.arbhlabs.taprelay.data.local.dao.ControllerMappingDao
import com.arbhlabs.taprelay.execution.HapticsManager
import com.arbhlabs.taprelay.execution.TapFeedback
import com.arbhlabs.taprelay.haptics.HapticActivationContext
import com.arbhlabs.taprelay.trigger.ActivationPresenter
import com.arbhlabs.taprelay.trigger.TriggerRouter
import com.arbhlabs.taprelay.trigger.TriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ControllerManager(
    private val context: Context,
    private val mappingDao: ControllerMappingDao,
    private val triggerRouter: TriggerRouter,
    private val haptics: HapticsManager,
    private val automaticLightControls: AutomaticLightControls,
    var onFeedback: ((TapFeedback) -> Unit)? = null,
    /**
     * Called with a button's label when a decoded press matched no active mapping. The global
     * accessibility service uses it to tell a tester "delivered, but nothing is mapped here"
     * instead of the press silently doing nothing.
     */
    var onUnmappedInput: ((String) -> Unit)? = null,
    /** Set by whichever activity is on screen, so a button can open a surface as well as fire. */
    var presenter: ActivationPresenter? = null,
    var onAdjustmentEvent: ((AdjustmentEvent) -> Unit)? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) : InputManager.InputDeviceListener {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val processor = ControllerInputProcessor()
    @Volatile var adjustmentConfig: AdjustmentConfig = AdjustmentConfig()
        private set
    private var adjustmentSession = PostActivationAdjustmentSession(adjustmentConfig)

    fun setAdjustmentConfig(config: AdjustmentConfig) {
        adjustmentConfig = config
        adjustmentSession = PostActivationAdjustmentSession(config)
    }

    /** Arms only after the routed action confirmed ON; OFF/toggle callers must pass false. */
    fun startPostActivationAdjustment(poweredOn: Boolean) {
        adjustmentSession.start(poweredOn)
    }

    /**
     * Every enabled mapping's input key, kept live from the database. The global accessibility
     * service uses it to decide *synchronously* whether to swallow a press: a button nobody has
     * mapped passes straight through to the app in front, so the service never steals a button a
     * game or another app needs. Empty until the first DB emission.
     */
    @Volatile
    private var mappedInputKeys: Set<String> = emptySet()

    init {
        automaticLightControls.onAdjustmentEnded = { rumbleAdjustmentEnded() }
        automaticLightControls.onAdjustmentLimit = { rumbleAdjustmentLimit() }
        scope.launch {
            mappingDao.observeAll().collectLatest { rows ->
                mappedInputKeys = rows.asSequence()
                    .filter { it.enabled }
                    .map { it.inputKey }
                    .toSet()
            }
        }
    }

    /** True if this exact input, or a chord that contains it, is a live mapping. */
    fun isInputMapped(inputKey: String): Boolean {
        val keys = mappedInputKeys
        if (keys.isEmpty()) return false
        if (inputKey in keys) return true
        return keys.any { it.contains('+') && it.split('+').contains(inputKey) }
    }

    /**
     * Key codes whose DOWN this manager consumed. The matching UP must be consumed too: Android
     * uses a button's release to finish focus navigation or to activate the focused view, so
     * letting the UP through would let a bound button also click whatever is under it.
     */
    private val consumedDownKeys = HashSet<Int>()

    /** Set from preferences. A pad with no motor ignores this either way. */
    @Volatile
    var rumbleEnabled: Boolean = true

    /** The pad that sent the press being handled, so the right controller buzzes. */
    private var lastInputDeviceId: Int = -1

    private val _connectedControllers = MutableStateFlow<List<ControllerDevice>>(emptyList())
    val connectedControllers: StateFlow<List<ControllerDevice>> = _connectedControllers.asStateFlow()
    val automaticLightControlState: StateFlow<AutomaticLightControlState> = automaticLightControls.state

    fun selectAutomaticLight(tagId: String) = automaticLightControls.select(tagId)

    private val _isLearningMode = MutableStateFlow(false)
    val isLearningMode: StateFlow<Boolean> = _isLearningMode.asStateFlow()

    private val _capturedInput = MutableStateFlow<ControllerInput?>(null)
    val capturedInput: StateFlow<ControllerInput?> = _capturedInput.asStateFlow()

    /** Every decoded press, learning or not, so a live view can light the button up. */
    private val _lastInput = MutableStateFlow<Pair<ControllerInput, Long>?>(null)
    val lastInput: StateFlow<Pair<ControllerInput, Long>?> = _lastInput.asStateFlow()

    private companion object {
        const val TAG = "TapRelayController"
    }

    /**
     * How many components (the foreground activity, Remote Mode, the always-app accessibility
     * service) currently want device tracking. The [InputManager] listener is registered on the
     * first and unregistered on the last, so an activity pausing never blinds a still-running
     * accessibility service.
     */
    private var listenerRefCount = 0

    fun startListening() {
        try {
            if (listenerRefCount++ == 0) {
                inputManager.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
            }
            refreshConnectedControllers()
        } catch (e: Exception) {
            Log.w(TAG, "Could not register InputDeviceListener", e)
        }
    }

    fun stopListening() {
        try {
            if (listenerRefCount > 0 && --listenerRefCount == 0) {
                inputManager.unregisterInputDeviceListener(this)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering InputDeviceListener", e)
        }
    }

    fun startLearning() {
        processor.reset()
        _capturedInput.value = null
        _isLearningMode.value = true
    }

    fun stopLearning() {
        _isLearningMode.value = false
        _capturedInput.value = null
        processor.reset()
        consumedDownKeys.clear()
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        refreshConnectedControllers()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        refreshConnectedControllers()
        // No pad left to adjust with or to buzz: close the window quietly.
        if (_connectedControllers.value.isEmpty()) automaticLightControls.endAdjustment(notify = false)
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        refreshConnectedControllers()
    }

    fun refreshConnectedControllers() {
        val list = mutableListOf<ControllerDevice>()
        val ids = inputManager.inputDeviceIds ?: intArrayOf()
        for (id in ids) {
            val dev = inputManager.getInputDevice(id) ?: continue
            val sources = dev.sources
            val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            val isJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if ((isGamepad || isJoystick) && !dev.isVirtual) {
                val descriptor = if (dev.descriptor.isNotBlank()) dev.descriptor else "${dev.vendorId}:${dev.productId}"
                list.add(
                    ControllerDevice(
                        id = id,
                        name = if (dev.name.isNotBlank()) dev.name else "Standard Gamepad",
                        descriptor = descriptor,
                        vendorId = dev.vendorId,
                        productId = dev.productId,
                        isGamepad = isGamepad,
                        batteryPercent = batteryPercentOf(dev)
                    )
                )
            }
        }
        _connectedControllers.value = list
    }

    /**
     * Called from Activity.dispatchKeyEvent, and from the global accessibility service.
     * Returns true if event was consumed.
     *
     * [consumeUnmapped] is true for an on-screen activity - swallowing a stray controller key
     * there just stops focus drifting. The global service passes false: a button with no mapping
     * must reach whatever app is in front, untouched.
     */
    fun handleKeyEvent(event: KeyEvent, consumeUnmapped: Boolean = true): Boolean {
        val isGamepadKey = com.arbhlabs.taprelay.controller.model.ControllerKeys.isGamepadSpecificKey(event.keyCode)
        val isControllerSource = ControllerInputProcessor.isGameControllerEvent(event.source, event.device)
        val hasControllerAttached = _connectedControllers.value.isNotEmpty()

        val isControllerInput = isGamepadKey || isControllerSource ||
            (hasControllerAttached && com.arbhlabs.taprelay.controller.model.ControllerKeys.normalizeKeyCode(event.keyCode) != null)
        if (!isControllerInput) {
            return false
        }

        // A consumed DOWN keeps its UP. Otherwise Android completes a focus move or a click in
        // whatever is underneath when the button is released. Still let the processor see the UP
        // so its modifier bookkeeping stays in sync.
        if (event.action == KeyEvent.ACTION_UP) {
            val ownedUp = consumedDownKeys.remove(event.keyCode)
            processor.processKeyEvent(event)
            return ownedUp || (_isLearningMode.value && consumeUnmapped)
        }

        // While learning, swallow every controller key - including the ups and repeats the
        // processor ignores - so the press assigns a button instead of moving system focus.
        val input = processor.processKeyEvent(event)
            ?: return _isLearningMode.value && consumeUnmapped
        val activeDev = _connectedControllers.value.firstOrNull()
        val descriptor = event.device?.descriptor?.ifBlank { activeDev?.descriptor ?: "*" } ?: activeDev?.descriptor ?: "*"
        val controllerName = event.device?.name?.ifBlank { activeDev?.name ?: "Gamepad" } ?: activeDev?.name ?: "Gamepad"
        // Inside the light-adjustment window the D-pad is borrowed, mapped or not.
        if (!_isLearningMode.value && automaticLightControls.onDpad(input.key)) {
            if (event.deviceId >= 0) lastInputDeviceId = event.deviceId
            _lastInput.value = input to System.currentTimeMillis()
            consumedDownKeys.add(event.keyCode)
            return true
        }
        // The global service must not steal a button nobody mapped: let it fall through to the app.
        if (!consumeUnmapped && !_isLearningMode.value && !isInputMapped(input.key)) {
            _lastInput.value = input to System.currentTimeMillis()
            onUnmappedInput?.invoke(input.label)
            return false
        }
        val consumed = handleInputDetected(input, descriptor, controllerName, event.deviceId)
        if (consumed && event.action == KeyEvent.ACTION_DOWN) consumedDownKeys.add(event.keyCode)
        return consumed
    }

    /**
     * Called from Activity.dispatchGenericMotionEvent.
     * Returns true if event was consumed.
     */
    fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        val isControllerSource = ControllerInputProcessor.isGameControllerEvent(event.source, event.device)
        val hasControllerAttached = _connectedControllers.value.isNotEmpty()
        if (!isControllerSource && !hasControllerAttached) {
            return false
        }

        // The left stick no longer adjusts lights (too sensitive); the D-pad does, briefly, after
        // the light turns on - see AutomaticLightControls. Stick motion keeps its normal role.
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        if (adjustmentSession.isActive()) {
            val adjustment = adjustmentSession.onStick(
                x,
                y,
                currentBrightness = 50
            )
            if (adjustment != null) {
                onAdjustmentEvent?.invoke(adjustment)
                return true
            }
        }

        val input = processor.processMotionEvent(event) ?: return _isLearningMode.value
        val activeDev = _connectedControllers.value.firstOrNull()
        val descriptor = event.device?.descriptor?.ifBlank { activeDev?.descriptor ?: "*" } ?: activeDev?.descriptor ?: "*"
        val controllerName = event.device?.name?.ifBlank { activeDev?.name ?: "Gamepad" } ?: activeDev?.name ?: "Gamepad"
        return handleInputDetected(input, descriptor, controllerName, event.deviceId)
    }

    /**
     * Buzzes the pad that was just pressed, so a toggle is felt in the hand holding the remote
     * rather than in a phone across the room.
     *
     * Gamepad motors are coarse rotating-mass units, not the phone's linear actuator, so a short
     * light tick simply is not felt. Success is shaped as a quick rise into a full-strength thud
     * that decays; failure is three unmistakable knocks. The best form the pad actually supports
     * is used: haptic primitives if it has them, amplitude-controlled waveforms if not, and a
     * plain on/off pattern with generous timings as the floor.
     */
    private fun rumble(success: Boolean) {
        if (!rumbleEnabled) return
        runCatching {
            val dev = inputManager.getInputDevice(lastInputDeviceId) ?: return
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dev.vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION") dev.vibrator
            }
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(rumbleEffect(vibrator, success))
        }
    }

    /**
     * The single "D-pad is yours again" cue: two even knocks, distinct from the rise-and-thud of
     * a success and the triple knock of a failure. Falls back to any connected pad.
     */
    private fun rumbleAdjustmentEnded() =
        vibratePad(longArrayOf(0, 80, 110, 80), intArrayOf(0, 255, 0, 255))

    /** One short, soft tick: "that's the brightest/dimmest it goes". */
    private fun rumbleAdjustmentLimit() =
        vibratePad(longArrayOf(0, 35), intArrayOf(0, 140))

    private fun vibratePad(timings: LongArray, amplitudes: IntArray) {
        if (!rumbleEnabled) return
        runCatching {
            val dev = inputManager.getInputDevice(lastInputDeviceId)
                ?: _connectedControllers.value.firstOrNull()?.let { inputManager.getInputDevice(it.id) }
                ?: return
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dev.vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION") dev.vibrator
            }
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(
                if (vibrator.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, amplitudes, -1)
                else VibrationEffect.createWaveform(timings, -1)
            )
        }
    }

    private fun rumbleEffect(vibrator: android.os.Vibrator, success: Boolean): VibrationEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wanted = if (success) {
                intArrayOf(
                    VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                    VibrationEffect.Composition.PRIMITIVE_THUD
                )
            } else {
                intArrayOf(VibrationEffect.Composition.PRIMITIVE_CLICK)
            }
            if (vibrator.areAllPrimitivesSupported(*wanted)) {
                val composition = VibrationEffect.startComposition()
                if (success) {
                    composition
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.7f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 20)
                } else {
                    composition
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 90)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 90)
                }
                return composition.compose()
            }
        }

        val timings: LongArray
        val amplitudes: IntArray
        if (success) {
            // Rise, hit, decay - reads as one confident "done" rather than a flat buzz.
            timings = longArrayOf(0, 40, 20, 130, 90)
            amplitudes = intArrayOf(0, 170, 0, 255, 90)
        } else {
            timings = longArrayOf(0, 110, 80, 110, 80, 110)
            amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
        }
        return if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            // No amplitude control: the motor is either on or off, so lean on duration.
            VibrationEffect.createWaveform(timings, -1)
        }
    }

    /** Bluetooth pads report their own charge from Android 12; wired ones report nothing. */
    private fun batteryPercentOf(dev: InputDevice): Int? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return null
        return runCatching {
            val state = dev.batteryState ?: return null
            if (!state.isPresent) return null
            val capacity = state.capacity
            if (capacity.isNaN() || capacity < 0f) null else (capacity * 100f).toInt().coerceIn(0, 100)
        }.getOrNull()
    }

    private fun handleInputDetected(
        input: ControllerInput,
        descriptor: String,
        controllerName: String,
        deviceId: Int = -1
    ): Boolean {
        if (deviceId >= 0) lastInputDeviceId = deviceId
        _lastInput.value = input to System.currentTimeMillis()
        // 1. If currently in learning mode, capture the input for the UI
        if (_isLearningMode.value) {
            _capturedInput.value = input
            haptics.vibrateClick()
            return true
        }

        // 2. A D-pad press inside the light-adjustment window steps the light instead.
        if (automaticLightControls.onDpad(input.key)) return true

        // 3. Otherwise, check for an active mapping in the database
        scope.launch {
            val mapping = withContext(Dispatchers.IO) {
                mappingDao.findActiveMapping(descriptor, input.key)
                    ?: mappingDao.findActiveMapping("*", input.key)
            }

            if (mapping != null) {
                automaticLightControls.select(mapping.tagId)
                onFeedback?.invoke(
                    TapFeedback(
                        title = "${mapping.inputLabel} → Triggering…",
                        isError = false,
                        pending = true
                    )
                )
                // The button is one trigger among several; the router decides what firing means.
                triggerRouter.fire(
                    tagId = mapping.tagId,
                    override = mapping.activationMode,
                    source = TriggerSource.CONTROLLER,
                    hapticContext = HapticActivationContext(
                        source = TriggerSource.CONTROLLER,
                        inputDeviceId = deviceId,
                        physicalInput = input.key,
                        mappingIdentity = "controller:${mapping.controllerDescriptor}:${mapping.inputKey}:${mapping.tagId}"
                    ),
                    presenter = presenter
                ) { fb ->
                    if (!fb.pending && !fb.isError) automaticLightControls.onActivationSettled(mapping.tagId)
                    onFeedback?.invoke(fb)
                }
            } else {
                Log.d(TAG, "Unmapped controller input: ${input.key} (${input.label}) from $controllerName")
                onUnmappedInput?.invoke(input.label)
            }
        }

        return true
    }

    /**
     * Narrow cross-app hand-off used only while LastDose's always-on activity owns focus. Android
     * gives the focused window controller events, so LastDose forwards normalized DOWN presses to
     * TapRelay's caller-authenticated provider instead of either app using an overlay/service.
     */
    fun handleForwardedInput(inputKey: String, descriptor: String, controllerName: String, deviceId: Int): Boolean {
        val normalized = ControllerKeys.normalizeKeyCodeName(inputKey) ?: return false
        return handleInputDetected(ControllerInput(normalized, ControllerKeys.formatLabel(normalized)), descriptor, controllerName, deviceId)
    }
}

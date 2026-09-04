package com.arbhlabs.taprelay.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.data.secure.TuyaCredentials
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.powerIntent
import com.arbhlabs.taprelay.domain.model.Brightness
import com.arbhlabs.taprelay.domain.model.ColorMath
import com.arbhlabs.taprelay.domain.model.LightPresets
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.domain.model.DiscoveredScene
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.model.TagTarget
import com.arbhlabs.taprelay.domain.model.TargetType
import com.arbhlabs.taprelay.domain.provider.GOVEE_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.SENSIBO_PROVIDER_ID
import com.arbhlabs.taprelay.domain.provider.TUYA_PROVIDER_ID
import com.arbhlabs.taprelay.execution.TapFeedback
import com.arbhlabs.taprelay.data.local.entity.TapLogEntity
import com.arbhlabs.taprelay.monetization.TapRelayProFeature
import com.arbhlabs.taprelay.nfc.NfcPayloadParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WizardStep { SCAN, PICK_DEVICE, PICK_ACTION, TUNE, NAME, DONE }

/** Sub-states of the SCAN step while the user holds a sticker to the phone. */
enum class ScanPhase { READY, DETECTED, WRITING, CONFIRM_OVERWRITE, ERROR }

data class WizardState(
    val active: Boolean = false,
    val step: WizardStep = WizardStep.SCAN,
    val scanPhase: ScanPhase = ScanPhase.READY,
    val allowOverwrite: Boolean = false,
    val editingExisting: Boolean = false,
    val tagId: String? = null,
    val discovering: Boolean = false,
    val targetType: TargetType = TargetType.DEVICE,
    val devices: List<DiscoveredDevice> = emptyList(),
    val scenes: List<DiscoveredScene> = emptyList(),
    val device: DiscoveredDevice? = null,
    /** Every light picked for this tag, in the order they were chosen. */
    val selectedDevices: List<DiscoveredDevice> = emptyList(),
    /** Device ids to re-select once discovery finishes, when editing an existing tag. */
    val pendingTargetIds: List<String> = emptyList(),
    val scene: DiscoveredScene? = null,
    val action: ActionType = ActionType.TOGGLE,
    val brightnessPercent: Int = Brightness.DEFAULT_PERCENT,
    /** Colour and brightness are optional extras on top of whatever the power action is. */
    val wantsColor: Boolean = false,
    val wantsBrightness: Boolean = false,
    val colorRgb: Int = LightPresets.DEFAULT_RGB,
    /** Which face of the colour picker is showing: named presets or the white-temperature slider. */
    val colorPickingWhite: Boolean = false,
    /** Slider position for the white-temperature face, in kelvin. */
    val whiteKelvin: Int = 2700,
    val name: String = "",
    val iconKey: String = "lamp",
    val error: String? = null,
    val busy: Boolean = false,
    /** Pro: Context-aware time-of-day condition window. */
    val timeConditionEnabled: Boolean = false,
    val startHour: Int = 8,
    val startMinute: Int = 0,
    val endHour: Int = 22,
    val endMinute: Int = 0,
    val offActionType: ActionType = ActionType.TURN_OFF
)

data class HomeUiState(
    val loading: Boolean = true,
    val onboardingComplete: Boolean = false,
    val goveeConnected: Boolean = false,
    val goveeDeviceCount: Int = 0,
    val tuyaConnected: Boolean = false,
    val tuyaDeviceCount: Int = 0,
    val tuyaSceneCount: Int = 0,
    val sensiboConnected: Boolean = false,
    val sensiboDeviceCount: Int = 0,
    val isPro: Boolean = false,
    val isTrialActive: Boolean = false,
    val trialDaysRemaining: Int = 0,
    val tags: List<TagEntity> = emptyList()
)

class TapRelayViewModel(app: Application) : AndroidViewModel(app) {

    private val services = (app as TapRelayApplication).services

    private val goveeConnected = MutableStateFlow(false)
    private val goveeDeviceCount = MutableStateFlow(0)
    private val tuyaConnected = MutableStateFlow(false)
    private val tuyaDeviceCount = MutableStateFlow(0)
    private val tuyaSceneCount = MutableStateFlow(0)
    private val sensiboConnected = MutableStateFlow(false)
    private val sensiboDeviceCount = MutableStateFlow(0)

    private data class Connections(
        val goveeConnected: Boolean,
        val goveeDeviceCount: Int,
        val tuyaConnected: Boolean,
        val tuyaDeviceCount: Int,
        val tuyaSceneCount: Int,
        val sensiboConnected: Boolean,
        val sensiboDeviceCount: Int
    )

    val isPro: StateFlow<Boolean> = services.entitlementRepository.isPro
    val proLicense = services.entitlementRepository.proLicense

    val tapLogs: Flow<List<TapLogEntity>> = services.tapLogDao.getRecentLogs()

    private val goveeState = combine(goveeConnected, goveeDeviceCount) { c, d -> c to d }
    private val tuyaState = combine(tuyaConnected, tuyaDeviceCount, tuyaSceneCount) { c, d, s -> Triple(c, d, s) }
    private val sensiboState = combine(sensiboConnected, sensiboDeviceCount) { c, d -> c to d }

    private val connections = combine(goveeState, tuyaState, sensiboState) { g, t, s ->
        Connections(
            goveeConnected = g.first,
            goveeDeviceCount = g.second,
            tuyaConnected = t.first,
            tuyaDeviceCount = t.second,
            tuyaSceneCount = t.third,
            sensiboConnected = s.first,
            sensiboDeviceCount = s.second
        )
    }

    val ui: StateFlow<HomeUiState> = combine(
        services.preferences.onboardingComplete,
        services.tagRepository.getAllTags(),
        connections,
        isPro,
        proLicense
    ) { onboarded, tags, c, pro, lic ->
        val trialActive = lic != null && lic.tier == "trial" && (lic.expiresAt == 0L || lic.expiresAt > System.currentTimeMillis())
        val daysRemaining = if (trialActive && lic != null) {
            val diff = lic.expiresAt - System.currentTimeMillis()
            val oneDayMs = 1000L * 60L * 60L * 24L
            if (diff > 0L) ((diff + oneDayMs - 1L) / oneDayMs).toInt() else 0
        } else 0
        HomeUiState(
            loading = false,
            onboardingComplete = onboarded,
            goveeConnected = c.goveeConnected,
            goveeDeviceCount = c.goveeDeviceCount,
            tuyaConnected = c.tuyaConnected,
            tuyaDeviceCount = c.tuyaDeviceCount,
            tuyaSceneCount = c.tuyaSceneCount,
            sensiboConnected = c.sensiboConnected,
            sensiboDeviceCount = c.sensiboDeviceCount,
            isPro = pro,
            isTrialActive = trialActive,
            trialDaysRemaining = daysRemaining,
            tags = tags
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private val _wizard = MutableStateFlow(WizardState())
    val wizard = _wizard.asStateFlow()

    private val _pill = MutableStateFlow<TapFeedback?>(null)
    val pill = _pill.asStateFlow()

    private val _setupPrompt = MutableStateFlow<String?>(null)
    val setupPrompt = _setupPrompt.asStateFlow()

    private val _nfcReady = MutableStateFlow(true)
    val nfcReady = _nfcReady.asStateFlow()
    fun setNfcReady(ready: Boolean) { _nfcReady.value = ready }

    init {
        refreshConnections()
    }

    fun refreshConnections() = viewModelScope.launch {
        val gConn = services.goveeProvider.isConnected()
        if (gConn) {
            runCatching {
                val devs = services.goveeProvider.discoverDevices()
                goveeDeviceCount.value = devs.size
            }
        } else {
            goveeDeviceCount.value = 0
        }
        goveeConnected.value = gConn

        val tConn = services.tuyaProvider.isConnected()
        if (tConn) {
            runCatching {
                val devs = services.tuyaProvider.discoverDevices()
                val scenes = services.tuyaProvider.discoverScenes()
                tuyaDeviceCount.value = devs.size
                tuyaSceneCount.value = scenes.size
            }
        } else {
            tuyaDeviceCount.value = 0
            tuyaSceneCount.value = 0
        }
        tuyaConnected.value = tConn

        val sConn = services.sensiboProvider.isConnected()
        if (sConn) {
            runCatching {
                val pods = services.sensiboProvider.discoverDevices()
                sensiboDeviceCount.value = pods.size
            }
        } else {
            sensiboDeviceCount.value = 0
        }
        sensiboConnected.value = sConn
    }

    // ---- Onboarding / provider ----

    fun completeOnboarding() = viewModelScope.launch {
        services.preferences.setOnboardingComplete(true)
    }

    private val _connectState = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val connectState = _connectState.asStateFlow()

    sealed interface ConnectState {
        data object Idle : ConnectState
        data object Validating : ConnectState
        data class Error(val message: String) : ConnectState
        data class Success(val title: String, val count: Int) : ConnectState
    }

    fun connectGovee(apiKey: String) = viewModelScope.launch {
        if (apiKey.isBlank()) {
            _connectState.value = ConnectState.Error("Paste your Govee key to continue.")
            return@launch
        }
        _connectState.value = ConnectState.Validating
        try {
            val devices = services.goveeProvider.validateAndSave(apiKey)
            goveeConnected.value = true
            goveeDeviceCount.value = devices.size
            _connectState.value = ConnectState.Success("Govee", devices.size)
        } catch (e: TapException) {
            _connectState.value = ConnectState.Error(e.error.message)
        } catch (e: Exception) {
            _connectState.value = ConnectState.Error("Couldn't reach Govee. Check your connection.")
        }
    }

    fun connectTuya(
        accessId: String,
        secret: String,
        region: String = "us",
        uid: String = ""
    ) = viewModelScope.launch {
        if (accessId.isBlank() || secret.isBlank()) {
            _connectState.value = ConnectState.Error("Enter your Access ID and Access Secret.")
            return@launch
        }
        _connectState.value = ConnectState.Validating
        try {
            val creds = TuyaCredentials(
                accessId = accessId.trim(),
                accessSecret = secret.trim(),
                region = region.trim().ifBlank { "us" },
                uid = uid.trim()
            )
            val (devices, scenes) = services.tuyaProvider.validateAndSave(creds)
            tuyaConnected.value = true
            tuyaDeviceCount.value = devices.size
            tuyaSceneCount.value = scenes.size
            _connectState.value = ConnectState.Success("Smart Life", devices.size)
        } catch (e: TapException) {
            _connectState.value = ConnectState.Error(e.error.message)
        } catch (e: Exception) {
            _connectState.value = ConnectState.Error("Couldn't reach Smart Life. Check your connection.")
        }
    }

    fun connectSensibo(apiKey: String) = viewModelScope.launch {
        if (apiKey.isBlank()) {
            _connectState.value = ConnectState.Error("Paste your Sensibo API key to continue.")
            return@launch
        }
        _connectState.value = ConnectState.Validating
        try {
            val pods = services.sensiboProvider.validateAndSave(apiKey)
            sensiboConnected.value = true
            sensiboDeviceCount.value = pods.size
            _connectState.value = ConnectState.Success("Sensibo", pods.size)
        } catch (e: TapException) {
            _connectState.value = ConnectState.Error(e.error.message)
        } catch (e: Exception) {
            _connectState.value = ConnectState.Error("Couldn't reach Sensibo. Check your connection.")
        }
    }

    fun resetConnectState() { _connectState.value = ConnectState.Idle }

    fun disconnectGovee() = viewModelScope.launch {
        services.secureKeyStorage.clearGoveeApiKey()
        goveeConnected.value = false
        goveeDeviceCount.value = 0
    }

    fun disconnectTuya() = viewModelScope.launch {
        services.secureKeyStorage.clearTuyaCredentials()
        services.tuyaProvider.clearCredentials()
        tuyaConnected.value = false
        tuyaDeviceCount.value = 0
        tuyaSceneCount.value = 0
    }

    fun disconnectSensibo() = viewModelScope.launch {
        services.secureKeyStorage.clearSensiboApiKey()
        sensiboConnected.value = false
        sensiboDeviceCount.value = 0
    }

    // ---- Monetization & Diagnostics ----

    fun activateLicense(key: String, onResult: (Result<String>) -> Unit) = viewModelScope.launch {
        val res = services.entitlementRepository.activateLicense(key)
        onResult(res)
    }

    fun startFreeTrial(onResult: (Result<String>) -> Unit) = viewModelScope.launch {
        val res = services.entitlementRepository.startFreeTrial()
        onResult(res)
    }

    fun deactivateLicense() = viewModelScope.launch {
        services.entitlementRepository.deactivate()
    }

    fun replay(tagId: String) = viewModelScope.launch {
        services.actionExecutor.replay(tagId) { fb -> _pill.value = fb }
    }

    // ---- Add-tag wizard ----

    fun startWizard() {
        _wizard.value = WizardState(active = true, step = WizardStep.SCAN)
    }

    fun cancelWizard() { _wizard.value = WizardState() }

    // ---- SCAN step transitions (driven by the Activity's NFC callback) ----

    fun onTagDetected() {
        if (_wizard.value.step == WizardStep.SCAN)
            _wizard.value = _wizard.value.copy(scanPhase = ScanPhase.DETECTED, error = null)
    }

    fun onWriting() {
        _wizard.value = _wizard.value.copy(scanPhase = ScanPhase.WRITING, error = null)
    }

    fun onNeedOverwriteConfirm() {
        _wizard.value = _wizard.value.copy(scanPhase = ScanPhase.CONFIRM_OVERWRITE, error = null)
    }

    fun confirmOverwrite() {
        _wizard.value = _wizard.value.copy(allowOverwrite = true, scanPhase = ScanPhase.READY, error = null)
    }

    fun retryScan() {
        _wizard.value = _wizard.value.copy(scanPhase = ScanPhase.READY, error = null)
    }

    /** Called by the Activity once a tag has been written with [tagId]. */
    fun onTagWritten(tagId: String) {
        _wizard.value = _wizard.value.copy(
            tagId = tagId, step = WizardStep.PICK_DEVICE, scanPhase = ScanPhase.READY, error = null
        )
        discoverDevicesAndScenes()
    }

    /** A tag scanned in the wizard that already carries a valid TapRelay identity. */
    fun onExistingTapRelayTag(tagId: String) = viewModelScope.launch {
        val existing = services.tagRepository.getTagById(tagId)
        _wizard.value = WizardState(
            active = true,
            step = WizardStep.PICK_DEVICE,
            editingExisting = true,
            tagId = tagId,
            targetType = existing?.targetType ?: TargetType.DEVICE,
            action = existing?.actionType ?: ActionType.TOGGLE,
            brightnessPercent = existing?.brightnessPercent ?: Brightness.DEFAULT_PERCENT,
            colorRgb = existing?.colorRgb ?: LightPresets.DEFAULT_RGB,
            wantsColor = existing?.colorRgb != null,
            colorPickingWhite = existing?.colorRgb?.let { ColorMath.nearestKelvin(it) != null } ?: false,
            whiteKelvin = existing?.colorRgb?.let { ColorMath.nearestKelvin(it) } ?: 2700,
            wantsBrightness = existing?.brightnessPercent != null,
            pendingTargetIds = existing?.allTargets?.map { it.deviceId } ?: emptyList(),
            name = existing?.friendlyName ?: "",
            iconKey = existing?.iconKey ?: "lamp",
            timeConditionEnabled = existing?.timeConditionEnabled ?: false,
            startHour = existing?.startHour ?: 8,
            startMinute = existing?.startMinute ?: 0,
            endHour = existing?.endHour ?: 22,
            endMinute = existing?.endMinute ?: 0,
            offActionType = existing?.offActionType ?: ActionType.TURN_OFF
        )
        discoverDevicesAndScenes()
    }

    fun onWizardWriteError(message: String) {
        _wizard.value = _wizard.value.copy(scanPhase = ScanPhase.ERROR, error = message)
    }

    fun editTag(tag: TagEntity) {
        _wizard.value = WizardState(
            active = true,
            step = WizardStep.PICK_DEVICE,
            editingExisting = true,
            tagId = tag.tagId,
            targetType = tag.targetType,
            action = tag.actionType,
            brightnessPercent = tag.brightnessPercent ?: Brightness.DEFAULT_PERCENT,
            colorRgb = tag.colorRgb ?: LightPresets.DEFAULT_RGB,
            wantsColor = tag.colorRgb != null,
            colorPickingWhite = tag.colorRgb?.let { ColorMath.nearestKelvin(it) != null } ?: false,
            whiteKelvin = tag.colorRgb?.let { ColorMath.nearestKelvin(it) } ?: 2700,
            wantsBrightness = tag.brightnessPercent != null,
            pendingTargetIds = tag.allTargets.map { it.deviceId },
            name = tag.friendlyName,
            iconKey = tag.iconKey,
            timeConditionEnabled = tag.timeConditionEnabled,
            startHour = tag.startHour ?: 8,
            startMinute = tag.startMinute ?: 0,
            endHour = tag.endHour ?: 22,
            endMinute = tag.endMinute ?: 0,
            offActionType = tag.offActionType ?: ActionType.TURN_OFF
        )
        discoverDevicesAndScenes()
    }

    fun configureUnregisteredTag(tagId: String) {
        _setupPrompt.value = null
        _wizard.value = WizardState(
            active = true, step = WizardStep.PICK_DEVICE, editingExisting = true, tagId = tagId
        )
        discoverDevicesAndScenes()
    }

    private fun discoverDevicesAndScenes() = viewModelScope.launch {
        _wizard.value = _wizard.value.copy(discovering = true, error = null)
        try {
            val allDevices = mutableListOf<DiscoveredDevice>()
            val allScenes = mutableListOf<DiscoveredScene>()

            if (services.goveeProvider.isConnected()) {
                runCatching { allDevices.addAll(services.goveeProvider.discoverDevices()) }
            }
            if (services.tuyaProvider.isConnected()) {
                runCatching {
                    allDevices.addAll(services.tuyaProvider.discoverDevices())
                    allScenes.addAll(services.tuyaProvider.discoverScenes())
                }
            }
            if (services.sensiboProvider.isConnected()) {
                runCatching { allDevices.addAll(services.sensiboProvider.discoverDevices()) }
            }

            val wanted = _wizard.value.pendingTargetIds
            val reselected = if (wanted.isEmpty()) _wizard.value.selectedDevices
            else wanted.mapNotNull { id -> allDevices.firstOrNull { it.deviceId == id } }

            _wizard.value = _wizard.value.copy(
                discovering = false,
                devices = allDevices,
                scenes = allScenes,
                selectedDevices = reselected,
                pendingTargetIds = emptyList()
            )
        } catch (e: TapException) {
            _wizard.value = _wizard.value.copy(discovering = false, error = e.error.message)
        } catch (e: Exception) {
            _wizard.value = _wizard.value.copy(discovering = false, error = "Couldn't load your devices.")
        }
    }

    fun retryDiscovery() = discoverDevicesAndScenes()

    /** Tapping a light adds it to the tag; tapping it again removes it. */
    fun toggleDeviceSelection(d: DiscoveredDevice) {
        val current = _wizard.value.selectedDevices
        val next = if (current.any { it.deviceId == d.deviceId && it.providerId == d.providerId }) {
            current.filterNot { it.deviceId == d.deviceId && it.providerId == d.providerId }
        } else {
            current + d
        }
        _wizard.value = _wizard.value.copy(selectedDevices = next, scene = null)
    }

    fun continueFromDevices() {
        val picked = _wizard.value.selectedDevices
        if (picked.isEmpty()) return
        val action = _wizard.value.action
        // Drop an action the new selection cannot all perform.
        val stillValid = when (action) {
            ActionType.RUN_SCENE -> false
            else -> true
        }
        _wizard.value = _wizard.value.copy(
            device = picked.first(),
            scene = null,
            targetType = TargetType.DEVICE,
            action = if (stillValid) action else ActionType.TOGGLE,
            name = _wizard.value.name.ifBlank { defaultNameFor(picked) },
            step = WizardStep.PICK_ACTION
        )
    }

    private fun defaultNameFor(picked: List<DiscoveredDevice>): String =
        if (picked.size == 1) picked.first().name
        else "${picked.first().name} +${picked.size - 1}"

    fun selectScene(s: DiscoveredScene) {
        _wizard.value = _wizard.value.copy(
            scene = s,
            device = null,
            targetType = TargetType.SCENE,
            action = ActionType.RUN_SCENE,
            name = _wizard.value.name.ifBlank { s.name },
            iconKey = "scene",
            step = WizardStep.NAME
        )
    }

    /** Picks the power behaviour. Colour and brightness are chosen separately. */
    fun selectAction(a: ActionType) { _wizard.value = _wizard.value.copy(action = a) }

    fun setWantsColor(v: Boolean) { _wizard.value = _wizard.value.copy(wantsColor = v) }

    fun setWantsBrightness(v: Boolean) { _wizard.value = _wizard.value.copy(wantsBrightness = v) }

    fun continueFromAction() {
        val w = _wizard.value
        _wizard.value = w.copy(
            step = if (w.wantsColor || w.wantsBrightness) WizardStep.TUNE else WizardStep.NAME
        )
    }

    fun setBrightness(percent: Int) {
        _wizard.value = _wizard.value.copy(brightnessPercent = Brightness.clampPercent(percent))
    }

    fun setColor(rgb: Int) { _wizard.value = _wizard.value.copy(colorRgb = rgb) }

    /** Switches the colour step between the named presets and the white-temperature slider. */
    fun setColorPickingWhite(white: Boolean) {
        val w = _wizard.value
        _wizard.value = if (white) {
            w.copy(colorPickingWhite = true, colorRgb = com.arbhlabs.taprelay.domain.model.ColorMath.kelvinToRgb(w.whiteKelvin))
        } else {
            w.copy(colorPickingWhite = false)
        }
    }

    fun setWhiteKelvin(kelvin: Int) {
        val k = kelvin.coerceIn(LightPresets.MIN_KELVIN, LightPresets.MAX_KELVIN)
        _wizard.value = _wizard.value.copy(
            whiteKelvin = k,
            colorRgb = com.arbhlabs.taprelay.domain.model.ColorMath.kelvinToRgb(k)
        )
    }

    fun confirmTuning() { _wizard.value = _wizard.value.copy(step = WizardStep.NAME) }

    fun setName(v: String) { _wizard.value = _wizard.value.copy(name = v) }
    fun setIcon(v: String) { _wizard.value = _wizard.value.copy(iconKey = v) }

    fun saveTag() = viewModelScope.launch {
        val w = _wizard.value
        val id = w.tagId ?: return@launch
        _wizard.value = w.copy(busy = true)
        val existing = services.tagRepository.getTagById(id)
        val currentTags = services.tagRepository.getAllTags().first()
        if (existing == null && currentTags.size >= 5 && !services.entitlementRepository.canAccess(TapRelayProFeature.MULTI_DEVICE_ROUTINES)) {
            _wizard.value = w.copy(
                busy = false,
                error = "Free tier limit reached (5 active tags). Upgrade to Pro or start your 7-day free trial for unlimited tags."
            )
            return@launch
        }

        val entity = if (w.targetType == TargetType.SCENE) {
            val scene = w.scene ?: return@launch
            (existing ?: TagEntity(
                tagId = id,
                friendlyName = "",
                iconKey = w.iconKey,
                providerId = scene.providerId,
                deviceId = scene.sceneId,
                deviceSku = "tuya_scene",
                actionType = ActionType.RUN_SCENE,
                targetType = TargetType.SCENE
            )).copy(
                friendlyName = w.name.ifBlank { scene.name },
                iconKey = w.iconKey,
                deviceId = scene.sceneId,
                deviceSku = "tuya_scene",
                providerId = scene.providerId,
                actionType = ActionType.RUN_SCENE,
                targetType = TargetType.SCENE,
                timeConditionEnabled = w.timeConditionEnabled,
                startHour = if (w.timeConditionEnabled) w.startHour else null,
                startMinute = if (w.timeConditionEnabled) w.startMinute else null,
                endHour = if (w.timeConditionEnabled) w.endHour else null,
                endMinute = if (w.timeConditionEnabled) w.endMinute else null,
                offActionType = if (w.timeConditionEnabled) w.offActionType else null,
                modifiedAt = System.currentTimeMillis()
            )
        } else {
            val picked = w.selectedDevices.ifEmpty { listOfNotNull(w.device) }
            val device = picked.firstOrNull() ?: return@launch
            val extras = picked.drop(1).map {
                TagTarget(providerId = it.providerId, deviceId = it.deviceId, sku = it.sku, name = it.name)
            }
            (existing ?: TagEntity(
                tagId = id,
                friendlyName = "",
                iconKey = w.iconKey,
                providerId = device.providerId,
                deviceId = device.deviceId,
                deviceSku = device.sku,
                actionType = w.action,
                targetType = TargetType.DEVICE
            )).copy(
                friendlyName = w.name.ifBlank { device.name },
                iconKey = w.iconKey,
                deviceId = device.deviceId,
                deviceSku = device.sku,
                providerId = device.providerId,
                targetType = TargetType.DEVICE,
                actionType = w.action.powerIntent(),
                brightnessPercent = w.brightnessPercent.takeIf { w.wantsBrightness },
                colorRgb = w.colorRgb.takeIf { w.wantsColor },
                additionalTargets = extras,
                timeConditionEnabled = w.timeConditionEnabled,
                startHour = if (w.timeConditionEnabled) w.startHour else null,
                startMinute = if (w.timeConditionEnabled) w.startMinute else null,
                endHour = if (w.timeConditionEnabled) w.endHour else null,
                endMinute = if (w.timeConditionEnabled) w.endMinute else null,
                offActionType = if (w.timeConditionEnabled) w.offActionType else null,
                modifiedAt = System.currentTimeMillis()
            )
        }

        services.tagRepository.upsert(entity)
        _wizard.value = _wizard.value.copy(busy = false, step = WizardStep.DONE)
    }

    fun setTimeConditionEnabled(enabled: Boolean) {
        _wizard.value = _wizard.value.copy(timeConditionEnabled = enabled)
    }

    fun setTimeWindow(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        _wizard.value = _wizard.value.copy(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute
        )
    }

    fun setOffActionType(action: ActionType) {
        _wizard.value = _wizard.value.copy(offActionType = action)
    }

    // ---- Tag management ----

    fun renameTag(tag: TagEntity, name: String) = viewModelScope.launch {
        services.tagRepository.update(tag.copy(friendlyName = name.ifBlank { tag.friendlyName }))
    }

    fun setTagEnabled(tag: TagEntity, enabled: Boolean) = viewModelScope.launch {
        services.tagRepository.update(tag.copy(enabled = enabled))
    }

    fun deleteTag(tag: TagEntity) = viewModelScope.launch {
        services.tagRepository.delete(tag)
    }

    fun testTag(tag: TagEntity) {
        services.actionExecutor.executeByTagId(tag.tagId) { fb -> _pill.value = fb }
    }

    // ---- Scan handling (from Activity) ----

    fun onForegroundScan(tagId: String?) {
        if (tagId == null) return
        viewModelScope.launch {
            val known = services.tagRepository.getTagById(tagId) != null
            if (known) {
                services.actionExecutor.executeByTagId(tagId) { fb -> _pill.value = fb }
            } else {
                _setupPrompt.value = tagId
            }
        }
    }

    fun dismissSetupPrompt() { _setupPrompt.value = null }
    fun showPill(fb: TapFeedback) { _pill.value = fb }
    fun clearPill() { _pill.value = null }
}

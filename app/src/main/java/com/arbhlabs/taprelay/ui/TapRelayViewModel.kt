package com.arbhlabs.taprelay.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.data.secure.TuyaCredentials
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.ActivationMode
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
import com.arbhlabs.taprelay.controller.model.ControllerDevice
import com.arbhlabs.taprelay.controller.model.ControllerInput
import com.arbhlabs.taprelay.data.local.entity.ControllerMappingEntity
import com.arbhlabs.taprelay.data.local.entity.PlaceTransition
import com.arbhlabs.taprelay.data.local.entity.PlaceTriggerEntity
import com.arbhlabs.taprelay.data.local.entity.TapLogEntity
import com.arbhlabs.taprelay.data.prefs.AodDensity
import com.arbhlabs.taprelay.monetization.TapRelayProFeature
import com.arbhlabs.taprelay.nfc.NfcPayloadParser
import com.arbhlabs.taprelay.trigger.ActivationPresenter
import com.arbhlabs.taprelay.trigger.TriggerSource
import com.arbhlabs.taprelay.ui.quick.QuickControlsSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    val wantsFanSpeed: Boolean = false,
    val fanLevel: String = "high",
    /** Fan levels the picked climate devices actually report. Empty until they are read. */
    val fanLevels: List<String> = emptyList(),
    val readingCapabilities: Boolean = false,
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
    val offActionType: ActionType = ActionType.TURN_OFF,
    /** What a trigger does when it lands on this item. */
    val activationMode: ActivationMode = ActivationMode.EXECUTE
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
    val haConnected: Boolean = false,
    val haEntityCount: Int = 0,
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
    private val haConnected = MutableStateFlow(false)
    private val haEntityCount = MutableStateFlow(0)

    private data class Connections(
        val goveeConnected: Boolean,
        val goveeDeviceCount: Int,
        val tuyaConnected: Boolean,
        val tuyaDeviceCount: Int,
        val tuyaSceneCount: Int,
        val sensiboConnected: Boolean,
        val sensiboDeviceCount: Int,
        val haConnected: Boolean,
        val haEntityCount: Int
    )

    val isPro: StateFlow<Boolean> = services.entitlementRepository.isPro
    val proLicense = services.entitlementRepository.proLicense

    val tapLogs: Flow<List<TapLogEntity>> = services.tapLogDao.getRecentLogs()

    /** Which items are executing, so the surface the finger touched can say so immediately. */
    val runningItems: StateFlow<Set<String>> = services.actionExecutor.running

    private val goveeState = combine(goveeConnected, goveeDeviceCount) { c, d -> c to d }
    private val tuyaState = combine(tuyaConnected, tuyaDeviceCount, tuyaSceneCount) { c, d, s -> Triple(c, d, s) }
    private val sensiboState = combine(sensiboConnected, sensiboDeviceCount) { c, d -> c to d }
    private val haState = combine(haConnected, haEntityCount) { c, d -> c to d }

    private val connections = combine(goveeState, tuyaState, sensiboState, haState) { g, t, s, h ->
        Connections(
            goveeConnected = g.first,
            goveeDeviceCount = g.second,
            tuyaConnected = t.first,
            tuyaDeviceCount = t.second,
            tuyaSceneCount = t.third,
            sensiboConnected = s.first,
            sensiboDeviceCount = s.second,
            haConnected = h.first,
            haEntityCount = h.second
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
            haConnected = c.haConnected,
            haEntityCount = c.haEntityCount,
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

    /** The Quick Controls surface shown inside the app. */
    val quickControls = QuickControlsSession(services, viewModelScope)

    private val _openItemTagId = MutableStateFlow<String?>(null)
    val openItemTagId = _openItemTagId.asStateFlow()

    /** How this app shows an item when a trigger asks for a surface rather than an action. */
    val presenter: ActivationPresenter = object : ActivationPresenter {
        override fun openItem(tagId: String) { _openItemTagId.value = tagId }
        override fun openQuickControls(tagId: String) { quickControls.open(tagId) }
    }

    fun openItem(tagId: String) { presenter.openItem(tagId) }
    fun openQuickControls(tagId: String) {
        services.controllerManager.selectAutomaticLight(tagId)
        presenter.openQuickControls(tagId)
    }
    fun closeItem() { _openItemTagId.value = null }
    fun closeQuickControls() { quickControls.close() }

    private val _nfcReady = MutableStateFlow(true)
    val nfcReady = _nfcReady.asStateFlow()
    fun setNfcReady(ready: Boolean) { _nfcReady.value = ready }

    init {
        refreshConnections()
    }

    /**
     * Reads which providers are set up.
     *
     * Every one of these decrypts a credential through Tink, which reaches the Android Keystore
     * over hardware IPC. On the main thread that measured 53-61ms during launch and was most of
     * TapRelay's cold-start jank, so the whole sweep runs on IO and only the resulting flags come
     * back.
     */
    fun refreshConnections() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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

        val hConn = services.homeAssistantProvider.isConnected()
        if (hConn) {
            runCatching {
                val entities = services.homeAssistantProvider.discoverDevices()
                val scenes = services.homeAssistantProvider.discoverScenes()
                haEntityCount.value = entities.size + scenes.size
            }
        } else {
            haEntityCount.value = 0
        }
        haConnected.value = hConn
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

    /**
     * Connects the owner's own Home Assistant. The address is normalised first, so pasting the
     * URL out of a browser tab - path and all - works the way somebody expects it to.
     */
    fun connectHomeAssistant(baseUrl: String, token: String) = viewModelScope.launch {
        val url = com.arbhlabs.taprelay.data.remote.ha.HomeAssistantApiClient.normalizeBaseUrl(baseUrl)
        if (url.isBlank()) {
            _connectState.value = ConnectState.Error("Enter your Home Assistant address to continue.")
            return@launch
        }
        if (token.isBlank()) {
            _connectState.value = ConnectState.Error("Paste the access token you created in Home Assistant.")
            return@launch
        }
        _connectState.value = ConnectState.Validating
        try {
            if (!services.homeAssistantProvider.testConnection(url, token)) {
                _connectState.value = ConnectState.Error(
                    "Couldn't reach Home Assistant at that address. Check it's running and that " +
                        "your phone is on the same network."
                )
                return@launch
            }
            services.secureKeyStorage.saveHomeAssistantCredentials(
                com.arbhlabs.taprelay.data.secure.HomeAssistantCredentials(url, token.trim())
            )
            val entities = services.homeAssistantProvider.discoverDevices()
            val scenes = services.homeAssistantProvider.discoverScenes()
            haConnected.value = true
            haEntityCount.value = entities.size + scenes.size
            _connectState.value = ConnectState.Success("Home Assistant", entities.size + scenes.size)
        } catch (e: TapException) {
            _connectState.value = ConnectState.Error(e.error.message)
        } catch (e: Exception) {
            _connectState.value = ConnectState.Error("Couldn't reach Home Assistant. Check the address and try again.")
        }
    }

    fun disconnectHomeAssistant() = viewModelScope.launch {
        services.secureKeyStorage.clearHomeAssistantCredentials()
        haConnected.value = false
        haEntityCount.value = 0
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

    fun stepBack() {
        val w = _wizard.value
        if (!w.active) return
        when (w.step) {
            WizardStep.SCAN, WizardStep.DONE -> cancelWizard()
            WizardStep.PICK_DEVICE -> {
                if (w.editingExisting) cancelWizard()
                else _wizard.value = w.copy(step = WizardStep.SCAN, scanPhase = ScanPhase.READY)
            }
            WizardStep.PICK_ACTION -> {
                _wizard.value = w.copy(step = WizardStep.PICK_DEVICE)
            }
            WizardStep.TUNE -> {
                _wizard.value = w.copy(step = WizardStep.PICK_ACTION)
            }
            WizardStep.NAME -> {
                if (w.targetType == TargetType.SCENE) {
                    _wizard.value = w.copy(step = WizardStep.PICK_DEVICE)
                } else if (w.wantsColor || w.wantsBrightness) {
                    _wizard.value = w.copy(step = WizardStep.TUNE)
                } else {
                    _wizard.value = w.copy(step = WizardStep.PICK_ACTION)
                }
            }
        }
    }

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
            offActionType = existing?.offActionType ?: ActionType.TURN_OFF,
            wantsFanSpeed = existing?.fanLevel != null,
            fanLevel = existing?.fanLevel ?: "high",
            activationMode = existing?.activation ?: ActivationMode.EXECUTE
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
            wantsFanSpeed = tag.fanLevel != null,
            fanLevel = tag.fanLevel ?: "high",
            pendingTargetIds = tag.allTargets.map { it.deviceId },
            name = tag.friendlyName,
            iconKey = tag.iconKey,
            timeConditionEnabled = tag.timeConditionEnabled,
            startHour = tag.startHour ?: 8,
            startMinute = tag.startMinute ?: 0,
            endHour = tag.endHour ?: 22,
            endMinute = tag.endMinute ?: 0,
            offActionType = tag.offActionType ?: ActionType.TURN_OFF,
            activationMode = tag.activation
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
        val isClimate = picked.all { it.providerId == SENSIBO_PROVIDER_ID }
        val action = _wizard.value.action
        // Drop an action the new selection cannot all perform.
        val stillValid = when (action) {
            ActionType.RUN_SCENE -> false
            ActionType.TOGGLE_FAN_SPEED -> isClimate
            else -> true
        }
        _wizard.value = _wizard.value.copy(
            device = picked.first(),
            scene = null,
            targetType = TargetType.DEVICE,
            action = if (stillValid) action else ActionType.TOGGLE,
            iconKey = if (isClimate) "air" else if (_wizard.value.iconKey == "air") "lamp" else _wizard.value.iconKey,
            name = _wizard.value.name.ifBlank { defaultNameFor(picked) },
            fanLevels = if (isClimate) _wizard.value.fanLevels else emptyList(),
            step = WizardStep.PICK_ACTION
        )
        if (isClimate) loadClimateCapabilities(picked) else Unit
    }

    /**
     * Asks the picked devices what fan levels they really have, so the wizard never offers a
     * speed the hardware cannot do. A group only offers what all of its devices share.
     */
    private fun loadClimateCapabilities(picked: List<DiscoveredDevice>) = viewModelScope.launch {
        _wizard.value = _wizard.value.copy(readingCapabilities = true)
        val read = runCatching {
            picked.groupBy { it.providerId }
                .flatMap { (providerId, devices) ->
                    services.providers[providerId]
                        ?.getCapabilities(devices.map { it.deviceId to it.sku })
                        .orEmpty()
                }
                .map { it.fanLevels }
                .reduceOrNull { a, b -> a.filter { it in b } } ?: emptyList()
        }.getOrDefault(emptyList())

        val w = _wizard.value
        // A failed read leaves whatever we already knew rather than wiping the options.
        val levels = read.ifEmpty { w.fanLevels }
        val level = if (levels.isEmpty() || levels.any { it.equals(w.fanLevel, true) }) w.fanLevel
        else levels.last()
        _wizard.value = w.copy(
            readingCapabilities = false,
            fanLevels = levels,
            fanLevel = level,
            // A single-speed unit cannot flip between two speeds.
            action = if (w.action == ActionType.TOGGLE_FAN_SPEED && levels.size in 1 until 2)
                ActionType.TOGGLE else w.action
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

    fun setWantsFanSpeed(v: Boolean) { _wizard.value = _wizard.value.copy(wantsFanSpeed = v) }

    fun setFanLevel(v: String) { _wizard.value = _wizard.value.copy(fanLevel = v) }

    fun continueFromAction() {
        val w = _wizard.value
        val picked = w.selectedDevices.ifEmpty { listOfNotNull(w.device) }
        val isClimate = picked.isNotEmpty() && picked.all { it.providerId == SENSIBO_PROVIDER_ID }
        _wizard.value = w.copy(
            step = if (!isClimate && (w.wantsColor || w.wantsBrightness)) WizardStep.TUNE else WizardStep.NAME
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

    fun setActivationMode(mode: ActivationMode) {
        _wizard.value = _wizard.value.copy(activationMode = mode)
    }

    fun setName(v: String) { _wizard.value = _wizard.value.copy(name = v) }
    fun setIcon(v: String) { _wizard.value = _wizard.value.copy(iconKey = v) }

    fun saveTag() = viewModelScope.launch {
        val w = _wizard.value
        val id = w.tagId ?: return@launch
        _wizard.value = w.copy(busy = true)
        val existing = services.tagRepository.getTagById(id)

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
                activationMode = w.activationMode,
                modifiedAt = System.currentTimeMillis()
            )
        } else {
            val picked = w.selectedDevices.ifEmpty { listOfNotNull(w.device) }
            val device = picked.firstOrNull() ?: return@launch
            val isClimate = picked.any { it.providerId == SENSIBO_PROVIDER_ID }
            val finalAction = w.action.powerIntent()
            val finalFanLevel = if (isClimate && (w.action == ActionType.TOGGLE_FAN_SPEED || w.wantsFanSpeed)) w.fanLevel else null
            val extras = picked.drop(1).map {
                TagTarget(
                    providerId = it.providerId,
                    deviceId = it.deviceId,
                    sku = it.sku,
                    name = it.name,
                    fanLevel = if (it.providerId == SENSIBO_PROVIDER_ID && (w.action == ActionType.TOGGLE_FAN_SPEED || w.wantsFanSpeed)) w.fanLevel else null
                )
            }
            (existing ?: TagEntity(
                tagId = id,
                friendlyName = "",
                iconKey = w.iconKey,
                providerId = device.providerId,
                deviceId = device.deviceId,
                deviceSku = device.sku,
                actionType = finalAction,
                targetType = TargetType.DEVICE
            )).copy(
                friendlyName = w.name.ifBlank { device.name },
                iconKey = w.iconKey,
                deviceId = device.deviceId,
                deviceSku = device.sku,
                providerId = device.providerId,
                targetType = TargetType.DEVICE,
                actionType = finalAction,
                brightnessPercent = w.brightnessPercent.takeIf { w.wantsBrightness },
                colorRgb = w.colorRgb.takeIf { w.wantsColor },
                fanLevel = finalFanLevel,
                additionalTargets = extras,
                timeConditionEnabled = w.timeConditionEnabled,
                startHour = if (w.timeConditionEnabled) w.startHour else null,
                startMinute = if (w.timeConditionEnabled) w.startMinute else null,
                endHour = if (w.timeConditionEnabled) w.endHour else null,
                endMinute = if (w.timeConditionEnabled) w.endMinute else null,
                offActionType = if (w.timeConditionEnabled) w.offActionType else null,
                activationMode = w.activationMode,
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
                services.triggerRouter.fire(
                    tagId = tagId,
                    source = TriggerSource.NFC,
                    presenter = presenter
                ) { fb -> _pill.value = fb }
            } else {
                _setupPrompt.value = tagId
            }
        }
    }

    fun dismissSetupPrompt() { _setupPrompt.value = null }
    fun showPill(fb: TapFeedback) { _pill.value = fb }
    fun clearPill() { _pill.value = null }

    // ---- Places (geofenced routines) ----

    val placeTriggers: StateFlow<List<PlaceTriggerEntity>> =
        services.placeTriggerDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _locationPermission = MutableStateFlow(LocationPermission())
    val locationPermission = _locationPermission.asStateFlow()

    data class LocationPermission(
        val foreground: Boolean = false,
        val background: Boolean = false
    )

    fun refreshLocationPermission() {
        _locationPermission.value = LocationPermission(
            foreground = services.geofenceManager.hasForegroundLocation(),
            background = services.geofenceManager.hasBackgroundLocation()
        )
        // Granting "all the time" later is the usual path, so re-register whenever it changes.
        if (_locationPermission.value.background) syncGeofences()
    }

    fun syncGeofences() = viewModelScope.launch {
        val ok = services.geofenceManager.syncAll()
        if (!ok && services.placeTriggerDao.getEnabled().isNotEmpty()) {
            _pill.value = TapFeedback(
                "Places need location set to \"Allow all the time\".",
                isError = true
            )
        }
    }

    private val _pickedLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val pickedLocation = _pickedLocation.asStateFlow()

    private val _locating = MutableStateFlow(false)
    val locating = _locating.asStateFlow()

    fun useCurrentLocation() = viewModelScope.launch {
        _locating.value = true
        val loc = services.geofenceManager.currentLocation()
        _locating.value = false
        if (loc == null) {
            _pill.value = TapFeedback("Couldn't get a location fix.", isError = true)
        } else {
            _pickedLocation.value = loc.latitude to loc.longitude
        }
    }

    fun clearPickedLocation() { _pickedLocation.value = null }

    fun savePlace(
        name: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        transition: PlaceTransition,
        tagId: String,
        leaveTagId: String?
    ) = viewModelScope.launch {
        services.placeTriggerDao.insert(
            PlaceTriggerEntity(
                name = name.ifBlank { "Place" },
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                transition = transition,
                tagId = tagId,
                leaveTagId = leaveTagId
            )
        )
        _pickedLocation.value = null
        services.haptics.vibrateSuccess()
        syncGeofences()
    }

    fun setPlaceEnabled(trigger: PlaceTriggerEntity, enabled: Boolean) = viewModelScope.launch {
        services.placeTriggerDao.update(trigger.copy(enabled = enabled))
        syncGeofences()
    }

    fun deletePlace(trigger: PlaceTriggerEntity) = viewModelScope.launch {
        services.placeTriggerDao.delete(trigger)
        syncGeofences()
    }

    // ---- LastDose logs as TapRelay items ----

    /** What the LastDose picker knows right now. Null means "not looked yet". */
    private val _lastDoseItems = MutableStateFlow<List<com.arbhlabs.taprelay.execution.lastdose.LastDoseItem>?>(null)
    val lastDoseItems: StateFlow<List<com.arbhlabs.taprelay.execution.lastdose.LastDoseItem>?> = _lastDoseItems.asStateFlow()

    /** Null until checked; false when LastDose is missing or predates the logging contract. */
    private val _lastDoseAvailable = MutableStateFlow<Boolean?>(null)
    val lastDoseAvailable: StateFlow<Boolean?> = _lastDoseAvailable.asStateFlow()

    /** Reads across a process boundary, so never on the main thread. */
    fun refreshLastDose() = viewModelScope.launch {
        val (available, items) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val available = services.lastDoseClient.isAvailable()
            available to if (available) services.lastDoseClient.items() else emptyList()
        }
        _lastDoseAvailable.value = available
        _lastDoseItems.value = items
    }

    /**
     * Saves a LastDose log as an ordinary TapRelay item, so every trigger type can already fire it.
     * Editing an existing one keeps its id, and with it every controller mapping and NFC tag
     * already pointing at it.
     */
    fun saveLastDoseItem(
        existing: TagEntity?,
        lastDoseItemId: Long,
        lastDoseItemName: String,
        amount: String,
        unit: String,
        friendlyName: String
    ) = viewModelScope.launch {
        val name = friendlyName.ifBlank { lastDoseItemName.ifBlank { "LastDose log" } }
        val entity = (existing ?: TagEntity(
            tagId = "ld-" + java.util.UUID.randomUUID().toString(),
            friendlyName = name,
            iconKey = "lastdose",
            providerId = "lastdose",
            deviceId = lastDoseItemId.toString(),
            deviceSku = "",
            actionType = ActionType.TURN_ON
        )).copy(
            friendlyName = name,
            targetType = TargetType.LASTDOSE_LOG,
            deviceId = lastDoseItemId.toString(),
            lastDoseItemId = lastDoseItemId,
            lastDoseItemName = lastDoseItemName,
            lastDoseAmount = amount.trim().ifBlank { null },
            lastDoseUnit = unit.trim().ifBlank { null }
        )
        if (existing == null) services.tagRepository.upsert(entity) else services.tagRepository.update(entity)
        services.haptics.vibrateSuccess()
    }

    fun deleteLastDoseItem(tag: TagEntity) = viewModelScope.launch {
        services.tagRepository.delete(tag)
    }

    // ---- Windows PC relay ------------------------------------------------------------------

    val pcRelayCredentials: StateFlow<com.arbhlabs.taprelay.data.secure.PcRelayCredentials?> =
        services.secureKeyStorage.getPcRelayCredentials()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _pcFound = MutableStateFlow<List<com.arbhlabs.taprelay.pc.PcRelayProtocol.DiscoveryResponse>?>(null)
    val pcFound: StateFlow<List<com.arbhlabs.taprelay.pc.PcRelayProtocol.DiscoveryResponse>?> = _pcFound.asStateFlow()
    private val _pcBusy = MutableStateFlow(false)
    val pcBusy: StateFlow<Boolean> = _pcBusy.asStateFlow()
    private val _pcMessage = MutableStateFlow<String?>(null)
    val pcMessage: StateFlow<String?> = _pcMessage.asStateFlow()

    fun findPcs() = viewModelScope.launch {
        _pcBusy.value = true
        _pcMessage.value = null
        val found = runCatching { com.arbhlabs.taprelay.pc.PcRelayDiscovery(timeoutMs = 1_500).discover() }
            .getOrDefault(emptyList())
        _pcFound.value = found
        if (found.isEmpty()) {
            _pcMessage.value = "No PC found. Make sure TapRelay PC Relay is running on it and both are on the same Wi-Fi."
        }
        _pcBusy.value = false
    }

    fun pairPc(pc: com.arbhlabs.taprelay.pc.PcRelayProtocol.DiscoveryResponse, code: String) = viewModelScope.launch {
        _pcBusy.value = true
        val baseUrl = "http://${pc.host}:${pc.port}"
        val ownerId = java.util.UUID.randomUUID().toString()
        com.arbhlabs.taprelay.pc.PcRelayClient(services.pcRelayHttp, baseUrl, token = "", ownerId = ownerId)
            .pair(code)
            .onSuccess {
                services.secureKeyStorage.savePcRelayCredentials(
                    com.arbhlabs.taprelay.data.secure.PcRelayCredentials(baseUrl, it.token, ownerId)
                )
                _pcFound.value = null
                _pcMessage.value = "Paired with ${pc.name}."
                services.haptics.vibrateSuccess()
            }
            .onFailure { _pcMessage.value = "Pairing failed. Check the code shown on the PC and try again." }
        _pcBusy.value = false
    }

    fun unpairPc() = viewModelScope.launch {
        services.secureKeyStorage.clearPcRelayCredentials()
        _pcMessage.value = null
    }

    /** A PC action is an ordinary item: the action id in deviceId, its value (shortcut, URL) in deviceSku. */
    fun savePcItem(existing: TagEntity?, action: String, value: String, name: String) = viewModelScope.launch {
        val label = com.arbhlabs.taprelay.pc.PcRelayAction.label(action)
        val entity = (existing ?: newItem(prefix = "pc", name = name, iconKey = "pc")).copy(
            friendlyName = name.ifBlank { label },
            targetType = TargetType.PC_RELAY,
            providerId = "windows",
            deviceId = action,
            deviceSku = value.trim()
        )
        persist(existing, entity)
    }

    // ---- Magic Actions and the other non-device items -------------------------------------

    /**
     * Every item a Magic Action step can point at.
     *
     * Deliberately not filtered by kind beyond excluding Magic Actions themselves: a step can run
     * a lamp, a scene, a Home Assistant entity, a LastDose log, a web request, an app or Do Not
     * Disturb, because all of those are the same thing to the executor.
     */
    val stepCandidates: StateFlow<List<TagEntity>> = ui
        .map { state -> state.tags.filterNot { it.isMagicAction } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveMagicAction(
        existing: TagEntity?,
        name: String,
        iconKey: String,
        steps: List<com.arbhlabs.taprelay.domain.model.ActionStep>,
        activationMode: ActivationMode = ActivationMode.EXECUTE
    ) = viewModelScope.launch {
        val entity = (existing ?: newItem(prefix = "magic", name = name, iconKey = iconKey)).copy(
            friendlyName = name.ifBlank { "Magic Action" },
            iconKey = iconKey,
            targetType = TargetType.MAGIC_ACTION,
            actionChainJson = com.arbhlabs.taprelay.domain.model.ActionStep.encode(steps),
            activationMode = activationMode
        )
        persist(existing, entity)
    }

    /**
     * Saves a web request. The credential never enters the tags row: only the header's *name* is
     * stored there, and the value goes into encrypted storage keyed by this item.
     */
    fun saveWebhookItem(
        existing: TagEntity?,
        name: String,
        iconKey: String,
        url: String,
        method: com.arbhlabs.taprelay.execution.webhook.WebhookMethod,
        body: String,
        secretHeader: String,
        secretValue: String
    ) = viewModelScope.launch {
        val trimmedHeader = secretHeader.trim()
        val hasSecret = trimmedHeader.isNotBlank() && secretValue.isNotBlank()
        val entity = (existing ?: newItem(prefix = "web", name = name, iconKey = iconKey)).copy(
            friendlyName = name.ifBlank { "Web request" },
            iconKey = iconKey,
            targetType = TargetType.WEBHOOK,
            webhookUrl = url.trim(),
            webhookMethod = method.name,
            webhookBody = body.trim().ifBlank { null },
            webhookSecretHeader = trimmedHeader.ifBlank { null }
        )
        persist(existing, entity)
        if (hasSecret) {
            services.secureKeyStorage.saveWebhookSecret(entity.tagId, secretValue.trim())
        } else if (trimmedHeader.isBlank()) {
            services.secureKeyStorage.clearWebhookSecret(entity.tagId)
        }
    }

    fun saveLaunchItem(
        existing: TagEntity?,
        name: String,
        iconKey: String,
        kind: String,
        target: String
    ) = viewModelScope.launch {
        val entity = (existing ?: newItem(prefix = "open", name = name, iconKey = iconKey)).copy(
            friendlyName = name.ifBlank { "Open" },
            iconKey = iconKey,
            targetType = TargetType.LAUNCH,
            deviceId = target.trim(),
            deviceSku = kind
        )
        persist(existing, entity)
    }

    fun savePhoneItem(
        existing: TagEntity?,
        name: String,
        iconKey: String,
        phoneAction: String
    ) = viewModelScope.launch {
        val entity = (existing ?: newItem(prefix = "phone", name = name, iconKey = iconKey)).copy(
            friendlyName = name.ifBlank { com.arbhlabs.taprelay.domain.model.PhoneAction.label(phoneAction) },
            iconKey = iconKey,
            targetType = TargetType.PHONE,
            deviceId = phoneAction
        )
        persist(existing, entity)
    }

    /** Deletes any item, and the encrypted credential that belonged to it. */
    fun deleteItem(tag: TagEntity) = viewModelScope.launch {
        services.tagRepository.delete(tag)
        if (tag.isWebhook) services.secureKeyStorage.clearWebhookSecret(tag.tagId)
        // A deleted item leaves any Magic Action that pointed at it: the step reports it by name
        // rather than the sequence silently getting shorter.
        pruneAodFavourites(tag.tagId)
    }

    private fun newItem(prefix: String, name: String, iconKey: String) = TagEntity(
        tagId = "$prefix-" + java.util.UUID.randomUUID().toString(),
        friendlyName = name,
        iconKey = iconKey,
        providerId = prefix,
        deviceId = "",
        deviceSku = "",
        actionType = ActionType.TURN_ON
    )

    private suspend fun persist(existing: TagEntity?, entity: TagEntity) {
        if (existing == null) services.tagRepository.upsert(entity) else services.tagRepository.update(entity)
        services.haptics.vibrateSuccess()
    }

    /** Reads the stored credential so the editor can show that one exists without revealing it. */
    suspend fun hasWebhookSecret(tagId: String): Boolean =
        services.secureKeyStorage.getWebhookSecret(tagId).first() != null

    // ---- The always-on face's favourites ---------------------------------------------------

    val aodFavourites: StateFlow<List<String>> = services.preferences.aodFavourites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val aodShowClock: StateFlow<Boolean> = services.preferences.aodShowClock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val aodConfirmActions: StateFlow<Boolean> = services.preferences.aodConfirmActions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val aodMonochrome: StateFlow<Boolean> = services.preferences.aodMonochrome
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAodFavourites(ids: List<String>) = viewModelScope.launch {
        services.preferences.setAodFavourites(ids)
    }

    fun toggleAodFavourite(tagId: String) = viewModelScope.launch {
        val current = services.preferences.aodFavourites.first()
        val next = if (current.contains(tagId)) current - tagId else current + tagId
        services.preferences.setAodFavourites(next)
    }

    fun moveAodFavourite(tagId: String, delta: Int) = viewModelScope.launch {
        val current = services.preferences.aodFavourites.first().toMutableList()
        val index = current.indexOf(tagId)
        if (index < 0) return@launch
        val target = (index + delta).coerceIn(0, current.size - 1)
        if (target == index) return@launch
        current.removeAt(index)
        current.add(target, tagId)
        services.preferences.setAodFavourites(current)
    }

    private suspend fun pruneAodFavourites(removedTagId: String) {
        val current = services.preferences.aodFavourites.first()
        if (current.contains(removedTagId)) {
            services.preferences.setAodFavourites(current - removedTagId)
        }
    }

    fun setAodShowClock(value: Boolean) = viewModelScope.launch {
        services.preferences.setAodShowClock(value)
    }

    fun setAodConfirmActions(value: Boolean) = viewModelScope.launch {
        services.preferences.setAodConfirmActions(value)
    }

    fun setAodMonochrome(value: Boolean) = viewModelScope.launch {
        services.preferences.setAodMonochrome(value)
    }

    // ---- Phone-side capabilities -----------------------------------------------------------

    val installedApps: StateFlow<List<com.arbhlabs.taprelay.execution.phone.LaunchableApp>?> =
        MutableStateFlow<List<com.arbhlabs.taprelay.execution.phone.LaunchableApp>?>(null)
            .also { flow ->
                viewModelScope.launch {
                    // Reads the package manager, which is slow enough to matter on a cold open.
                    flow.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        services.appLauncher.launchableApps()
                    }
                }
            }.asStateFlow()

    fun hasDndAccess(): Boolean = services.phoneController.hasDndAccess

    fun dndAccessIntent() = services.phoneController.dndAccessIntent()

    /** Fires an item from the app so a mapping can be proved without a controller or a tag. */
    fun testItem(tag: TagEntity) {
        services.triggerRouter.fire(
            tagId = tag.tagId,
            override = ActivationMode.EXECUTE,
            source = TriggerSource.IN_APP
        ) { fb -> showPill(fb) }
    }

    // ---- Game Controller Remote Integration ----
    val connectedControllers: StateFlow<List<ControllerDevice>> =
        services.controllerManager.connectedControllers

    val automaticLightControls = services.controllerManager.automaticLightControlState

    val isControllerLearning: StateFlow<Boolean> =
        services.controllerManager.isLearningMode

    val capturedControllerInput: StateFlow<ControllerInput?> =
        services.controllerManager.capturedInput

    val controllerMappings: StateFlow<List<ControllerMappingEntity>> =
        services.controllerMappingDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Feedback & Remote Mode preferences ----
    val controllerRumble: StateFlow<Boolean> = services.preferences.controllerRumble
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val phoneHaptics: StateFlow<Boolean> = services.preferences.phoneHaptics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val automaticHapticSignatures: StateFlow<Boolean> = services.preferences.automaticHapticSignatures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val remoteAutoDim: StateFlow<Boolean> = services.preferences.remoteAutoDim
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val aodDensity: StateFlow<AodDensity> = services.preferences.aodDensity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AodDensity.NORMAL)

    fun setAodDensity(value: AodDensity) = viewModelScope.launch {
        services.preferences.setAodDensity(value)
    }

    fun setControllerRumble(value: Boolean) = viewModelScope.launch {
        services.preferences.setControllerRumble(value)
        if (value) services.haptics.vibrateTick()
    }

    fun setPhoneHaptics(value: Boolean) = viewModelScope.launch {
        services.preferences.setPhoneHaptics(value)
    }

    fun setAutomaticHapticSignatures(value: Boolean) = viewModelScope.launch {
        services.preferences.setAutomaticHapticSignatures(value)
    }

    fun resetHapticSignatures() {
        services.hapticSignatures.resetAssignments()
        services.haptics.vibrateTick()
    }

    fun setRemoteAutoDim(value: Boolean) = viewModelScope.launch {
        services.preferences.setRemoteAutoDim(value)
    }

    private val _keepScreenAwake = MutableStateFlow(false)
    val keepScreenAwake: StateFlow<Boolean> = _keepScreenAwake.asStateFlow()

    fun setKeepScreenAwake(keep: Boolean) {
        _keepScreenAwake.value = keep
    }

    fun startControllerLearning() {
        services.controllerManager.startLearning()
    }

    fun stopControllerLearning() {
        services.controllerManager.stopLearning()
    }

    fun refreshControllers() {
        services.controllerManager.refreshConnectedControllers()
    }

    fun saveControllerMapping(
        controllerDescriptor: String,
        controllerName: String,
        inputKey: String,
        inputLabel: String,
        tagId: String,
        activationMode: ActivationMode? = null
    ) = viewModelScope.launch {
        services.controllerMappingDao.insert(
            ControllerMappingEntity(
                controllerDescriptor = controllerDescriptor,
                controllerName = controllerName,
                inputKey = inputKey,
                inputLabel = inputLabel,
                tagId = tagId,
                activationMode = activationMode
            )
        )
        services.haptics.vibrateSuccess()
        _pill.value = TapFeedback("Mapping saved for $inputLabel", isError = false)
    }

    fun deleteControllerMapping(id: Long) = viewModelScope.launch {
        services.controllerMappingDao.deleteById(id)
        services.haptics.vibrateClick()
    }

    fun setControllerMappingActivationMode(
        mapping: ControllerMappingEntity,
        mode: ActivationMode?
    ) = viewModelScope.launch {
        services.controllerMappingDao.update(mapping.copy(activationMode = mode))
        services.haptics.vibrateClick()
    }

    fun toggleControllerMapping(mapping: ControllerMappingEntity) = viewModelScope.launch {
        services.controllerMappingDao.update(mapping.copy(enabled = !mapping.enabled))
    }

    fun testControllerMapping(tagId: String) {
        services.controllerManager.selectAutomaticLight(tagId)
        services.actionExecutor.executeByTagId(tagId) { fb -> _pill.value = fb }
    }
}

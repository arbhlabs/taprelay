package com.arbhlabs.taprelay.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arbhlabs.taprelay.TapRelayApplication
import com.arbhlabs.taprelay.data.local.entity.TagEntity
import com.arbhlabs.taprelay.domain.model.ActionType
import com.arbhlabs.taprelay.domain.model.DiscoveredDevice
import com.arbhlabs.taprelay.domain.model.TapException
import com.arbhlabs.taprelay.domain.provider.GOVEE_PROVIDER_ID
import com.arbhlabs.taprelay.execution.TapFeedback
import com.arbhlabs.taprelay.nfc.NfcPayloadParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WizardStep { SCAN, PICK_DEVICE, PICK_ACTION, NAME, DONE }

data class WizardState(
    val active: Boolean = false,
    val step: WizardStep = WizardStep.SCAN,
    val editingExisting: Boolean = false,
    val tagId: String? = null,
    val discovering: Boolean = false,
    val devices: List<DiscoveredDevice> = emptyList(),
    val device: DiscoveredDevice? = null,
    val action: ActionType = ActionType.TOGGLE,
    val name: String = "",
    val iconKey: String = "lamp",
    val error: String? = null,
    val busy: Boolean = false
)

data class HomeUiState(
    val loading: Boolean = true,
    val onboardingComplete: Boolean = false,
    val goveeConnected: Boolean = false,
    val tags: List<TagEntity> = emptyList()
)

class TapRelayViewModel(app: Application) : AndroidViewModel(app) {

    private val services = (app as TapRelayApplication).services

    private val goveeConnected = MutableStateFlow(false)

    val ui: StateFlow<HomeUiState> = combine(
        services.preferences.onboardingComplete,
        services.tagRepository.getAllTags(),
        goveeConnected
    ) { onboarded, tags, govee ->
        HomeUiState(false, onboarded, govee, tags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private val _wizard = MutableStateFlow(WizardState())
    val wizard = _wizard.asStateFlow()

    private val _pill = MutableStateFlow<TapFeedback?>(null)
    val pill = _pill.asStateFlow()

    private val _setupPrompt = MutableStateFlow<String?>(null)
    val setupPrompt = _setupPrompt.asStateFlow()

    init { refreshGoveeConnected() }

    fun refreshGoveeConnected() = viewModelScope.launch {
        goveeConnected.value = services.goveeProvider.isConnected()
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
        data class Success(val deviceCount: Int) : ConnectState
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
            _connectState.value = ConnectState.Success(devices.size)
        } catch (e: TapException) {
            _connectState.value = ConnectState.Error(e.error.message)
        } catch (e: Exception) {
            _connectState.value = ConnectState.Error("Couldn't reach Govee. Check your connection.")
        }
    }

    fun resetConnectState() { _connectState.value = ConnectState.Idle }

    fun disconnectGovee() = viewModelScope.launch {
        services.secureKeyStorage.clearGoveeApiKey()
        goveeConnected.value = false
    }

    // ---- Add-tag wizard ----

    fun startWizard() {
        _wizard.value = WizardState(active = true, step = WizardStep.SCAN)
    }

    fun cancelWizard() { _wizard.value = WizardState() }

    /** Called by the Activity once a blank tag has been written with [tagId]. */
    fun onTagWritten(tagId: String) {
        _wizard.value = _wizard.value.copy(tagId = tagId, step = WizardStep.PICK_DEVICE)
        discoverDevices()
    }

    fun onWizardWriteError(message: String) {
        _wizard.value = _wizard.value.copy(error = message)
    }

    fun editTag(tag: TagEntity) {
        _wizard.value = WizardState(
            active = true,
            step = WizardStep.PICK_DEVICE,
            editingExisting = true,
            tagId = tag.tagId,
            action = tag.actionType,
            name = tag.friendlyName,
            iconKey = tag.iconKey
        )
        discoverDevices()
    }

    fun configureUnregisteredTag(tagId: String) {
        _setupPrompt.value = null
        _wizard.value = WizardState(
            active = true, step = WizardStep.PICK_DEVICE, editingExisting = true, tagId = tagId
        )
        discoverDevices()
    }

    private fun discoverDevices() = viewModelScope.launch {
        _wizard.value = _wizard.value.copy(discovering = true, error = null)
        try {
            val devices = services.goveeProvider.discoverDevices()
            _wizard.value = _wizard.value.copy(discovering = false, devices = devices)
        } catch (e: TapException) {
            _wizard.value = _wizard.value.copy(discovering = false, error = e.error.message)
        } catch (e: Exception) {
            _wizard.value = _wizard.value.copy(discovering = false, error = "Couldn't load your devices.")
        }
    }

    fun retryDiscovery() = discoverDevices()

    fun selectDevice(d: DiscoveredDevice) {
        _wizard.value = _wizard.value.copy(
            device = d,
            name = _wizard.value.name.ifBlank { d.name },
            step = WizardStep.PICK_ACTION
        )
    }

    fun selectAction(a: ActionType) {
        _wizard.value = _wizard.value.copy(action = a, step = WizardStep.NAME)
    }

    fun setName(v: String) { _wizard.value = _wizard.value.copy(name = v) }
    fun setIcon(v: String) { _wizard.value = _wizard.value.copy(iconKey = v) }

    fun saveTag() = viewModelScope.launch {
        val w = _wizard.value
        val id = w.tagId ?: return@launch
        val device = w.device ?: return@launch
        _wizard.value = w.copy(busy = true)
        val existing = services.tagRepository.getTagById(id)
        val entity = (existing ?: TagEntity(
            tagId = id,
            friendlyName = "",
            iconKey = w.iconKey,
            providerId = GOVEE_PROVIDER_ID,
            deviceId = device.deviceId,
            deviceSku = device.sku,
            actionType = w.action
        )).copy(
            friendlyName = w.name.ifBlank { device.name },
            iconKey = w.iconKey,
            deviceId = device.deviceId,
            deviceSku = device.sku,
            providerId = device.providerId,
            actionType = w.action,
            modifiedAt = System.currentTimeMillis()
        )
        services.tagRepository.upsert(entity)
        _wizard.value = _wizard.value.copy(busy = false, step = WizardStep.DONE)
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

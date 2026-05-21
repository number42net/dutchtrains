package net.number42.dutchtrains.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.number42.dutchtrains.data.datastore.AppPreferences
import net.number42.dutchtrains.data.repository.StationRepository
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val displayWindowMinutes: Int = 120,
    val notifyPlatformChanges: Boolean = true,
    val notifyDepartureTime: Boolean = true,
    val notifyArrivalTime: Boolean = true,
    val notifyPlatformArrivalChanges: Boolean = false,
    val notifyMaterialChanges: Boolean = true,
    val isSaved: Boolean = false,
    val isValidating: Boolean = false,
    val validationResult: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val stationRepository: StationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val key = appPreferences.apiKeyFlow.first()
            _uiState.update {
                it.copy(
                    apiKey = key,
                    displayWindowMinutes = appPreferences.displayWindowMinutesFlow.first(),
                    notifyPlatformChanges = appPreferences.notifyPlatformChangesFlow.first(),
                    notifyDepartureTime = appPreferences.notifyDepartureTimeFlow.first(),
                    notifyArrivalTime = appPreferences.notifyArrivalTimeFlow.first(),
                    notifyPlatformArrivalChanges = appPreferences.notifyPlatformArrivalChangesFlow.first(),
                    notifyMaterialChanges = appPreferences.notifyMaterialChangesFlow.first(),
                )
            }
        }
    }

    fun onApiKeyChange(key: String) {
        _uiState.update { it.copy(apiKey = key, isSaved = false, validationResult = null) }
    }

    fun onSave() {
        viewModelScope.launch {
            appPreferences.saveApiKey(_uiState.value.apiKey.trim())
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun onTestConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isValidating = true, validationResult = null) }
            // Save key first so interceptor picks it up, then test it
            appPreferences.saveApiKey(_uiState.value.apiKey.trim())
            val results = stationRepository.searchStations("Amsterdam")
            val result = if (results.isNotEmpty()) "Connected — ${results.size} stations found"
                         else "No results — check your API key"
            _uiState.update { it.copy(isValidating = false, validationResult = result) }
        }
    }

    fun onNotifyPlatformChanges(enabled: Boolean) {
        _uiState.update { it.copy(notifyPlatformChanges = enabled, isSaved = false) }
        viewModelScope.launch { appPreferences.saveNotifyPlatformChanges(enabled) }
    }

    fun onNotifyDepartureTime(enabled: Boolean) {
        _uiState.update { it.copy(notifyDepartureTime = enabled, isSaved = false) }
        viewModelScope.launch { appPreferences.saveNotifyDepartureTime(enabled) }
    }

    fun onNotifyArrivalTime(enabled: Boolean) {
        _uiState.update { it.copy(notifyArrivalTime = enabled, isSaved = false) }
        viewModelScope.launch { appPreferences.saveNotifyArrivalTime(enabled) }
    }

    fun onNotifyPlatformArrivalChanges(enabled: Boolean) {
        _uiState.update { it.copy(notifyPlatformArrivalChanges = enabled, isSaved = false) }
        viewModelScope.launch { appPreferences.saveNotifyPlatformArrivalChanges(enabled) }
    }

    fun onNotifyMaterialChanges(enabled: Boolean) {
        _uiState.update { it.copy(notifyMaterialChanges = enabled, isSaved = false) }
        viewModelScope.launch { appPreferences.saveNotifyMaterialChanges(enabled) }
    }

    fun onDisplayWindowMinutesChange(minutes: Int) {
        _uiState.update { it.copy(displayWindowMinutes = minutes, isSaved = false) }
        viewModelScope.launch { appPreferences.saveDisplayWindowMinutes(minutes) }
    }
}

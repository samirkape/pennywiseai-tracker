package com.spendly.tracker.ui.screens.connector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.connector.data.preferences.ConnectorPreferencesRepository
import com.spendly.tracker.connector.data.security.SecureKeyStore
import com.spendly.tracker.connector.domain.AiProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Backs the AI Connector configuration screen (provider, model, API key).
 */
@HiltViewModel
class ConnectorSettingsViewModel @Inject constructor(
    private val preferencesRepository: ConnectorPreferencesRepository,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {

    val provider: StateFlow<AiProvider> = preferencesRepository.provider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiProvider.OPENAI)

    val model: StateFlow<String> = preferencesRepository.model
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiProvider.OPENAI.defaultModel)

    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val _apiKeyInput = MutableStateFlow("")
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshApiKeyState()
    }

    fun refreshApiKeyState() {
        viewModelScope.launch {
            _hasApiKey.value = withContext(Dispatchers.IO) { secureKeyStore.hasApiKey() }
        }
    }

    fun onApiKeyChange(value: String) {
        _apiKeyInput.value = value
    }

    fun setProvider(newProvider: AiProvider) {
        viewModelScope.launch {
            preferencesRepository.setProvider(newProvider)
            _message.value = "Switched to ${newProvider.displayName}"
        }
    }

    fun setModel(newModel: String) {
        viewModelScope.launch {
            preferencesRepository.setModel(newModel)
        }
    }

    fun saveApiKey() {
        val key = _apiKeyInput.value.trim()
        if (key.isEmpty()) {
            _message.value = "Enter an API key first"
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { secureKeyStore.saveApiKey(key) }
            }.onSuccess {
                _apiKeyInput.value = ""
                _hasApiKey.value = true
                _message.value = "API key saved"
            }.onFailure {
                _message.value = "Could not save the API key"
            }
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { secureKeyStore.clearApiKey() }
            _apiKeyInput.value = ""
            _hasApiKey.value = false
            _message.value = "API key removed"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

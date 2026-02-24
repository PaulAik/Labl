package com.paulaik.labl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paulaik.labl.api.ClaudeApiClient
import com.paulaik.labl.api.LabelApiClient
import com.paulaik.labl.data.ApiKeyStore
import com.paulaik.labl.data.model.AnalysisResult
import com.paulaik.labl.data.model.SymbolLabel
import com.paulaik.labl.ml.SymbolAnalyzer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyStore = ApiKeyStore(application)
    private val apiClient = ClaudeApiClient()
    private val labelApiClient = LabelApiClient()
    private var analyzer: SymbolAnalyzer? = null

    /** Validated examples from the backend, refreshed every 5 minutes. */
    private var cachedExamples: List<SymbolLabel> = emptyList()

    init {
        // Kick off example cache refresh in the background
        viewModelScope.launch {
            while (isActive) {
                val url = backendUrl.value
                if (url.isNotBlank()) {
                    cachedExamples = labelApiClient.fetchExamples(url)
                }
                delay(5 * 60 * 1_000L) // refresh every 5 minutes
            }
        }
    }

    val apiKey: StateFlow<String> = apiKeyStore.apiKey.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ""
    )

    val backendUrl: StateFlow<String> = apiKeyStore.backendUrl.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "http://10.0.2.2:8080"
    )

    private val _analysisResult = MutableStateFlow<AnalysisResult?>(null)
    val analysisResult: StateFlow<AnalysisResult?> = _analysisResult.asStateFlow()

    private val _isAnalysing = MutableStateFlow(false)
    val isAnalysing: StateFlow<Boolean> = _isAnalysing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Call once to get a configured analyzer to hand to CameraX. */
    fun buildAnalyzer(): SymbolAnalyzer = SymbolAnalyzer(
        scope = viewModelScope,
        apiClient = apiClient,
        apiKey = { apiKey.value },
        examples = { cachedExamples },
        onAnalysing = {
            _isAnalysing.value = true
            _errorMessage.value = null
        },
        onResult = { result ->
            _isAnalysing.value = false
            _analysisResult.value = result
        },
        onError = { msg ->
            _isAnalysing.value = false
            _errorMessage.value = msg
            viewModelScope.launch {
                delay(8_000)
                _errorMessage.compareAndSet(msg, null)
            }
        }
    ).also { analyzer = it }

    /** Force the next camera frame to be analysed immediately. */
    fun triggerScan() {
        analyzer?.triggerNow()
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch { apiKeyStore.save(key) }
    }

    fun saveBackendUrl(url: String) {
        viewModelScope.launch { apiKeyStore.saveBackendUrl(url) }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearResults() {
        _analysisResult.value = null
    }
}

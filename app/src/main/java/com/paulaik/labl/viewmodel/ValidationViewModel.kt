package com.paulaik.labl.viewmodel

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paulaik.labl.api.ClaudeApiClient
import com.paulaik.labl.api.LabelApiClient
import com.paulaik.labl.data.ApiKeyStore
import com.paulaik.labl.data.model.AnalysisResult
import com.paulaik.labl.data.model.SymbolLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ValidationState {
    object Loading : ValidationState()
    object Empty : ValidationState()
    data class Ready(val queue: List<SymbolLabel>, val currentIndex: Int) : ValidationState() {
        val current get() = queue[currentIndex]
        val remaining get() = queue.size - currentIndex
    }
    data class Error(val message: String) : ValidationState()
}

/** Result of running a crop image through the live AR pipeline. */
sealed class ArTestResult {
    object Idle : ArTestResult()
    object Loading : ArTestResult()
    data class Done(
        val labelName: String,
        val arResult: AnalysisResult
    ) : ArTestResult()
    data class Error(val message: String) : ArTestResult()
}

class ValidationViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKeyStore = ApiKeyStore(application)
    private val labelApiClient = LabelApiClient()
    private val claudeApiClient = ClaudeApiClient()

    private val _state = MutableStateFlow<ValidationState>(ValidationState.Loading)
    val state: StateFlow<ValidationState> = _state.asStateFlow()

    private val _arTestResult = MutableStateFlow<ArTestResult>(ArTestResult.Idle)
    val arTestResult: StateFlow<ArTestResult> = _arTestResult.asStateFlow()

    private var backendUrl: String = "http://10.0.2.2:8080"

    /** The Claude API key read from DataStore (needed for the AR test). */
    private val apiKey: StateFlow<String> = apiKeyStore.apiKey.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ""
    )

    fun setBackendUrl(url: String) {
        backendUrl = url.trim().ifBlank { "http://10.0.2.2:8080" }
    }

    // ── Queue management ──────────────────────────────────────────────────

    fun load() {
        _state.value = ValidationState.Loading
        viewModelScope.launch {
            labelApiClient.fetchPendingLabels(backendUrl)
                .onSuccess { labels ->
                    _state.value = if (labels.isEmpty()) ValidationState.Empty
                    else ValidationState.Ready(labels, 0)
                }
                .onFailure { e ->
                    _state.value = ValidationState.Error(e.message ?: "Failed to load")
                }
        }
    }

    fun decide(approved: Boolean) {
        val ready = _state.value as? ValidationState.Ready ?: return
        val label = ready.current

        val nextIndex = ready.currentIndex + 1
        _state.value = if (nextIndex >= ready.queue.size) ValidationState.Empty
        else ready.copy(currentIndex = nextIndex)

        viewModelScope.launch {
            labelApiClient.submitValidation(backendUrl, label.id, approved)
                .onFailure { e ->
                    _state.value = ValidationState.Error("Submit failed: ${e.message}")
                }
        }
    }

    // ── AR test ───────────────────────────────────────────────────────────

    /**
     * Fetches the crop image for [label] via the backend proxy, then runs it
     * through the live Claude AR pipeline so you can see if the model agrees
     * with the validated label.
     */
    fun testCropInAR(label: SymbolLabel) {
        val key = label.cropS3Key ?: label.s3Key
        _arTestResult.value = ArTestResult.Loading
        viewModelScope.launch {
            labelApiClient.fetchImageBytes(backendUrl, key)
                .onSuccess { bytes ->
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    claudeApiClient.analyzeFrame(b64, apiKey.value)
                        .onSuccess { result ->
                            _arTestResult.value = ArTestResult.Done(label.name, result)
                        }
                        .onFailure { e ->
                            _arTestResult.value = ArTestResult.Error(e.message ?: "Claude error")
                        }
                }
                .onFailure { e ->
                    _arTestResult.value = ArTestResult.Error("Image fetch failed: ${e.message}")
                }
        }
    }

    fun dismissArTest() {
        _arTestResult.value = ArTestResult.Idle
    }
}

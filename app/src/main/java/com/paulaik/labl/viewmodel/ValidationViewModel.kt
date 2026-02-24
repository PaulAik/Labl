package com.paulaik.labl.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paulaik.labl.api.LabelApiClient
import com.paulaik.labl.data.model.SymbolLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class ValidationViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = LabelApiClient()

    private val _state = MutableStateFlow<ValidationState>(ValidationState.Loading)
    val state: StateFlow<ValidationState> = _state.asStateFlow()

    private var backendUrl: String = "http://10.0.2.2:8080"

    fun setBackendUrl(url: String) {
        backendUrl = url.trim().ifBlank { "http://10.0.2.2:8080" }
    }

    fun load() {
        _state.value = ValidationState.Loading
        viewModelScope.launch {
            apiClient.fetchPendingLabels(backendUrl)
                .onSuccess { labels ->
                    _state.value = if (labels.isEmpty()) {
                        ValidationState.Empty
                    } else {
                        ValidationState.Ready(labels, 0)
                    }
                }
                .onFailure { e ->
                    _state.value = ValidationState.Error(e.message ?: "Failed to load")
                }
        }
    }

    /** Submit a validation decision and advance to the next label. */
    fun decide(approved: Boolean) {
        val ready = _state.value as? ValidationState.Ready ?: return
        val label = ready.current

        // Optimistically advance the queue immediately
        val nextIndex = ready.currentIndex + 1
        _state.value = if (nextIndex >= ready.queue.size) {
            ValidationState.Empty
        } else {
            ready.copy(currentIndex = nextIndex)
        }

        // Fire and forget – network call in background
        viewModelScope.launch {
            apiClient.submitValidation(backendUrl, label.id, approved)
                .onFailure { e ->
                    // Surface the error but don't block the user
                    _state.value = ValidationState.Error("Submit failed: ${e.message}")
                }
        }
    }
}

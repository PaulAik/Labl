package com.paulaik.labl.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paulaik.labl.api.TrainingApiClient
import com.paulaik.labl.data.model.ApplianceType
import com.paulaik.labl.data.model.ShotType
import com.paulaik.labl.data.model.TrainingSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

sealed class TrainingUiState {
    /** Nothing in progress – show the appliance picker. */
    object AppliancePicker : TrainingUiState()

    /** User is framing a shot. */
    data class ReadyToCapture(val session: TrainingSession, val shot: ShotType) : TrainingUiState()

    /** Image is being uploaded to the backend. */
    data class Uploading(val session: TrainingSession, val shot: ShotType) : TrainingUiState()

    /** All shots collected successfully. */
    data class Complete(val session: TrainingSession) : TrainingUiState()

    /** Something went wrong; the user can retry or abort. */
    data class Error(val message: String, val session: TrainingSession, val shot: ShotType) :
        TrainingUiState()
}

class TrainingViewModel(application: Application) : AndroidViewModel(application) {

    private val apiClient = TrainingApiClient()

    /** Set by TrainingScreen once the CameraX ImageCapture use-case is bound. */
    var imageCapture: ImageCapture? = null

    private val _uiState = MutableStateFlow<TrainingUiState>(TrainingUiState.AppliancePicker)
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    /** Backend URL, injected from the persisted DataStore value via TrainingScreen. */
    private var backendUrl: String = "http://10.0.2.2:8080"

    fun setBackendUrl(url: String) {
        backendUrl = url.trim().ifBlank { "http://10.0.2.2:8080" }
    }

    // ── Session lifecycle ──────────────────────────────────────────────────

    fun startSession(applianceType: ApplianceType) {
        val session = TrainingSession(applianceType = applianceType)
        val first = session.nextShot ?: return
        _uiState.value = TrainingUiState.ReadyToCapture(session, first)
    }

    fun reset() {
        imageCapture = null
        _uiState.value = TrainingUiState.AppliancePicker
    }

    // ── Capture ───────────────────────────────────────────────────────────

    /**
     * Trigger an in-memory capture on [executor]. The result is handed to
     * [processCapture] which re-encodes to JPEG and uploads to the backend.
     */
    fun captureShot(executor: Executor) {
        val state = _uiState.value as? TrainingUiState.ReadyToCapture ?: return
        val ic = imageCapture ?: run {
            _uiState.value = TrainingUiState.Error(
                "Camera not ready", state.session, state.shot
            )
            return
        }

        ic.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bytes = image.toJpegBytes()
                image.close()
                if (bytes != null) {
                    uploadShot(state.session, state.shot, bytes)
                } else {
                    _uiState.value = TrainingUiState.Error(
                        "Image conversion failed", state.session, state.shot
                    )
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture error", exception)
                _uiState.value = TrainingUiState.Error(
                    exception.message ?: "Capture failed", state.session, state.shot
                )
            }
        })
    }

    // ── Upload ────────────────────────────────────────────────────────────

    private fun uploadShot(session: TrainingSession, shot: ShotType, bytes: ByteArray) {
        _uiState.value = TrainingUiState.Uploading(session, shot)
        viewModelScope.launch {
            apiClient.uploadShot(
                backendUrl = backendUrl,
                sessionId = session.id,
                applianceType = session.applianceType,
                shotType = shot,
                jpegBytes = bytes
            ).onSuccess {
                val updated = session.copy(completedShots = session.completedShots + shot)
                val next = updated.nextShot
                _uiState.value = if (next != null) {
                    TrainingUiState.ReadyToCapture(updated, next)
                } else {
                    TrainingUiState.Complete(updated)
                }
            }.onFailure { e ->
                _uiState.value = TrainingUiState.Error(
                    e.message ?: "Upload failed", session, shot
                )
            }
        }
    }

    fun retryUpload() {
        val err = _uiState.value as? TrainingUiState.Error ?: return
        _uiState.value = TrainingUiState.ReadyToCapture(err.session, err.shot)
    }

    // ── Image conversion ──────────────────────────────────────────────────

    private fun ImageProxy.toJpegBytes(): ByteArray? = try {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        // Captured images are always JPEG from ImageCapture; rotate if needed
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val rotated = if (imageInfo.rotationDegrees != 0) {
            val m = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        } else bitmap
        ByteArrayOutputStream().also { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }.toByteArray()
    } catch (e: Exception) {
        Log.e(TAG, "Image conversion failed", e)
        null
    }

    companion object {
        private const val TAG = "TrainingViewModel"
    }
}

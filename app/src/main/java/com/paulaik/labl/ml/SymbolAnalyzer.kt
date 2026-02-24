package com.paulaik.labl.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.ImageFormat
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.paulaik.labl.api.ClaudeApiClient
import com.paulaik.labl.data.model.AnalysisResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * CameraX ImageAnalysis.Analyzer that throttles frames, converts them to
 * JPEG, and sends them to the Claude Vision API for symbol identification.
 */
class SymbolAnalyzer(
    private val scope: CoroutineScope,
    private val apiClient: ClaudeApiClient,
    private val apiKey: () -> String,
    private val onAnalysing: () -> Unit,
    private val onResult: (AnalysisResult) -> Unit,
    private val onError: (String) -> Unit,
    private val intervalMs: Long = 3_000L
) : ImageAnalysis.Analyzer {

    @Volatile private var lastAnalysisTs = 0L
    @Volatile private var busy = false

    /** Reset the interval timer so the next incoming frame is analysed immediately. */
    fun triggerNow() {
        lastAnalysisTs = 0L
    }

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (busy || now - lastAnalysisTs < intervalMs) {
            image.close()
            return
        }
        busy = true
        lastAnalysisTs = now

        val jpegBytes = image.toJpegBytes()
        image.close()

        if (jpegBytes == null) {
            busy = false
            return
        }

        scope.launch {
            try {
                onAnalysing()
                val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                val result = apiClient.analyzeFrame(b64, apiKey())
                result
                    .onSuccess { onResult(it) }
                    .onFailure { onError(it.message ?: "Unknown error") }
            } finally {
                busy = false
            }
        }
    }

    // ------------------------------------------------------------------
    // Image conversion helpers
    // ------------------------------------------------------------------

    private fun ImageProxy.toJpegBytes(): ByteArray? = try {
        val bitmap = when (format) {
            ImageFormat.JPEG -> {
                // Already JPEG – decode and re-encode after rotation
                val buf = planes[0].buffer
                val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            else -> yuv420ToBitmap()
        } ?: return null

        val rotated = bitmap.rotateDegrees(imageInfo.rotationDegrees)
        val scaled = rotated.scaledToMax(1024)

        ByteArrayOutputStream().also { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }.toByteArray()
    } catch (e: Exception) {
        Log.e(TAG, "Image conversion failed", e)
        null
    }

    private fun ImageProxy.yuv420ToBitmap(): Bitmap? = try {
        val yBuf = planes[0].buffer
        val uBuf = planes[1].buffer
        val vBuf = planes[2].buffer

        val ySize = yBuf.remaining()
        val uSize = uBuf.remaining()
        val vSize = vBuf.remaining()

        // Convert YUV_420_888 → NV21 → Bitmap via YuvImage
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuf.get(nv21, 0, ySize)
        vBuf.get(nv21, ySize, vSize)
        uBuf.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
        val jpegBytes = out.toByteArray()
        android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    } catch (e: Exception) {
        Log.e(TAG, "YUV conversion failed", e)
        null
    }

    private fun Bitmap.rotateDegrees(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
    }

    private fun Bitmap.scaledToMax(maxPx: Int): Bitmap {
        if (width <= maxPx && height <= maxPx) return this
        val scale = maxPx.toFloat() / maxOf(width, height)
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt(),
            (height * scale).toInt(),
            true
        )
    }

    companion object {
        private const val TAG = "SymbolAnalyzer"
    }
}

package com.paulaik.labl.ml

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.paulaik.labl.api.ClaudeApiClient
import com.paulaik.labl.data.model.AnalysisResult
import com.paulaik.labl.data.model.SymbolLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * CameraX [ImageAnalysis.Analyzer] that:
 *  1. Converts each incoming frame to a [Bitmap].
 *  2. Embeds it with [SymbolEmbedder] (MobileNet V2, 1280-dim).
 *  3. Checks the [EmbeddingStore] — if a near-identical frame has been seen
 *     before (cosine-sim ≥ 0.88) the cached [AnalysisResult] is returned
 *     **without** calling Claude.
 *  4. On a cache miss, encodes the frame as JPEG, calls the Claude Vision API,
 *     then stores the new (embedding, result) pair for future hits.
 *
 * [embedder] and [embeddingStore] are optional; if either is null (e.g. the
 * model hasn't downloaded yet) the analyzer falls back to Claude every time.
 */
class SymbolAnalyzer(
    private val scope: CoroutineScope,
    private val apiClient: ClaudeApiClient,
    private val apiKey: () -> String,
    private val onAnalysing: () -> Unit,
    private val onResult: (AnalysisResult) -> Unit,
    private val onError: (String) -> Unit,
    private val intervalMs: Long = 3_000L,
    private val examples: () -> List<SymbolLabel> = { emptyList() },
    private val embedder: SymbolEmbedder? = null,
    private val embeddingStore: EmbeddingStore? = null,
) : ImageAnalysis.Analyzer {

    @Volatile private var lastAnalysisTs = 0L
    @Volatile private var busy = false

    /** Reset the interval timer so the next incoming frame is analysed immediately. */
    fun triggerNow() { lastAnalysisTs = 0L }

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (busy || now - lastAnalysisTs < intervalMs) {
            image.close()
            return
        }
        busy = true
        lastAnalysisTs = now

        val bitmap = image.toBitmap()
        image.close()

        if (bitmap == null) { busy = false; return }

        scope.launch {
            try {
                // ── 1. Try local embedding store ──────────────────────────
                val embedding = embedder?.takeIf { it.isReady }?.embed(bitmap)
                if (embedding != null) {
                    val cached = embeddingStore?.findNearest(embedding)
                    if (cached != null) {
                        Log.d(TAG, "Cache hit — skipping Claude (store: ${embeddingStore?.size})")
                        onResult(cached.copy(timestamp = System.currentTimeMillis()))
                        return@launch
                    }
                }

                // ── 2. Fall back to Claude ────────────────────────────────
                onAnalysing()
                val jpegBytes = bitmap.toJpeg() ?: return@launch
                val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                apiClient.analyzeFrame(b64, apiKey(), examples())
                    .onSuccess { result ->
                        onResult(result)
                        // Populate store so future identical frames skip Claude
                        if (embedding != null && result.symbols.isNotEmpty()) {
                            embeddingStore?.add(embedding, result)
                            Log.d(TAG, "Stored embedding (store: ${embeddingStore?.size})")
                        }
                    }
                    .onFailure { onError(it.message ?: "Unknown error") }
            } finally {
                busy = false
            }
        }
    }

    // ── Image conversion helpers ─────────────────────────────────────────

    /** Converts the [ImageProxy] to a rotated, downscaled [Bitmap]. */
    private fun ImageProxy.toBitmap(): Bitmap? = try {
        val raw = when (format) {
            ImageFormat.JPEG -> {
                val buf = planes[0].buffer
                val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            else -> yuv420ToBitmap()
        } ?: return null
        raw.rotateDegrees(imageInfo.rotationDegrees).scaledToMax(1024)
    } catch (e: Exception) {
        Log.e(TAG, "Bitmap conversion failed", e)
        null
    }

    /** Compresses an existing [Bitmap] to JPEG bytes. */
    private fun Bitmap.toJpeg(): ByteArray? = try {
        ByteArrayOutputStream()
            .also { compress(Bitmap.CompressFormat.JPEG, 82, it) }
            .toByteArray()
    } catch (e: Exception) {
        Log.e(TAG, "JPEG encoding failed", e)
        null
    }

    private fun ImageProxy.yuv420ToBitmap(): Bitmap? = try {
        val yBuf = planes[0].buffer
        val uBuf = planes[1].buffer
        val vBuf = planes[2].buffer
        val ySize = yBuf.remaining()
        val uSize = uBuf.remaining()
        val vSize = vBuf.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuf.get(nv21, 0, ySize)
        vBuf.get(nv21, ySize, vSize)
        uBuf.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
        val bytes = out.toByteArray()
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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

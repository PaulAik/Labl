package com.paulaik.labl.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Wraps the MobileNet V2 TFLite feature extractor.
 *
 * Input:  224×224 RGB bitmap, channels normalised to [0, 1].
 * Output: 1280-dim L2-normalised float vector.
 *
 * Because vectors are L2-normalised, cosine similarity reduces to a plain
 * dot product, which is faster to compute in [EmbeddingStore].
 *
 * Call [initialize] once (after [ModelDownloader.ensureDownloaded] succeeds).
 * Check [isReady] before calling [embed]; if false the interpreter hasn't
 * loaded yet and [embed] returns null — the caller should fall back to Claude.
 */
class SymbolEmbedder(private val context: Context) {

    companion object {
        private const val TAG = "SymbolEmbedder"
        const val INPUT_SIZE = 224
        const val EMBEDDING_DIMS = 1280
    }

    private var interpreter: Interpreter? = null

    val isReady: Boolean get() = interpreter != null

    /**
     * Loads the TFLite interpreter from the cached model file.
     * Must be called from a background thread (file I/O).
     * Returns true on success.
     */
    fun initialize(): Boolean {
        val file = ModelDownloader.modelFile(context)
        if (!file.exists()) {
            Log.w(TAG, "Model file not found — run ModelDownloader first")
            return false
        }
        return try {
            val options = Interpreter.Options().apply { numThreads = 2 }
            interpreter = Interpreter(file, options)
            Log.i(TAG, "Embedder ready (${EMBEDDING_DIMS}-dim)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load model: ${e.message}")
            false
        }
    }

    /**
     * Embeds [bitmap] into a 1280-dim L2-normalised vector.
     * Returns null if the interpreter is not ready or inference fails.
     * Safe to call from any thread.
     */
    fun embed(bitmap: Bitmap): FloatArray? {
        val interp = interpreter ?: return null

        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        // NHWC float32 input buffer: [1, 224, 224, 3]
        val inputBuf = ByteBuffer
            .allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val px = resized.getPixel(x, y)
                inputBuf.putFloat((px shr 16 and 0xFF) / 255f)  // R
                inputBuf.putFloat((px shr 8  and 0xFF) / 255f)  // G
                inputBuf.putFloat((px        and 0xFF) / 255f)  // B
            }
        }
        inputBuf.rewind()

        val outputBuf = ByteBuffer
            .allocateDirect(EMBEDDING_DIMS * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

        return try {
            interp.run(inputBuf, outputBuf)
            outputBuf.rewind()
            FloatArray(EMBEDDING_DIMS) { outputBuf.float }.also { l2Normalise(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Inference failed: ${e.message}")
            null
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun l2Normalise(v: FloatArray) {
        var norm = 0f
        v.forEach { norm += it * it }
        norm = sqrt(norm.toDouble()).toFloat()
        if (norm > 0f) for (i in v.indices) v[i] /= norm
    }
}

package com.paulaik.labl.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads the MobileNet V2 feature-vector TFLite model on first launch and
 * caches it in [Context.getFilesDir]/ml/.
 *
 * The model produces 1280-dim embeddings from 224×224 RGB input (normalised [0,1]).
 *
 * Source: https://tfhub.dev/google/lite-model/imagenet/mobilenet_v2_100_224/feature_vector/5
 * If the URL below ever becomes stale, grab the latest .tflite from that page.
 */
object ModelDownloader {

    private const val TAG = "ModelDownloader"

    private const val MODEL_URL =
        "https://storage.googleapis.com/tfhub-lite-models/google/lite-model/" +
        "imagenet/mobilenet_v2_100_224/feature_vector/5/metadata/1.tflite"

    private const val MODEL_FILENAME = "mobilenet_v2.tflite"

    /** Minimum acceptable file size — guards against truncated downloads. */
    private const val MIN_VALID_BYTES = 8_000_000L  // ~8 MB

    fun modelFile(context: Context): File =
        File(context.filesDir, "ml/$MODEL_FILENAME")

    /**
     * Ensures the model file is present and valid.
     * Downloads it if missing. Returns true when the file is ready to use.
     * Safe to call on any thread; network I/O is dispatched to [Dispatchers.IO].
     */
    suspend fun ensureDownloaded(context: Context): Boolean = withContext(Dispatchers.IO) {
        val dest = modelFile(context)
        if (dest.exists() && dest.length() >= MIN_VALID_BYTES) {
            Log.d(TAG, "Model already cached (${dest.length()} bytes)")
            return@withContext true
        }
        Log.i(TAG, "Downloading MobileNet V2 model…")
        dest.parentFile?.mkdirs()
        try {
            val client = OkHttpClient.Builder()
                .callTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(MODEL_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} — download failed")
                    return@withContext false
                }
                val bytes = response.body?.bytes()
                if (bytes == null || bytes.size < MIN_VALID_BYTES) {
                    Log.w(TAG, "Incomplete download: ${bytes?.size} bytes")
                    dest.delete()
                    return@withContext false
                }
                dest.writeBytes(bytes)
                Log.i(TAG, "Model saved (${bytes.size} bytes)")
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download error: ${e.message}")
            dest.delete()
            false
        }
    }
}

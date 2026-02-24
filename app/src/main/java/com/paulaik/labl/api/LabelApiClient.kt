package com.paulaik.labl.api

import android.util.Log
import com.paulaik.labl.data.model.SymbolLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LabelApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchPendingLabels(backendUrl: String): Result<List<SymbolLabel>> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("${backendUrl.trimEnd('/')}/labels?status=pending")
                    .get()
                    .build()
                val body = client.newCall(req).execute().use { it.body?.string() ?: "[]" }
                Result.success(parseLabels(body))
            } catch (e: Exception) {
                Log.e(TAG, "fetchPendingLabels", e)
                Result.failure(e)
            }
        }

    /** Fetch raw JPEG bytes for a crop via the backend image proxy. */
    suspend fun fetchImageBytes(backendUrl: String, s3Key: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val url = "${backendUrl.trimEnd('/')}/images?key=${
                    java.net.URLEncoder.encode(s3Key, "UTF-8")
                }"
                val req = Request.Builder().url(url).get().build()
                val bytes = client.newCall(req).execute().use { it.body?.bytes() }
                    ?: return@withContext Result.failure(Exception("Empty response"))
                Result.success(bytes)
            } catch (e: Exception) {
                Log.e(TAG, "fetchImageBytes", e)
                Result.failure(e)
            }
        }

    /**
     * Fetch approved labels to use as few-shot examples in the AR prompt.
     * [category] filters by category (optional). Returns at most [limit] entries.
     */
    suspend fun fetchExamples(
        backendUrl: String,
        category: String = "",
        limit: Int = 5
    ): List<SymbolLabel> = withContext(Dispatchers.IO) {
        try {
            val cat = if (category.isNotBlank()) "&category=$category" else ""
            val req = Request.Builder()
                .url("${backendUrl.trimEnd('/')}/labels/examples?limit=$limit$cat")
                .get()
                .build()
            val body = client.newCall(req).execute().use { it.body?.string() ?: "[]" }
            parseLabels(body)
        } catch (e: Exception) {
            Log.e(TAG, "fetchExamples", e)
            emptyList()
        }
    }

    suspend fun submitValidation(
        backendUrl: String,
        id: String,
        approved: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", id)
                put("approved", approved)
            }.toString()
            val req = Request.Builder()
                .url("${backendUrl.trimEnd('/')}/labels/validate")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().close()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "submitValidation", e)
            Result.failure(e)
        }
    }

    private fun parseLabels(json: String): List<SymbolLabel> {
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            SymbolLabel(
                id = o.optString("id"),
                sessionId = o.optString("session_id"),
                applianceType = o.optString("appliance_type"),
                s3Key = o.optString("s3_key"),
                cropS3Key = o.optString("crop_s3_key").takeIf { it.isNotEmpty() },
                name = o.optString("name"),
                description = o.optString("description"),
                category = o.optString("category"),
                confidence = o.optString("confidence"),
                validated = o.optInt("validated", 0),
                createdAt = o.optLong("created_at", 0)
            )
        }
    }

    companion object {
        private const val TAG = "LabelApiClient"
    }
}

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

package com.paulaik.labl.api

import android.util.Log
import com.paulaik.labl.data.model.ApplianceType
import com.paulaik.labl.data.model.ShotType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class TrainingApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Upload a single training image to the Go backend.
     * Returns the parsed JSON body on success, or a failure with an error message.
     */
    suspend fun uploadShot(
        backendUrl: String,
        sessionId: String,
        applianceType: ApplianceType,
        shotType: ShotType,
        jpegBytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val imageBody = jpegBytes.toRequestBody("image/jpeg".toMediaType())
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("session_id", sessionId)
                .addFormDataPart("appliance_type", applianceType.name.lowercase())
                .addFormDataPart("shot_label", shotType.name.lowercase())
                .addFormDataPart("image", "${shotType.name.lowercase()}.jpg", imageBody)
                .build()

            val request = Request.Builder()
                .url("${backendUrl.trimEnd('/')}/upload")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(body)
            } else {
                Result.failure(Exception("Server error ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "TrainingApiClient"
    }
}

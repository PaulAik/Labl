package com.paulaik.labl.api

import android.util.Log
import com.paulaik.labl.data.model.AnalysisResult
import com.paulaik.labl.data.model.Confidence
import com.paulaik.labl.data.model.Symbol
import com.paulaik.labl.data.model.SymbolCategory
import com.paulaik.labl.data.model.SymbolLabel
import com.paulaik.labl.data.model.SymbolPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class ClaudeApiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeFrame(
        base64Jpeg: String,
        apiKey: String,
        fewShotExamples: List<SymbolLabel> = emptyList()
    ): Result<AnalysisResult> = withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("No API key configured. Open Settings to add your Claude API key.")
                )
            }

            val body = buildRequestJson(base64Jpeg, fewShotExamples)

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .build()

            runCatching {
                val response = http.newCall(request).execute()
                val responseText = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val msg = runCatching {
                        JSONObject(responseText).optJSONObject("error")?.optString("message")
                    }.getOrNull() ?: "HTTP ${response.code}"
                    error("Claude API error: $msg")
                }

                parseResponse(responseText)
            }
        }

    private fun buildRequestJson(
        base64Jpeg: String,
        fewShotExamples: List<SymbolLabel> = emptyList()
    ): JSONObject {
        val imageContent = JSONObject()
            .put("type", "image")
            .put(
                "source", JSONObject()
                    .put("type", "base64")
                    .put("media_type", "image/jpeg")
                    .put("data", base64Jpeg)
            )

        val prompt = if (fewShotExamples.isEmpty()) PROMPT else buildString {
            append(PROMPT)
            append("\n\nHere are some examples from your own validated symbol library:\n")
            fewShotExamples.forEachIndexed { i, ex ->
                append("${i + 1}. name=\"${ex.name}\" category=${ex.category} " +
                    "description=\"${ex.description}\"\n")
            }
            append("\nUse these as reference when identifying similar symbols.")
        }

        val textContent = JSONObject()
            .put("type", "text")
            .put("text", prompt)

        val message = JSONObject()
            .put("role", "user")
            .put("content", JSONArray().put(imageContent).put(textContent))

        return JSONObject()
            .put("model", "claude-haiku-3-5")
            .put("max_tokens", 1024)
            .put("messages", JSONArray().put(message))
    }

    private fun parseResponse(responseText: String): AnalysisResult {
        val root = JSONObject(responseText)
        val text = root
            .optJSONArray("content")
            ?.optJSONObject(0)
            ?.optString("text", "") ?: ""

        Log.d(TAG, "Claude response: $text")

        // Extract the JSON block – handle both raw JSON and markdown code fences
        val jsonBlock = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""").find(text)?.groupValues?.get(1)
            ?: Regex("""\{[\s\S]*\}""").find(text)?.value
            ?: return AnalysisResult(emptyList())

        val parsed = JSONObject(jsonBlock)
        val symbolsArray = parsed.optJSONArray("symbols") ?: return AnalysisResult(emptyList())

        val symbols = buildList {
            for (i in 0 until symbolsArray.length()) {
                val obj = symbolsArray.optJSONObject(i) ?: continue
                add(
                    Symbol(
                        id = UUID.randomUUID().toString(),
                        name = obj.optString("name", "Unknown Symbol"),
                        category = parseCategory(obj.optString("category")),
                        meaning = obj.optString("meaning", ""),
                        instructions = obj.optString("instructions", ""),
                        position = parsePosition(obj.optString("position")),
                        confidence = parseConfidence(obj.optString("confidence"))
                    )
                )
            }
        }
        return AnalysisResult(symbols)
    }

    private fun parseCategory(raw: String): SymbolCategory = when (raw.lowercase().trim()) {
        "washing" -> SymbolCategory.WASHING
        "drying" -> SymbolCategory.DRYING
        "ironing" -> SymbolCategory.IRONING
        "bleaching" -> SymbolCategory.BLEACHING
        "dishwasher" -> SymbolCategory.DISHWASHER
        "oven", "cooking" -> SymbolCategory.OVEN
        else -> SymbolCategory.OTHER
    }

    private fun parsePosition(raw: String): SymbolPosition = when (raw.lowercase().replace("-", "_").trim()) {
        "top_left" -> SymbolPosition.TOP_LEFT
        "top_center", "top" -> SymbolPosition.TOP_CENTER
        "top_right" -> SymbolPosition.TOP_RIGHT
        "middle_left", "left" -> SymbolPosition.MIDDLE_LEFT
        "center", "middle" -> SymbolPosition.CENTER
        "middle_right", "right" -> SymbolPosition.MIDDLE_RIGHT
        "bottom_left" -> SymbolPosition.BOTTOM_LEFT
        "bottom_center", "bottom" -> SymbolPosition.BOTTOM_CENTER
        "bottom_right" -> SymbolPosition.BOTTOM_RIGHT
        else -> SymbolPosition.CENTER
    }

    private fun parseConfidence(raw: String): Confidence = when (raw.lowercase().trim()) {
        "high" -> Confidence.HIGH
        "low" -> Confidence.LOW
        else -> Confidence.MEDIUM
    }

    companion object {
        private const val TAG = "ClaudeApiClient"

        private val PROMPT = """
You are an expert on appliance symbols and care labels. Analyse the image and identify every visible symbol or icon on an appliance control panel or care label. Look for symbols from:

- Washing machine (wash temperature, hand wash, gentle cycle, spin speed, etc.)
- Tumble dryer (tumble dry, line dry, flat dry, drip dry, no heat, etc.)
- Iron (ironing temperatures, steam iron, do not iron, etc.)
- Bleach (chlorine bleach, oxygen bleach, no bleach, etc.)
- Dishwasher (dishwasher safe, top rack only, hand wash only, etc.)
- Oven (fan/convection, grill, bottom heat, defrost, pizza setting, etc.)
- Any other appliance control symbols

For EVERY symbol you can see, respond using ONLY this exact JSON (no other text):

{
  "symbols": [
    {
      "name": "Short descriptive name (e.g. 'Fan Oven', '60°C Cotton Wash')",
      "category": "washing|drying|ironing|bleaching|dishwasher|oven|other",
      "meaning": "One sentence explaining what the symbol means.",
      "instructions": "One sentence of practical advice for the user.",
      "position": "Approximate location in the image: top-left|top-center|top-right|middle-left|center|middle-right|bottom-left|bottom-center|bottom-right",
      "confidence": "high|medium|low"
    }
  ]
}

If no symbols are found return: {"symbols": []}
        """.trimIndent()
    }
}

package com.paulaik.labl.ml

import android.content.Context
import android.util.Base64
import android.util.Log
import com.paulaik.labl.data.model.AnalysisResult
import com.paulaik.labl.data.model.Confidence
import com.paulaik.labl.data.model.Symbol
import com.paulaik.labl.data.model.SymbolCategory
import com.paulaik.labl.data.model.SymbolPosition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * Nearest-neighbour cache mapping frame embeddings → [AnalysisResult].
 *
 * Because [SymbolEmbedder] L2-normalises its output, cosine similarity between
 * two vectors equals their dot product — no expensive division required.
 *
 * Thread-safe via [synchronized]. Disk I/O is offloaded to a single
 * background thread so the camera pipeline is never blocked.
 *
 * Typical usage:
 *   1. [load] once at app start (from a coroutine on Dispatchers.IO).
 *   2. Call [findNearest] before every Claude call; return cached result on hit.
 *   3. Call [add] after every successful Claude response to grow the store.
 */
class EmbeddingStore(private val context: Context) {

    private data class Entry(
        val vector: FloatArray,
        val result: AnalysisResult,
        val ts: Long = System.currentTimeMillis()
    )

    companion object {
        private const val TAG = "EmbeddingStore"
        private const val STORE_FILE = "ml/embedding_store.json"

        /** Maximum entries kept in memory and on disk. Oldest are evicted first. */
        private const val MAX_ENTRIES = 500

        /**
         * Singleton so [CameraViewModel] and [ValidationViewModel] share the
         * same in-memory store without a DI framework.
         */
        @Volatile private var _instance: EmbeddingStore? = null
        fun getInstance(context: Context): EmbeddingStore =
            _instance ?: synchronized(this) {
                _instance ?: EmbeddingStore(context.applicationContext).also { _instance = it }
            }
    }

    private val entries = ArrayDeque<Entry>()
    private val ioThread = Executors.newSingleThreadExecutor()
    private val storeFile get() = File(context.filesDir, STORE_FILE)

    // ── Public API ─────────────────────────────────────────────────────────

    val size: Int get() = synchronized(entries) { entries.size }

    /**
     * Store a (vector, result) pair.
     * Near-duplicates (dot-product > 0.98) are removed to avoid redundancy.
     * Oldest entries are evicted when [MAX_ENTRIES] is reached.
     */
    fun add(vector: FloatArray, result: AnalysisResult) {
        synchronized(entries) {
            entries.removeAll { dot(it.vector, vector) > 0.98f }
            if (entries.size >= MAX_ENTRIES) entries.removeFirst()
            entries.addLast(Entry(vector.copyOf(), result))
        }
        ioThread.execute { persist() }
    }

    /**
     * Returns the cached [AnalysisResult] whose embedding has the highest
     * cosine similarity to [query] if it exceeds [threshold], else null.
     *
     * Default threshold 0.88 → same scene with minor camera jitter will hit;
     * a different appliance or meaningfully different angle will miss.
     */
    fun findNearest(query: FloatArray, threshold: Float = 0.88f): AnalysisResult? =
        synchronized(entries) {
            var bestSim = threshold - 1e-4f
            var bestResult: AnalysisResult? = null
            for (e in entries) {
                val sim = dot(e.vector, query)
                if (sim > bestSim) { bestSim = sim; bestResult = e.result }
            }
            bestResult
        }

    /** Load persisted entries from disk. Call once at startup on a background thread. */
    fun load() {
        val file = storeFile
        if (!file.exists()) return
        try {
            val arr = JSONArray(file.readText())
            val loaded = mutableListOf<Entry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                loaded.add(
                    Entry(
                        vector = decodeVector(obj.getString("v")),
                        result = decodeResult(obj.getJSONObject("r")),
                        ts     = obj.optLong("t", System.currentTimeMillis())
                    )
                )
            }
            synchronized(entries) {
                entries.clear()
                entries.addAll(loaded.takeLast(MAX_ENTRIES))
            }
            Log.i(TAG, "Loaded ${loaded.size} entries from disk")
        } catch (e: Exception) {
            Log.w(TAG, "Load failed: ${e.message}")
        }
    }

    // ── Similarity ─────────────────────────────────────────────────────────

    /** Dot product of two L2-normalised vectors == cosine similarity. */
    private fun dot(a: FloatArray, b: FloatArray): Float {
        var d = 0f
        for (i in a.indices) d += a[i] * b[i]
        return d
    }

    // ── Serialisation ───────────────────────────────────────────────────────

    private fun persist() {
        try {
            val snapshot = synchronized(entries) { entries.toList() }
            val arr = JSONArray()
            snapshot.forEach { e ->
                arr.put(JSONObject().apply {
                    put("v", encodeVector(e.vector))
                    put("r", encodeResult(e.result))
                    put("t", e.ts)
                })
            }
            storeFile.parentFile?.mkdirs()
            storeFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Persist failed: ${e.message}")
        }
    }

    /** Encodes a float array as little-endian Base64 binary (~6.7 KB per 1280-dim vector). */
    private fun encodeVector(v: FloatArray): String {
        val buf = ByteBuffer.allocate(v.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { buf.putFloat(it) }
        return Base64.encodeToString(buf.array(), Base64.NO_WRAP)
    }

    private fun decodeVector(encoded: String): FloatArray {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buf.float }
    }

    private fun encodeResult(r: AnalysisResult): JSONObject = JSONObject().apply {
        put("ts", r.timestamp)
        val syms = JSONArray()
        r.symbols.forEach { s ->
            syms.put(JSONObject().apply {
                put("id",      s.id)
                put("name",    s.name)
                put("cat",     s.category.name)
                put("meaning", s.meaning)
                put("instr",   s.instructions)
                put("pos",     s.position.name)
                put("conf",    s.confidence.name)
            })
        }
        put("symbols", syms)
    }

    private fun decodeResult(obj: JSONObject): AnalysisResult {
        val arr = obj.getJSONArray("symbols")
        val symbols = (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            Symbol(
                id           = s.getString("id"),
                name         = s.getString("name"),
                category     = runCatching { SymbolCategory.valueOf(s.getString("cat")) }
                                   .getOrDefault(SymbolCategory.OTHER),
                meaning      = s.getString("meaning"),
                instructions = s.optString("instr", ""),
                position     = runCatching { SymbolPosition.valueOf(s.getString("pos")) }
                                   .getOrDefault(SymbolPosition.CENTER),
                confidence   = runCatching { Confidence.valueOf(s.getString("conf")) }
                                   .getOrDefault(Confidence.MEDIUM)
            )
        }
        return AnalysisResult(symbols, obj.optLong("ts", System.currentTimeMillis()))
    }
}

package com.paulaik.labl.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paulaik.labl.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "labl_settings")

class ApiKeyStore(private val context: Context) {

    private val KEY = stringPreferencesKey("claude_api_key")

    /** Emits the stored key, falling back to the build-time key from local.properties. */
    val apiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY]?.takeIf { it.isNotBlank() }
            ?: BuildConfig.CLAUDE_API_KEY
    }

    suspend fun save(key: String) {
        context.dataStore.edit { it[KEY] = key.trim() }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(KEY) }
    }
}

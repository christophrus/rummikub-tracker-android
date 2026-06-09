package com.lorus.rummikubtracker.counter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single shared DataStore instance for the entire app.
 * Exposed as extension property so both tracker and counter use the same underlying file.
 */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsKeys {
    val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
    val THEME_MODE = stringPreferencesKey("theme")
}

object SettingsDefaults {
    const val CONFIDENCE_THRESHOLD = 0.25f
    const val THEME_MODE = "system"
}

class SettingsDataStore(context: Context) {

    private val dataStore: DataStore<Preferences> = context.appDataStore

    val confidenceThreshold: Flow<Float> = dataStore.data.map { prefs ->
        prefs[SettingsKeys.CONFIDENCE_THRESHOLD] ?: SettingsDefaults.CONFIDENCE_THRESHOLD
    }

    val themeMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[SettingsKeys.THEME_MODE] ?: SettingsDefaults.THEME_MODE
    }

    suspend fun setConfidenceThreshold(value: Float) {
        dataStore.edit { prefs -> prefs[SettingsKeys.CONFIDENCE_THRESHOLD] = value }
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { prefs -> prefs[SettingsKeys.THEME_MODE] = mode }
    }
}

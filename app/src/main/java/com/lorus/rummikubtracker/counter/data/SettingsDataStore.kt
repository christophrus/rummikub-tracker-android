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

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsKeys {
    val CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
    val THEME_MODE = stringPreferencesKey("theme_mode") // "system", "light", "dark"
}

object SettingsDefaults {
    const val CONFIDENCE_THRESHOLD = 0.25f
    const val THEME_MODE = "system"
}

class SettingsDataStore(private val context: Context) {

    val confidenceThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[SettingsKeys.CONFIDENCE_THRESHOLD] ?: SettingsDefaults.CONFIDENCE_THRESHOLD
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SettingsKeys.THEME_MODE] ?: SettingsDefaults.THEME_MODE
    }

    suspend fun setConfidenceThreshold(value: Float) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.CONFIDENCE_THRESHOLD] = value
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.THEME_MODE] = mode
        }
    }
}

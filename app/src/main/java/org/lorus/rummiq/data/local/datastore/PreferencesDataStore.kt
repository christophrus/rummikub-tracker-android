package org.lorus.rummiq.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import org.lorus.rummiq.counter.data.appDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppPreferences(
    val uiLanguage: String = "en",
    val ttsLanguage: String = "en",
    val theme: String = "system",
    val timerDuration: Int = 60000,
    val maxExtensions: Int = 3,
    val scrollLock: Boolean = false,
    val extensionReplenishRounds: Int = 0,
    val gameNumberSeq: Int = 1,
    val preferredSettings: String = "{}",
    val confidenceThreshold: Float = 0.70f
)

@Singleton
class PreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_UI_LANGUAGE = stringPreferencesKey("ui_language")
        private val KEY_TTS_LANGUAGE = stringPreferencesKey("tts_language")
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_TIMER_DURATION = intPreferencesKey("timer_duration")
        private val KEY_MAX_EXTENSIONS = intPreferencesKey("max_extensions")
        private val KEY_SCROLL_LOCK = booleanPreferencesKey("scroll_lock")
        private val KEY_EXTENSION_REPLENISH_ROUNDS = intPreferencesKey("extension_replenish_rounds")
        private val KEY_GAME_NUMBER_SEQ = intPreferencesKey("game_number_seq")
        private val KEY_PREFERRED_SETTINGS = stringPreferencesKey("preferred_settings")
        private val KEY_CONFIDENCE_THRESHOLD = floatPreferencesKey("confidence_threshold")
    }

    val preferences: Flow<AppPreferences> = context.appDataStore.data.map { prefs ->
        AppPreferences(
            uiLanguage = prefs[KEY_UI_LANGUAGE] ?: "en",
            ttsLanguage = prefs[KEY_TTS_LANGUAGE] ?: "en",
            theme = prefs[KEY_THEME] ?: "system",
            timerDuration = prefs[KEY_TIMER_DURATION] ?: 60000,
            maxExtensions = prefs[KEY_MAX_EXTENSIONS] ?: 3,
            scrollLock = prefs[KEY_SCROLL_LOCK] ?: false,
            extensionReplenishRounds = prefs[KEY_EXTENSION_REPLENISH_ROUNDS] ?: 0,
            gameNumberSeq = prefs[KEY_GAME_NUMBER_SEQ] ?: 1,
            preferredSettings = prefs[KEY_PREFERRED_SETTINGS] ?: "{}",
            confidenceThreshold = prefs[KEY_CONFIDENCE_THRESHOLD] ?: 0.70f
        )
    }

    suspend fun setUiLanguage(language: String) {
        context.appDataStore.edit { it[KEY_UI_LANGUAGE] = language }
    }

    suspend fun setTtsLanguage(language: String) {
        context.appDataStore.edit { it[KEY_TTS_LANGUAGE] = language }
    }

    suspend fun setTheme(theme: String) {
        context.appDataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun setTimerDuration(duration: Int) {
        context.appDataStore.edit { it[KEY_TIMER_DURATION] = duration }
    }

    suspend fun setMaxExtensions(max: Int) {
        context.appDataStore.edit { it[KEY_MAX_EXTENSIONS] = max }
    }

    suspend fun setScrollLock(locked: Boolean) {
        context.appDataStore.edit { it[KEY_SCROLL_LOCK] = locked }
    }

    suspend fun setExtensionReplenishRounds(rounds: Int) {
        context.appDataStore.edit { it[KEY_EXTENSION_REPLENISH_ROUNDS] = rounds }
    }

    suspend fun incrementAndGetGameNumber(): Int {
        var number = 1
        context.appDataStore.edit { prefs ->
            number = (prefs[KEY_GAME_NUMBER_SEQ] ?: 1) + 1
            prefs[KEY_GAME_NUMBER_SEQ] = number
        }
        return number
    }

    suspend fun setPreferredSettings(settings: String) {
        context.appDataStore.edit { it[KEY_PREFERRED_SETTINGS] = settings }
    }

    suspend fun setConfidenceThreshold(threshold: Float) {
        context.appDataStore.edit { it[KEY_CONFIDENCE_THRESHOLD] = threshold }
    }

    suspend fun clearAll() {
        context.appDataStore.edit { it.clear() }
    }
}

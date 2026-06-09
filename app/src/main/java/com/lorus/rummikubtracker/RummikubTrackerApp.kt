package com.lorus.rummikubtracker

import android.app.Application
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class RummikubTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Read locale synchronously via SharedPreferences (DataStore proto not readable sync)
        val prefs = getSharedPreferences("settings_prefs", MODE_PRIVATE)
        val lang = prefs.getString("ui_language", "en") ?: "en"
        LocaleManager.setLanguage(lang)
    }
}

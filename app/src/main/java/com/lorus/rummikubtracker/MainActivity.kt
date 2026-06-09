package com.lorus.rummikubtracker

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.lorus.rummikubtracker.data.local.datastore.PreferencesDataStore
import com.lorus.rummikubtracker.ui.navigation.RummikubNavHost
import com.lorus.rummikubtracker.ui.theme.RummikubTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesDataStore: PreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved UI language
        val language = runBlocking { preferencesDataStore.preferences.first().uiLanguage }
        val locale = LocaleManager.supportedLocales[language] ?: Locale.ENGLISH
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        enableEdgeToEdge()
        setContent {
            RummikubTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RummikubNavHost()
                }
            }
        }
    }
}

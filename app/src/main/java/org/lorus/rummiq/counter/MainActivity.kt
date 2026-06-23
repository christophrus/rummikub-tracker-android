package org.lorus.rummiq.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.lorus.rummiq.counter.data.SettingsDataStore
import org.lorus.rummiq.counter.ui.theme.RummiQCounterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsDataStore = SettingsDataStore(applicationContext)

        enableEdgeToEdge()
        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsState(initial = "system")
            RummiQCounterTheme(
                themeMode = themeMode,
                darkTheme = isSystemInDarkTheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RummiQApp()
                }
            }
        }
    }
}

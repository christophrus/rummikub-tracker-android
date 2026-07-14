package org.lorus.rummiq.ui.screens

import androidx.lifecycle.viewModelScope

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lorus.rummiq.R
import org.lorus.rummiq.LocaleManager
import org.lorus.rummiq.data.local.datastore.PreferencesDataStore
import org.lorus.rummiq.data.repository.GameRepository
import org.lorus.rummiq.data.repository.PlayerRepository
import org.lorus.rummiq.domain.model.Config
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
    private val gameRepository: GameRepository,
    private val playerRepository: PlayerRepository,
    @ApplicationContext private val appContext: android.content.Context
) : androidx.lifecycle.ViewModel() {

    var uiLanguage by mutableStateOf("en")
    var ttsLanguage by mutableStateOf("en")
    var theme by mutableStateOf("system")
    var confidenceThreshold by mutableStateOf(0.70f)
    var showClearDialog by mutableStateOf(false)
    var showUiLangDropdown by mutableStateOf(false)
    var showTtsDropdown by mutableStateOf(false)

    private val scope get() = viewModelScope

    init {
        scope.launch {
            preferencesDataStore.preferences.first().let { prefs ->
                uiLanguage = prefs.uiLanguage
                ttsLanguage = prefs.ttsLanguage
                theme = prefs.theme
                confidenceThreshold = prefs.confidenceThreshold
                // Sync static locale so attachBaseContext works on first launch
                LocaleManager.setLanguage(prefs.uiLanguage)
            }
        }
    }

    fun updateUiLanguage(lang: String) {
        uiLanguage = lang
        LocaleManager.setLanguage(lang)
        // Write to SharedPreferences for synchronous read at boot
        appContext.getSharedPreferences("settings_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("ui_language", lang).apply()
        scope.launch { preferencesDataStore.setUiLanguage(lang) }
    }

    fun updateTtsLanguage(lang: String) {
        ttsLanguage = lang
        scope.launch { preferencesDataStore.setTtsLanguage(lang) }
    }

    fun updateTheme(themeMode: String) {
        theme = themeMode
        scope.launch { preferencesDataStore.setTheme(themeMode) }
    }

    fun updateConfidenceThreshold(value: Float) {
        confidenceThreshold = value
        scope.launch { preferencesDataStore.setConfidenceThreshold(value) }
    }

    fun clearAllData() {
        scope.launch {
            playerRepository.deleteAllPlayers()
            preferencesDataStore.clearAll()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // UI Language
            Text(
                text = stringResource(R.string.ui_language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = viewModel.showUiLangDropdown,
                onExpandedChange = { viewModel.showUiLangDropdown = it }
            ) {
                OutlinedTextField(
                    value = stringResource(getUiLanguageResId(viewModel.uiLanguage)),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.showUiLangDropdown) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = viewModel.showUiLangDropdown,
                    onDismissRequest = { viewModel.showUiLangDropdown = false }
                ) {
                    Config.UI_LANGUAGES.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(stringResource(getUiLanguageResId(lang))) },
                            onClick = {
                                viewModel.updateUiLanguage(lang)
                                viewModel.showUiLangDropdown = false
                                (context as? android.app.Activity)?.recreate()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Voice Language
            Text(
                text = stringResource(R.string.voice_language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = viewModel.showTtsDropdown,
                onExpandedChange = { viewModel.showTtsDropdown = it }
            ) {
                OutlinedTextField(
                    value = stringResource(getTtsLanguageResId(viewModel.ttsLanguage)),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.showTtsDropdown) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = viewModel.showTtsDropdown,
                    onDismissRequest = { viewModel.showTtsDropdown = false }
                ) {
                    Config.TTS_LANGUAGES.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(stringResource(getTtsLanguageResId(lang))) },
                            onClick = {
                                viewModel.updateTtsLanguage(lang)
                                viewModel.showTtsDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Theme (radio buttons style from counter)
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))

            ThemeOption(
                label = stringResource(R.string.settings_theme_system),
                selected = viewModel.theme == "system",
                onClick = { viewModel.updateTheme("system") }
            )
            ThemeOption(
                label = stringResource(R.string.settings_theme_light),
                selected = viewModel.theme == "light",
                onClick = { viewModel.updateTheme("light") }
            )
            ThemeOption(
                label = stringResource(R.string.settings_theme_dark),
                selected = viewModel.theme == "dark",
                onClick = { viewModel.updateTheme("dark") }
            )

            Spacer(Modifier.height(24.dp))

            // Confidence Threshold (from counter)
            Text(
                text = stringResource(R.string.settings_confidence),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_confidence_desc, viewModel.confidenceThreshold * 100f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = viewModel.confidenceThreshold,
                onValueChange = { viewModel.updateConfidenceThreshold(it) },
                valueRange = 0.1f..0.9f,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("10%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("90%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.weight(1f))

            // Clear All Data button
            Button(
                onClick = { viewModel.showClearDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.clear_all_data))
            }
        }
    }

    // Clear confirmation dialog
    if (viewModel.showClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearDialog = false },
            title = { Text(stringResource(R.string.clear_all_data)) },
            text = { Text(stringResource(R.string.confirm_clear_data)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        viewModel.showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}

@androidx.annotation.StringRes
private fun getUiLanguageResId(code: String): Int {
    return when (code) {
        "de" -> R.string.lang_de
        "fr" -> R.string.lang_fr
        else -> R.string.lang_en
    }
}

@androidx.annotation.StringRes
private fun getTtsLanguageResId(code: String): Int {
    return when (code) {
        "en" -> R.string.lang_en
        "de" -> R.string.lang_de
        "fr" -> R.string.lang_fr
        "es" -> R.string.lang_es
        "it" -> R.string.lang_it
        "nl" -> R.string.lang_nl
        "pl" -> R.string.lang_pl
        "ru" -> R.string.lang_ru
        "tr" -> R.string.lang_tr
        "cs" -> R.string.lang_cs
        else -> R.string.lang_en
    }
}

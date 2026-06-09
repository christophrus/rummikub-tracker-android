package com.lorus.rummikubtracker.ui.screens

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
import com.lorus.rummikubtracker.R
import com.lorus.rummikubtracker.data.local.datastore.PreferencesDataStore
import com.lorus.rummikubtracker.data.repository.GameRepository
import com.lorus.rummikubtracker.data.repository.PlayerRepository
import com.lorus.rummikubtracker.domain.model.Config
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataStore: PreferencesDataStore,
    private val gameRepository: GameRepository,
    private val playerRepository: PlayerRepository
) : androidx.lifecycle.ViewModel() {

    var uiLanguage by mutableStateOf("en")
    var ttsLanguage by mutableStateOf("en")
    var theme by mutableStateOf("system")
    var confidenceThreshold by mutableStateOf(0.25f)
    var showClearDialog by mutableStateOf(false)
    var showUiLangDropdown by mutableStateOf(false)
    var showTtsDropdown by mutableStateOf(false)

    private val scope = kotlinx.coroutines.MainScope()

    init {
        scope.launch {
            preferencesDataStore.preferences.first().let { prefs ->
                uiLanguage = prefs.uiLanguage
                ttsLanguage = prefs.ttsLanguage
                theme = prefs.theme
                confidenceThreshold = prefs.confidenceThreshold
            }
        }
    }

    fun updateUiLanguage(lang: String) {
        uiLanguage = lang
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
                    value = getUiLanguageName(viewModel.uiLanguage),
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
                            text = { Text(getUiLanguageName(lang)) },
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
                    value = getTtsLanguageName(viewModel.ttsLanguage),
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
                            text = { Text(getTtsLanguageName(lang)) },
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

private fun getUiLanguageName(code: String): String {
    return when (code) {
        "de" -> "Deutsch"
        "fr" -> "Français"
        else -> "English"
    }
}

private fun getTtsLanguageName(code: String): String {
    return when (code) {
        "en" -> "English"
        "de" -> "Deutsch"
        "fr" -> "Français"
        "es" -> "Español"
        "it" -> "Italiano"
        "nl" -> "Nederlands"
        "pl" -> "Polski"
        "ru" -> "Русский"
        "tr" -> "Türkçe"
        "cs" -> "Čeština"
        else -> code
    }
}

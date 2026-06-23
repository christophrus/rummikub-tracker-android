package org.lorus.rummiq.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.lorus.rummiq.R
import org.lorus.rummiq.data.local.datastore.PreferencesDataStore
import org.lorus.rummiq.data.repository.GameRepository
import org.lorus.rummiq.domain.model.Config
import org.lorus.rummiq.domain.model.Game
import org.lorus.rummiq.domain.model.Player
import org.lorus.rummiq.domain.usecase.PlayerManager
import org.lorus.rummiq.ui.components.ScrollIndicator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewPlayerEntry(
    val name: String = "",
    val imagePath: String? = null
)

@HiltViewModel
class NewGameViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val playerManager: PlayerManager,
    private val preferencesDataStore: PreferencesDataStore,
    @ApplicationContext private val appContext: android.content.Context
) : androidx.lifecycle.ViewModel() {

    var gameName by mutableStateOf("")
    var timerDuration by mutableStateOf(60_000)
    var maxExtensions by mutableStateOf(3)
    var extensionReplenishEnabled by mutableStateOf(false)
    var extensionReplenishRounds by mutableStateOf(4)
    var players by mutableStateOf(listOf<NewPlayerEntry>())
    var savedPlayers by mutableStateOf<List<Player>>(emptyList())
    var showTimerDropdown by mutableStateOf(false)
    var newPlayerName by mutableStateOf("")
    var errorMessage by mutableStateOf<String?>(null)

    private val scope = kotlinx.coroutines.MainScope()

    init {
        scope.launch {
            preferencesDataStore.preferences.first().let { prefs ->
                timerDuration = prefs.timerDuration
                maxExtensions = prefs.maxExtensions
            }
            playerManager.getAllPlayers().collect { saved ->
                savedPlayers = saved
            }
        }
    }

    fun addPlayer(name: String) {
        if (name.isBlank()) return
        players = players + NewPlayerEntry(name = name.trim())
        newPlayerName = ""
        errorMessage = null
    }

    fun removePlayer(index: Int) {
        players = players.toMutableList().also { it.removeAt(index) }
        errorMessage = null
    }

    fun movePlayer(fromIndex: Int, toIndex: Int) {
        if (toIndex < 0 || toIndex >= players.size) return
        val mutable = players.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        players = mutable
    }

    fun addSavedPlayer(player: Player) {
        if (players.any { it.name == player.name }) return
        players = players + NewPlayerEntry(name = player.name, imagePath = player.imagePath)
        errorMessage = null
    }

    suspend fun startGame(): Long? {
        if (players.size < 2) {
            errorMessage = "min_two_players"
            return null
        }

        val gameName = gameName.ifBlank {
            val seq = preferencesDataStore.incrementAndGetGameNumber()
            appContext.getString(R.string.default_game_name, seq)
        }

        // Save all new players
        players.forEach { entry ->
            if (savedPlayers.none { it.name == entry.name }) {
                playerManager.savePlayer(entry.name, entry.imagePath)
            }
        }

        val game = Game(
            name = gameName,
            timerDuration = timerDuration,
            originalTimerDuration = timerDuration,
            maxExtensions = maxExtensions,
            extensionReplenishRounds = if (extensionReplenishEnabled) extensionReplenishRounds else 0,
            ttsLanguage = preferencesDataStore.preferences.first().ttsLanguage,
            players = players.mapIndexed { index, entry ->
                Player(name = entry.name, imagePath = entry.imagePath, order = index, maxExtensions = maxExtensions)
            }
        )

        // Save preferences
        preferencesDataStore.setTimerDuration(timerDuration)
        preferencesDataStore.setMaxExtensions(maxExtensions)

        return gameRepository.createGame(game)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewGameScreen(
    onStartGame: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: NewGameViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    var showSavedPlayers by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_game_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        val canScrollForward by remember { derivedStateOf { scrollState.canScrollForward } }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState)
            ) {
            // Game name
            OutlinedTextField(
                value = viewModel.gameName,
                onValueChange = { viewModel.gameName = it },
                label = { Text(stringResource(R.string.game_name)) },
                placeholder = { Text(stringResource(R.string.game_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            // Timer duration dropdown
            ExposedDropdownMenuBox(
                expanded = viewModel.showTimerDropdown,
                onExpandedChange = { viewModel.showTimerDropdown = it }
            ) {
                OutlinedTextField(
                    value = stringResource(getTimerDurationResId(viewModel.timerDuration)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.timer_duration)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.showTimerDropdown) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = viewModel.showTimerDropdown,
                    onDismissRequest = { viewModel.showTimerDropdown = false }
                ) {
                    Config.TIMER_PRESETS.forEach { (ms, labelRes) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(getStringResId(labelRes))) },
                            onClick = {
                                viewModel.timerDuration = ms
                                viewModel.showTimerDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Max extensions slider
            Text(
                text = "${stringResource(R.string.max_extensions)}: ${viewModel.maxExtensions}",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = viewModel.maxExtensions.toFloat(),
                onValueChange = { viewModel.maxExtensions = it.toInt() },
                valueRange = 0f..10f,
                steps = 9
            )

            Spacer(Modifier.height(16.dp))

            // Extension replenishment
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.extension_replenish))
                Switch(
                    checked = viewModel.extensionReplenishEnabled,
                    onCheckedChange = { viewModel.extensionReplenishEnabled = it }
                )
            }
            if (viewModel.extensionReplenishEnabled) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Config.REPLENISH_ROUNDS_OPTIONS.forEach { rounds ->
                        FilterChip(
                            selected = viewModel.extensionReplenishRounds == rounds,
                            onClick = { viewModel.extensionReplenishRounds = rounds },
                            label = { Text(stringResource(R.string.every_n_rounds, rounds)) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Players section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${stringResource(R.string.players)} (${viewModel.players.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = { showSavedPlayers = !showSavedPlayers }) {
                    Text(stringResource(R.string.quick_add))
                }
            }

            // Add player row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = viewModel.newPlayerName,
                    onValueChange = { viewModel.newPlayerName = it },
                    label = { Text(stringResource(R.string.add_player)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { viewModel.addPlayer(viewModel.newPlayerName) }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_player))
                }
            }

            // Saved players quick-add
            if (showSavedPlayers && viewModel.savedPlayers.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Column {
                    viewModel.savedPlayers
                        .filter { sp -> viewModel.players.none { it.name == sp.name } }
                        .forEach { player ->
                            Text(
                                text = player.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.addSavedPlayer(player) }
                                    .padding(8.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                }
            }

            // Player list
            Column(
                modifier = Modifier.padding(top = 8.dp)
            ) {
                viewModel.players.forEachIndexed { index, player ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.movePlayer(index, index - 1) }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                        }
                        IconButton(onClick = { viewModel.movePlayer(index, index + 1) }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                        Text(
                            text = "${index + 1}. ${player.name}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        IconButton(onClick = { viewModel.removePlayer(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }

            // Error message
            viewModel.errorMessage?.let { error ->
                Text(
                    text = stringResource(getStringResId(error)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(8.dp))

            // Start Game button
            Button(
                onClick = {
                    scope.launch {
                        val gameId = viewModel.startGame()
                        if (gameId != null) onStartGame(gameId)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = viewModel.players.size >= 2
            ) {
                Text(
                    text = stringResource(R.string.start_game),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

            ScrollIndicator(
                canScrollForward = canScrollForward,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@androidx.annotation.StringRes
private fun getTimerDurationResId(ms: Int): Int {
    return when (ms) {
        30_000 -> R.string.timer_30s
        45_000 -> R.string.timer_45s
        60_000 -> R.string.timer_1m
        90_000 -> R.string.timer_1_5m
        120_000 -> R.string.timer_2m
        180_000 -> R.string.timer_3m
        300_000 -> R.string.timer_5m
        else -> R.string.app_name
    }
}

private fun getStringResId(name: String): Int {
    return when (name) {
        "timer_30s" -> R.string.timer_30s
        "timer_45s" -> R.string.timer_45s
        "timer_1m" -> R.string.timer_1m
        "timer_1_5m" -> R.string.timer_1_5m
        "timer_2m" -> R.string.timer_2m
        "timer_3m" -> R.string.timer_3m
        "timer_5m" -> R.string.timer_5m
        "min_two_players" -> R.string.min_two_players
        else -> R.string.app_name
    }
}

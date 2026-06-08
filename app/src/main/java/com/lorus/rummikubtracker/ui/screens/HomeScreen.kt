package com.lorus.rummikubtracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lorus.rummikubtracker.R
import com.lorus.rummikubtracker.data.repository.GameRepository
import com.lorus.rummikubtracker.domain.model.Game
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gameRepository: GameRepository
) : androidx.lifecycle.ViewModel() {
    var activeGame by mutableStateOf<Game?>(null)
        private set

    init {
        // Collect active game in a coroutine
        kotlinx.coroutines.MainScope().launch {
            gameRepository.getActiveGame().collect { game ->
                activeGame = game
            }
        }
    }

    fun cancelGame() {
        kotlinx.coroutines.MainScope().launch {
            activeGame?.let { gameRepository.deleteGame(it.id) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewGame: () -> Unit,
    onResumeGame: (Long) -> Unit,
    onManagePlayers: () -> Unit,
    onGameHistory: () -> Unit,
    onSettings: () -> Unit,
    onCounter: () -> Unit,
    viewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val activeGame = viewModel.activeGame
    var showCancelDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Active game card
            if (activeGame != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    onClick = { onResumeGame(activeGame.id) }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.game_in_progress),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = activeGame.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Round ${activeGame.currentRound + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { onResumeGame(activeGame.id) }) {
                                Text(stringResource(R.string.resume_game))
                            }
                            TextButton(
                                onClick = { showCancelDialog = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(stringResource(R.string.cancel_game))
                            }
                        }
                    }
                }
            }

            // Main action buttons
            ActionButton(
                title = stringResource(R.string.new_game),
                onClick = onNewGame
            )
            ActionButton(
                title = stringResource(R.string.manage_players),
                onClick = onManagePlayers
            )
            ActionButton(
                title = stringResource(R.string.game_history),
                onClick = onGameHistory
            )
            ActionButton(
                title = stringResource(R.string.settings),
                onClick = onSettings
            )
            ActionButton(
                title = stringResource(R.string.counter),
                onClick = onCounter
            )
        }
    }

    // Cancel game dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.cancel_game)) },
            text = { Text(stringResource(R.string.confirm_cancel_game)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.cancelGame()
                            showCancelDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }
}

@Composable
private fun ActionButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

package org.lorus.rummiq.ui.screens

import androidx.lifecycle.viewModelScope

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lorus.rummiq.R
import org.lorus.rummiq.data.repository.GameRepository
import org.lorus.rummiq.domain.engine.TimerEngine
import org.lorus.rummiq.domain.model.Game
import org.lorus.rummiq.ui.components.ScrollIndicator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val timerEngine: TimerEngine
) : androidx.lifecycle.ViewModel() {
    var activeGame by mutableStateOf<Game?>(null)
        private set

    init {
        viewModelScope.launch {
            gameRepository.getActiveGame().collect { game ->
                activeGame = game
            }
        }
    }

    fun cancelGame() {
        viewModelScope.launch {
            activeGame?.let {
                timerEngine.stop()
                gameRepository.deleteGame(it.id)
            }
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
    var showActiveGameWarning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        val scrollState = rememberScrollState()
        val canScrollForward by remember { derivedStateOf { scrollState.canScrollForward } }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Spacer(Modifier.height(48.dp))

            // App icon
            Icon(
                imageVector = Icons.Default.Casino,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))

            // Title
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(32.dp))

            // Active game card
            if (activeGame != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    onClick = { onResumeGame(activeGame.id) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                activeGame.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.round_label, activeGame.currentRound + 1),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = { showCancelDialog = true }) {
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.cancel_game),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            Spacer(Modifier.height(16.dp))

            // Menu items
            MenuItem(
                icon = Icons.Default.AddCircle,
                title = stringResource(R.string.new_game),
                description = stringResource(R.string.new_game_desc),
                onClick = {
                    if (activeGame != null) {
                        showActiveGameWarning = true
                    } else {
                        onNewGame()
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            MenuItem(
                icon = Icons.Default.People,
                title = stringResource(R.string.manage_players),
                description = stringResource(R.string.manage_players_desc),
                onClick = onManagePlayers
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            MenuItem(
                icon = Icons.Default.History,
                title = stringResource(R.string.game_history),
                description = stringResource(R.string.game_history_desc),
                onClick = onGameHistory
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            MenuItem(
                icon = Icons.Default.CameraAlt,
                title = stringResource(R.string.counter),
                description = stringResource(R.string.counter_desc),
                onClick = onCounter
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            MenuItem(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.settings),
                description = stringResource(R.string.settings_desc),
                onClick = onSettings
            )

            Spacer(Modifier.height(32.dp))
            }

            ScrollIndicator(
                canScrollForward = canScrollForward,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // New game while active game warning
    if (showActiveGameWarning) {
        AlertDialog(
            onDismissRequest = { showActiveGameWarning = false },
            title = { Text(stringResource(R.string.game_in_progress)) },
            text = { Text(stringResource(R.string.confirm_cancel_game)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.cancelGame()
                            showActiveGameWarning = false
                            onNewGame()
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
                TextButton(onClick = { showActiveGameWarning = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
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
private fun MenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

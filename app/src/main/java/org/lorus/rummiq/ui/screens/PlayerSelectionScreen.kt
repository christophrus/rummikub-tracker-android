package org.lorus.rummiq.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.lorus.rummiq.R
import org.lorus.rummiq.data.local.dao.GamePlayerDao
import org.lorus.rummiq.data.repository.GameRepository
import org.lorus.rummiq.domain.model.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerSelectionViewModel @Inject constructor(
    private val gamePlayerDao: GamePlayerDao,
    private val gameRepository: GameRepository
) : androidx.lifecycle.ViewModel() {
    var players by mutableStateOf<List<Player>>(emptyList())
    var selectedPlayer by mutableStateOf<String?>(null)

    private val scope = kotlinx.coroutines.MainScope()

    fun loadPlayers(gameId: Long) {
        scope.launch {
            gamePlayerDao.getPlayersForGameOnce(gameId).let { entities ->
                players = entities.map { Player(name = it.playerName, order = it.playerOrder) }
            }
        }
    }

    fun setStartingPlayer(gameId: Long, playerName: String) {
        scope.launch {
            val game = gameRepository.getGameById(gameId).first() ?: return@launch
            val playerIndex = game.players.indexOfFirst { it.name == playerName }
            if (playerIndex >= 0) {
                gameRepository.updateGame(
                    game.copy(
                        currentPlayerIndex = playerIndex,
                        roundBeginnerIndex = playerIndex
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSelectionScreen(
    gameId: Long,
    onPlayerSelected: () -> Unit,
    onBack: () -> Unit,
    viewModel: PlayerSelectionViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    LaunchedEffect(gameId) {
        viewModel.loadPlayers(gameId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_starting_player)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.select_starting_player),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(viewModel.players) { player ->
                    Card(
                        onClick = {
                            viewModel.selectedPlayer = player.name
                            viewModel.setStartingPlayer(gameId, player.name)
                            onPlayerSelected()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (viewModel.selectedPlayer == player.name)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = player.name,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

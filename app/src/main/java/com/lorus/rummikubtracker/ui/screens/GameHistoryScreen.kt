package com.lorus.rummikubtracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lorus.rummikubtracker.R
import com.lorus.rummikubtracker.data.repository.GameRepository
import com.lorus.rummikubtracker.domain.model.Game
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class GameHistoryViewModel @Inject constructor(
    private val gameRepository: GameRepository
) : androidx.lifecycle.ViewModel() {
    var games by mutableStateOf<List<Game>>(emptyList())
    var gameToDelete by mutableStateOf<Long?>(null)
    var expandedGameId by mutableStateOf<Long?>(null)

    private val scope = kotlinx.coroutines.MainScope()

    init {
        scope.launch {
            gameRepository.getCompletedGames().collect { completed ->
                games = completed
            }
        }
    }

    fun deleteGame(gameId: Long) {
        scope.launch {
            gameRepository.deleteGame(gameId)
            gameToDelete = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameHistoryScreen(
    onBack: () -> Unit,
    onViewGame: (Long) -> Unit,
    viewModel: GameHistoryViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.game_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.games.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_games_played),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(viewModel.games) { game ->
                    val isExpanded = viewModel.expandedGameId == game.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable {
                                viewModel.expandedGameId = if (isExpanded) null else game.id
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = game.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = game.endTime?.let {
                                            stringResource(R.string.date_label, dateFormat.format(Date(it)))
                                        } ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.rounds_label, game.rounds.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Icon(
                                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                            }

                            // Winner info
                            val winner = game.computeWinner()
                            if (winner != null) {
                                Text(
                                    text = stringResource(R.string.winner_label, winner),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Expanded score table
                            AnimatedVisibility(visible = isExpanded) {
                                Column {
                                    Spacer(Modifier.height(12.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(8.dp))

                                    if (game.rounds.isNotEmpty()) {
                                        // Score table header
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState())
                                        ) {
                                            // Round numbers column
                                            Column(modifier = Modifier.width(60.dp)) {
                                                Text(
                                                    text = "#",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                game.rounds.forEach { round ->
                                                    Text(
                                                        text = "R${round.roundNumber + 1}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.padding(vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = stringResource(R.string.total),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            // Score columns per player
                                            game.players.forEach { player ->
                                                Column(
                                                    modifier = Modifier.width(70.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = player.name.take(8),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    val cumulatives = game.getCumulativeTotals()
                                                    game.rounds.forEach { round ->
                                                        val score = round.scores[player.name] ?: 0
                                                        Text(
                                                            text = "$score",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            modifier = Modifier.padding(vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "${game.getPlayerTotal(player.name)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(onClick = { viewModel.gameToDelete = game.id }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.delete_game),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    viewModel.gameToDelete?.let { gameId ->
        AlertDialog(
            onDismissRequest = { viewModel.gameToDelete = null },
            title = { Text(stringResource(R.string.delete_game)) },
            text = { Text(stringResource(R.string.confirm_delete_game)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteGame(gameId) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.gameToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

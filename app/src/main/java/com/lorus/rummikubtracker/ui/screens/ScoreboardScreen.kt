package com.lorus.rummikubtracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lorus.rummikubtracker.R
import com.lorus.rummikubtracker.data.repository.GameRepository
import com.lorus.rummikubtracker.domain.model.Game
import com.lorus.rummikubtracker.domain.usecase.GameManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// Vibrant player colours for scoreboard
private val PLAYER_COLORS = listOf(
    Color(0xFFE53935), // red
    Color(0xFF1E88E5), // blue
    Color(0xFF43A047), // green
    Color(0xFFFB8C00), // orange
    Color(0xFF8E24AA), // purple
    Color(0xFF00ACC1), // cyan
)

@HiltViewModel
class ScoreboardViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val gameManager: GameManager
) : androidx.lifecycle.ViewModel() {

    var game by mutableStateOf<Game?>(null)
        private set

    private val scope = kotlinx.coroutines.MainScope()
    private var gameId: Long = 0

    fun loadGame(id: Long) {
        gameId = id
        scope.launch {
            game = gameRepository.getGameById(gameId).first()
        }
    }

    fun endGame(onDone: () -> Unit) {
        val g = game ?: return
        scope.launch {
            gameManager.endGame(g)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreboardScreen(
    gameId: Long,
    onBack: () -> Unit,
    onGameEnded: () -> Unit = {},
    viewModel: ScoreboardViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    LaunchedEffect(gameId) {
        viewModel.loadGame(gameId)
    }

    val game = viewModel.game
    var showEndGameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.scoreboard),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF16213E)
    ) { padding ->
        val g = game
        if (g == null || g.rounds.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_rounds_played),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            return@Scaffold
        }

        // Compute cumulative running totals per round
        val cumulativeByPlayer = remember(g) {
            val map = mutableMapOf<String, MutableList<Int>>()
            g.players.forEach { p -> map[p.name] = mutableListOf() }
            g.rounds.forEach { round ->
                g.players.forEach { p ->
                    val prev = map[p.name]?.lastOrNull() ?: 0
                    val score = round.scores[p.name] ?: 0
                    map[p.name]?.add(prev + score)
                }
            }
            map
        }

        val playerColors = g.players.mapIndexed { idx, _ ->
            PLAYER_COLORS[idx % PLAYER_COLORS.size]
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            // === Header card ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3460))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = g.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${g.rounds.size} ${stringResource(R.string.rounds_label, 0).substringBefore(\":\")} · ${g.players.size} ${stringResource(R.string.players)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // === Rounds table card ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // --- Table header ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF0F3460), Color(0xFF1A1A2E))
                                ),
                                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Round number column
                        Text(
                            text = "#",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.width(44.dp),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.width(4.dp))
                        // Player score columns
                        g.players.forEachIndexed { idx, player ->
                            Text(
                                text = player.name.take(8),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = playerColors[idx],
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            if (idx < g.players.size - 1) {
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // --- Round rows ---
                    g.rounds.forEachIndexed { roundIdx, round ->
                        val isEven = roundIdx % 2 == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isEven) Color.White.copy(alpha = 0.03f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Round number
                            Text(
                                text = "${stringResource(R.string.round_abbr)}${roundIdx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.6f),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(44.dp),
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.width(4.dp))

                            // Player scores
                            g.players.forEachIndexed { pIdx, player ->
                                val score = round.scores[player.name] ?: 0
                                val isWinner = round.winnerPlayerName == player.name
                                val cumTotal = cumulativeByPlayer[player.name]?.getOrNull(roundIdx)

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$score",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isWinner) Color(0xFFFFB300) else Color.White.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center
                                    )
                                    if (isWinner) {
                                        Text(
                                            text = "🏆",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.offset(x = 18.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                if (pIdx < g.players.size - 1) {
                                    Spacer(Modifier.width(4.dp))
                                }
                            }
                        }

                        if (roundIdx < g.rounds.size - 1) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.05f),
                                thickness = 0.5.dp
                            )
                        }
                    }

                    HorizontalDivider(
                        color = Color(0xFFFFB300).copy(alpha = 0.4f),
                        thickness = 1.5.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // --- Final totals row ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFFFFB300).copy(alpha = 0.08f),
                                RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.total).take(4),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB300),
                            modifier = Modifier.width(44.dp),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.width(4.dp))
                        g.players.forEachIndexed { idx, player ->
                            val total = cumulativeByPlayer[player.name]?.lastOrNull() ?: 0
                            Text(
                                text = "$total",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            if (idx < g.players.size - 1) {
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // === End Game button ===
            Button(
                onClick = { showEndGameDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC62828)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.end_game),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // End Game confirmation dialog
    if (showEndGameDialog) {
        AlertDialog(
            onDismissRequest = { showEndGameDialog = false },
            title = { Text(stringResource(R.string.end_game)) },
            text = { Text(stringResource(R.string.confirm_end_game)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndGameDialog = false
                        viewModel.endGame(onGameEnded)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndGameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

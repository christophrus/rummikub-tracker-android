package org.lorus.rummiq.ui.screens

import androidx.lifecycle.viewModelScope

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
import org.lorus.rummiq.R
import org.lorus.rummiq.data.repository.GameRepository
import org.lorus.rummiq.domain.engine.TimerEngine
import org.lorus.rummiq.domain.model.Game
import org.lorus.rummiq.domain.usecase.GameManager
import org.lorus.rummiq.ui.components.ScrollIndicator
import org.lorus.rummiq.ui.theme.goldAccentColor
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
    private val gameManager: GameManager,
    private val timerEngine: TimerEngine
) : androidx.lifecycle.ViewModel() {

    var game by mutableStateOf<Game?>(null)
        private set

    private val scope get() = viewModelScope
    private var gameId: Long = 0

    fun loadGame(id: Long) {
        gameId = id
        scope.launch {
            game = gameRepository.getGameById(gameId).first()
        }
    }

    fun endGame(onDone: (Long) -> Unit) {
        val g = game ?: return
        val gid = g.id
        scope.launch {
            gameManager.endGame(g)
            timerEngine.stop()
            onDone(gid)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreboardScreen(
    gameId: Long,
    onBack: () -> Unit,
    onGameEnded: (Long) -> Unit = {},
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
                }
            )
        }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

        val scrollState = rememberScrollState()
        val canScrollForward by remember { derivedStateOf { scrollState.canScrollForward } }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(12.dp)
            ) {
            // === Header card ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${g.rounds.size} ${stringResource(R.string.rounds_label, 0).substringBefore(':')} · ${g.players.size} ${stringResource(R.string.players)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // === Rounds table card ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // --- Round rows ---
                    g.rounds.forEachIndexed { roundIdx, round ->
                        val isEven = roundIdx % 2 == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isEven) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Round number
                            Text(
                                text = "${stringResource(R.string.round_abbr)}${roundIdx + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(44.dp),
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.width(4.dp))

                            // Player scores
                            val lowestRoundScore = round.scores.values.filter { it >= 0 }.minOrNull() ?: 0
                            g.players.forEachIndexed { pIdx, player ->
                                val score = round.scores[player.name] ?: 0
                                val isLowest = score == lowestRoundScore
                                val cumTotal = cumulativeByPlayer[player.name]?.getOrNull(roundIdx)

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$score",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isLowest) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isLowest) goldAccentColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    if (isLowest) {
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
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f),
                        thickness = 1.5.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // --- Final totals row ---
                    val totals = g.players.map { cumulativeByPlayer[it.name]?.lastOrNull() ?: 0 }
                    val lowestTotal = totals.minOrNull() ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                                RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.total).take(4),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = goldAccentColor(),
                            modifier = Modifier.width(44.dp),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.width(4.dp))
                        g.players.forEachIndexed { idx, player ->
                            val total = cumulativeByPlayer[player.name]?.lastOrNull() ?: 0
                            val isWinner = total == lowestTotal
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$total",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWinner) goldAccentColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (isWinner) {
                                    Text(
                                        text = "🏆",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.offset(x = 16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
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
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.end_game),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(16.dp))
        }

        ScrollIndicator(
            canScrollForward = canScrollForward,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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

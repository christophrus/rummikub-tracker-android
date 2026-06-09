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
    private val gameRepository: GameRepository
) : androidx.lifecycle.ViewModel() {

    var game by mutableStateOf<Game?>(null)
        private set

    private val scope = kotlinx.coroutines.MainScope()

    fun loadGame(gameId: Long) {
        scope.launch {
            game = gameRepository.getGameById(gameId).first()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreboardScreen(
    gameId: Long,
    onBack: () -> Unit,
    viewModel: ScoreboardViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    LaunchedEffect(gameId) {
        viewModel.loadGame(gameId)
    }

    val game = viewModel.game

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
                    text = "No rounds played yet",
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
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = g.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${g.rounds.size} ${stringResource(R.string.round_label, 0).replace("0", "").trim()} · ${g.players.size} Players",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // === Player legend pills ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                g.players.forEachIndexed { idx, player ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = playerColors[idx].copy(alpha = 0.15f),
                        modifier = Modifier.border(1.dp, playerColors[idx], RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(playerColors[idx])
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = player.name,
                                color = playerColors[idx],
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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
                    modifier = Modifier
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
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
                                text = player.name.take(6),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = playerColors[idx],
                                modifier = Modifier.width(56.dp),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            if (idx < g.players.size - 1) {
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // Cumulative total column
                        Text(
                            text = "∑",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB300),
                            modifier = Modifier.width(44.dp),
                            textAlign = TextAlign.Center
                        )
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
                            // Round number with trophy for winner
                            Box(
                                modifier = Modifier.width(44.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (round.winnerPlayerName != null) {
                                    Text(
                                        text = "🏆",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            // Round label below
                            Text(
                                text = "R${roundIdx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.width(44.dp).offset(y = 8.dp),
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.width(4.dp))

                            // Player scores
                            g.players.forEachIndexed { pIdx, player ->
                                val score = round.scores[player.name] ?: 0
                                val isWinner = round.winnerPlayerName == player.name
                                val cumTotal = cumulativeByPlayer[player.name]?.getOrNull(roundIdx)

                                Box(
                                    modifier = Modifier.width(56.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isWinner) {
                                        // Gold badge for round winner
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFFB300).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "$score",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFB300),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "$score",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.8f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    // Cumulative tiny hint
                                    cumTotal?.let { cum ->
                                        Text(
                                            text = "$cum",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.3f),
                                            modifier = Modifier.offset(y = 18.dp),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                if (pIdx < g.players.size - 1) {
                                    Spacer(Modifier.width(4.dp))
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            // Round total sum
                            val roundSum = round.scores.values.sum()
                            Text(
                                text = "$roundSum",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300).copy(alpha = 0.7f),
                                modifier = Modifier.width(44.dp),
                                textAlign = TextAlign.Center
                            )
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
                                modifier = Modifier.width(56.dp),
                                textAlign = TextAlign.Center
                            )
                            if (idx < g.players.size - 1) {
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB300).copy(alpha = 0.4f),
                            modifier = Modifier.width(44.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // === Leaderboard card ===
            val ranked = g.players
                .map { p -> p.name to (cumulativeByPlayer[p.name]?.lastOrNull() ?: 0) }
                .sortedBy { it.second }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F3460))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Leaderboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(12.dp))
                    ranked.forEachIndexed { idx, (name, total) ->
                        val playerIdx = g.players.indexOfFirst { it.name == name }
                        val color = playerColors.getOrElse(playerIdx) { Color.White }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(
                                    Color.White.copy(alpha = if (idx == 0) 0.08f else 0.03f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rank badge
                            Surface(
                                shape = CircleShape,
                                color = when (idx) {
                                    0 -> Color(0xFFFFB300)
                                    1 -> Color(0xFFB0BEC5)
                                    2 -> Color(0xFF8D6E63)
                                    else -> Color.White.copy(alpha = 0.2f)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal,
                                color = color,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$total",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

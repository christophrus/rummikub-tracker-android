package com.lorus.rummikubtracker.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lorus.rummikubtracker.R
import com.lorus.rummikubtracker.data.repository.GameRepository
import com.lorus.rummikubtracker.domain.engine.AudioEngine
import com.lorus.rummikubtracker.domain.engine.TimerEngine
import com.lorus.rummikubtracker.domain.model.*
import com.lorus.rummikubtracker.domain.usecase.GameManager
import com.lorus.rummikubtracker.domain.usecase.ScoreValidator
import com.lorus.rummikubtracker.ui.components.AnalogClock
import com.lorus.rummikubtracker.ui.components.ConfettiEffect
import com.lorus.rummikubtracker.ui.components.PlayerAvatar
import com.lorus.rummikubtracker.ui.components.PlayerCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActiveGameUiState(
    val game: Game? = null,
    val showEndGameDialog: Boolean = false,
    val showWinnerDeclaration: Boolean = false,
    val winnerPlayerName: String? = null,
    val scores: Map<String, String> = emptyMap(),
    val validationError: String? = null,
    val isScanning: Set<String> = emptySet()
)

@HiltViewModel
class ActiveGameViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val gameManager: GameManager,
    val timerEngine: TimerEngine,
    private val audioEngine: AudioEngine
) : androidx.lifecycle.ViewModel() {

    var uiState by mutableStateOf(ActiveGameUiState())
        private set

    private val scope = kotlinx.coroutines.MainScope()
    private var gameId: Long = 0

    fun loadGame(id: Long) {
        gameId = id
        scope.launch {
            // Get initial game to configure timer/audio once
            val initialGame = gameRepository.getGameById(id).first() ?: return@launch
            timerEngine.configure(initialGame.timerDuration.toLong(), initialGame.maxExtensions)
            audioEngine.setTtsLanguage(initialGame.ttsLanguage)
            audioEngine.initialize()

            // Collect subsequent changes for UI only — don't reconfigure timer
            gameRepository.getGameById(id).collect { game ->
                game?.let { g ->
                    uiState = uiState.copy(game = g)
                }
            }
        }
    }

    fun toggleTimer() {
        timerEngine.toggle()
    }

    fun resetTimer() {
        timerEngine.reset()
    }

    fun extendTimer(): Boolean {
        val game = uiState.game ?: return false
        val currentPlayer = game.players.getOrNull(game.currentPlayerIndex) ?: return false

        scope.launch {
            val success = gameManager.incrementExtensions(gameId, currentPlayer.name)
            if (success) {
                timerEngine.extend()
                audioEngine.playExtend()
            }
        }
        return true
    }

    fun declareWinner() {
        val game = uiState.game ?: return
        val currentPlayer = game.players.getOrNull(game.currentPlayerIndex) ?: return
        timerEngine.stop()
        audioEngine.playVictory()
        audioEngine.announcePlayer(currentPlayer.name)
        uiState = uiState.copy(
            showWinnerDeclaration = true,
            winnerPlayerName = currentPlayer.name,
            scores = game.players.associate { it.name to "" }
        )
    }

    fun skipPlayer() {
        val game = uiState.game ?: return
        val nextIndex = (game.currentPlayerIndex + 1) % game.players.size
        val nextPlayer = game.players[nextIndex]

        // Update local state immediately so UI reflects the new player
        val updatedGame = game.copy(currentPlayerIndex = nextIndex)
        uiState = uiState.copy(game = updatedGame)

        // Timer operations must happen synchronously on the click
        timerEngine.reset()
        timerEngine.start()

        // DB update and audio run in background
        scope.launch {
            gameRepository.updateGame(updatedGame)
            audioEngine.playTurnNotification()
            audioEngine.announcePlayer(nextPlayer.name)
        }
    }

    fun updateScore(playerName: String, score: String) {
        uiState = uiState.copy(
            scores = uiState.scores + (playerName to score),
            validationError = null
        )
    }

    fun saveRound() {
        val game = uiState.game ?: return
        val scoreMap = uiState.scores.mapValues { it.value.toIntOrNull() ?: -1 }

        // Validate
        if (scoreMap.values.any { it < 0 }) {
            uiState = uiState.copy(validationError = "all_scores_required")
            return
        }

        val validation = ScoreValidator.validate(scoreMap, game.players.map { it.name })
        if (!validation.isValid) {
            uiState = uiState.copy(validationError = validation.errorMessage ?: "one_zero_required")
            return
        }

        val round = Round(
            gameId = gameId,
            roundNumber = game.currentRound,
            scores = scoreMap,
            winnerPlayerName = uiState.winnerPlayerName
        )

        // Clear winner declaration UI immediately
        uiState = uiState.copy(
            showWinnerDeclaration = false,
            winnerPlayerName = null,
            scores = emptyMap(),
            validationError = null
        )

        // Timer operations must happen synchronously
        timerEngine.reset()
        timerEngine.start()

        scope.launch {
            gameManager.saveRound(game, round)

            // Announce next player
            val updatedGame = gameRepository.getGameById(gameId).first()
            updatedGame?.let { g ->
                val nextPlayer = g.players.getOrNull(g.currentPlayerIndex)
                nextPlayer?.let { audioEngine.announcePlayer(it.name) }
            }

            audioEngine.playTurnNotification()
        }
    }

    fun endGame() {
        val game = uiState.game ?: return
        scope.launch {
            gameManager.endGame(game)
            timerEngine.stop()
        }
    }

    fun showEndGameDialog() {
        uiState = uiState.copy(showEndGameDialog = true)
    }

    fun dismissEndGameDialog() {
        uiState = uiState.copy(showEndGameDialog = false)
    }

    override fun onCleared() {
        super.onCleared()
        timerEngine.stop()
        audioEngine.destroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveGameScreen(
    gameId: Long,
    onGameEnded: () -> Unit,
    onBack: () -> Unit,
    viewModel: ActiveGameViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state = viewModel.uiState
    val timerState by viewModel.timerEngine.timerState.collectAsState()
    val remainingMs by viewModel.timerEngine.remainingMs.collectAsState()
    val extensionsUsed by viewModel.timerEngine.extensionsUsed.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(gameId) {
        viewModel.loadGame(gameId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.game?.name ?: "", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.round_number, (state.game?.currentRound ?: 0) + 1),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showEndGameDialog() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.end_game))
                    }
                }
            )
        }
    ) { padding ->
        val game = state.game ?: return@Scaffold

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.showWinnerDeclaration) {
                // Winner declaration view
                WinnerDeclarationView(
                    winnerName = state.winnerPlayerName ?: "",
                    players = game.players,
                    scores = state.scores,
                    validationError = state.validationError,
                    isScanning = state.isScanning,
                    onScoreChange = { name, score -> viewModel.updateScore(name, score) },
                    onSaveRound = { viewModel.saveRound() },
                    onScanTile = { /* API scan */ }
                )
            } else {
                // Active game view
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Timer section
                    TimerSection(
                        remainingMs = remainingMs,
                        // Use max of original and remaining so extended clock shows correct proportion
                        totalMs = maxOf(game.timerDuration.toLong(), remainingMs),
                        isPaused = timerState == TimerState.PAUSED,
                        isRunning = timerState == TimerState.RUNNING,
                        extensionsRemaining = game.maxExtensions - extensionsUsed,
                        currentPlayerName = game.players.getOrNull(game.currentPlayerIndex)?.name ?: "",
                        currentPlayerImage = game.players.getOrNull(game.currentPlayerIndex)?.imagePath,
                        onToggleTimer = { viewModel.toggleTimer() },
                        onResetTimer = { viewModel.resetTimer() },
                        onExtend = { viewModel.extendTimer() },
                        onDeclareWinner = { viewModel.declareWinner() },
                        onSkipPlayer = { viewModel.skipPlayer() }
                    )

                    HorizontalDivider()

                    // Score summary table
                    if (game.rounds.isNotEmpty()) {
                        ScoreSummaryTable(game = game)
                        HorizontalDivider()
                    }

                    // Player cards grid
                    Text(
                        text = stringResource(R.string.players),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(game.players) { index, player ->
                            PlayerCard(
                                name = player.name,
                                imagePath = player.imagePath,
                                isCurrentPlayer = index == game.currentPlayerIndex,
                                score = game.getPlayerTotal(player.name)
                            )
                        }
                    }

                    // End Game button
                    Button(
                        onClick = { viewModel.showEndGameDialog() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.end_game))
                    }
                }
            }
        }
    }

    // End Game dialog
    if (state.showEndGameDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissEndGameDialog() },
            title = { Text(stringResource(R.string.end_game)) },
            text = { Text(stringResource(R.string.confirm_end_game)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.endGame()
                            onGameEnded()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissEndGameDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TimerSection(
    remainingMs: Long,
    totalMs: Long,
    isPaused: Boolean,
    isRunning: Boolean,
    extensionsRemaining: Int,
    currentPlayerName: String,
    currentPlayerImage: String?,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onExtend: () -> Unit,
    onDeclareWinner: () -> Unit,
    onSkipPlayer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Analog Clock
        AnalogClock(
            remainingMs = remainingMs,
            totalMs = totalMs,
            isPaused = isPaused,
            size = 180.dp
        )

        Spacer(Modifier.height(12.dp))

        // Timer controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleTimer) {
                Icon(
                    if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isRunning) stringResource(R.string.pause) else stringResource(R.string.play),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onResetTimer) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.reset),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // Extend button
            FilledTonalButton(
                onClick = onExtend,
                enabled = extensionsRemaining > 0
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.extensions_remaining, extensionsRemaining)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Current player card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerAvatar(
                    name = currentPlayerName,
                    imagePath = currentPlayerImage,
                    size = 48
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentPlayerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                // Winner button
                IconButton(
                    onClick = onDeclareWinner,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = stringResource(R.string.declare_winner),
                        tint = Color(0xFFFFB300)
                    )
                }
                // Skip button
                IconButton(
                    onClick = onSkipPlayer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.skip),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun WinnerDeclarationView(
    winnerName: String,
    players: List<Player>,
    scores: Map<String, String>,
    validationError: String?,
    isScanning: Set<String>,
    onScoreChange: (String, String) -> Unit,
    onSaveRound: () -> Unit,
    onScanTile: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Confetti background
        ConfettiEffect()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Trophy icon and winner name
            Text(
                text = "🏆",
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center
            )

            Text(
                text = stringResource(R.string.winner),
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFFFB300),
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF8E1)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerAvatar(name = winnerName, size = 64)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = winnerName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Score entry form
            Text(
                text = stringResource(R.string.enter_scores),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            players.forEach { player ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = player.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    OutlinedTextField(
                        value = scores[player.name] ?: "",
                        onValueChange = { onScoreChange(player.name, it) },
                        modifier = Modifier.width(120.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(stringResource(R.string.score)) },
                        isError = false
                    )

                    Spacer(Modifier.width(8.dp))

                    IconButton(onClick = { onScanTile(player.name) }) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = stringResource(R.string.scan_tile),
                            tint = if (player.name in isScanning)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Validation error
            validationError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Save Round button
            Button(
                onClick = onSaveRound,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = scores.values.all { it.isNotBlank() }
            ) {
                Text(
                    text = stringResource(R.string.save_round),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun ScoreSummaryTable(game: Game) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        // Header row
        Row {
            // Round # column
            Text(
                text = "#",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )
            game.players.forEach { player ->
                Text(
                    text = player.name.take(6),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(55.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Score rows
        game.rounds.forEach { round ->
            Row {
                Text(
                    text = "R${round.roundNumber + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center
                )
                game.players.forEach { player ->
                    val score = round.scores[player.name] ?: 0
                    val isWinner = round.winnerPlayerName == player.name
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                        color = if (isWinner) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(55.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Cumulative totals
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        Row {
            Text(
                text = stringResource(R.string.total),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )
            game.players.forEach { player ->
                Text(
                    text = "${game.getPlayerTotal(player.name)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(55.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

package com.lorus.rummikubtracker.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lorus.rummikubtracker.R
import com.lorus.rummikubtracker.counter.data.local.AppDatabase
import com.lorus.rummikubtracker.counter.data.repository.HistoryRepository
import com.lorus.rummikubtracker.counter.ml.ImagePreprocessor
import com.lorus.rummikubtracker.counter.ml.NmsProcessor
import com.lorus.rummikubtracker.counter.ml.OrientationDetector
import com.lorus.rummikubtracker.counter.ml.OrientationPreprocessor
import com.lorus.rummikubtracker.counter.ml.YoloDetector
import com.lorus.rummikubtracker.data.local.datastore.PreferencesDataStore
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    val isScanning: Set<String> = emptySet(),
    val showDurationDropdown: Boolean = false,
    val scrollLocked: Boolean = false,
    val scannedPlayerName: String? = null,
    val showScanSourceDialog: Boolean = false
)

@HiltViewModel
class ActiveGameViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val gameManager: GameManager,
    val timerEngine: TimerEngine,
    private val audioEngine: AudioEngine,
    private val preferencesDataStore: PreferencesDataStore,
    @ApplicationContext private val appContext: android.content.Context
) : androidx.lifecycle.ViewModel() {

    var uiState by mutableStateOf(ActiveGameUiState())
        private set

    private val scope = kotlinx.coroutines.MainScope()
    private var gameId: Long = 0
    private var gameLoaded = false

    fun loadGame(id: Long) {
        val isNewGame = id != gameId || !gameLoaded
        gameId = id
        gameLoaded = true
        // Wire timer callbacks (safe to set multiple times)
        timerEngine.onTickSound = { audioEngine.playTick() }
        timerEngine.onTimeUp = { skipPlayer() }
        scope.launch {
            // Get initial game to configure timer/audio once per game load
            val initialGame = gameRepository.getGameById(id).first() ?: return@launch
            if (isNewGame) {
                val currentPlayerExtUsed = initialGame.players
                    .getOrNull(initialGame.currentPlayerIndex)
                    ?.extensionsUsed ?: 0
                timerEngine.configure(
                    initialGame.timerDuration.toLong(),
                    initialGame.maxExtensions,
                    currentPlayerExtUsed
                )
                audioEngine.setTtsLanguage(initialGame.ttsLanguage)
                audioEngine.initialize()
            }

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

    fun toggleScrollLock() {
        uiState = uiState.copy(scrollLocked = !uiState.scrollLocked)
    }

    fun toggleDurationDropdown() {
        uiState = uiState.copy(showDurationDropdown = !uiState.showDurationDropdown)
    }

    fun dismissDurationDropdown() {
        uiState = uiState.copy(showDurationDropdown = false)
    }

    fun setTimerDuration(ms: Int) {
        val game = uiState.game ?: return
        timerEngine.configure(ms.toLong(), game.maxExtensions, currentExtensionsUsed = timerEngine.extensionsUsed.value)
        uiState = uiState.copy(showDurationDropdown = false)
    }

    fun extendTimer() {
        // Extend must happen synchronously so the UI updates immediately
        if (!timerEngine.extend()) return

        val game = uiState.game ?: return
        val currentPlayer = game.players.getOrNull(game.currentPlayerIndex) ?: return

        // DB persistence runs in background
        scope.launch {
            gameManager.incrementExtensions(gameId, currentPlayer.name)
            audioEngine.playExtend()
        }
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
            scores = game.players.associate { it.name to if (it.name == currentPlayer.name) "0" else "" }
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
        timerEngine.setExtensionsUsed(nextPlayer.extensionsUsed)
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

            // Restore next player's extension count
            val updatedGame = gameRepository.getGameById(gameId).first()
            updatedGame?.let { g ->
                val nextPlayer = g.players.getOrNull(g.currentPlayerIndex)
                nextPlayer?.let {
                    timerEngine.setExtensionsUsed(it.extensionsUsed)
                    audioEngine.announcePlayer(it.name)
                }
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

    // --- Tile Scanning ---
    private val yoloDetector: YoloDetector by lazy { YoloDetector.getInstance(appContext) }
    private val orientationDetector: OrientationDetector by lazy { OrientationDetector.getInstance(appContext) }
    private val historyRepository: HistoryRepository by lazy {
        val db = AppDatabase.getInstance(appContext)
        HistoryRepository(db.analysisDao(), appContext)
    }

    fun startTileScan(playerName: String) {
        uiState = uiState.copy(
            scannedPlayerName = playerName,
            showScanSourceDialog = true
        )
    }

    fun onDialogLaunchCamera() {
        uiState = uiState.copy(showScanSourceDialog = false)
    }

    fun onDialogLaunchGallery() {
        uiState = uiState.copy(showScanSourceDialog = false)
    }

    fun cancelScanDialog() {
        uiState = uiState.copy(showScanSourceDialog = false, scannedPlayerName = null)
    }

    fun onImageCaptured(bitmap: Bitmap) {
        val playerName = uiState.scannedPlayerName ?: return
        uiState = uiState.copy(
            isScanning = uiState.isScanning + playerName,
            scannedPlayerName = null
        )

        kotlinx.coroutines.MainScope().launch(Dispatchers.Default) {
            var safeBitmap: Bitmap? = null
            var orientedBitmap: Bitmap? = null
            try {
                val confThreshold = preferencesDataStore.preferences.first().confidenceThreshold
                safeBitmap = ImagePreprocessor.downscaleIfNeeded(bitmap, maxDimension = 1280)
                val orientationInput = OrientationPreprocessor.preprocess(safeBitmap!!)
                val detectedDegrees = orientationDetector.detect(orientationInput)
                val correctionDegrees = orientationDetector.correctionDegrees(detectedDegrees)
                orientedBitmap = if (correctionDegrees != 0) {
                    val rotated = ImagePreprocessor.rotateBitmap(safeBitmap!!, correctionDegrees)
                    if (rotated != safeBitmap) safeBitmap!!.recycle()
                    rotated
                } else safeBitmap

                val (inputArray, letterboxInfo) = ImagePreprocessor.preprocess(orientedBitmap!!)
                val rawOutput = yoloDetector.detect(inputArray)
                val tiles = NmsProcessor.postProcess(
                    rawOutput, letterboxInfo, orientedBitmap.width, orientedBitmap.height,
                    confThreshold = confThreshold
                )
                val totalScore = tiles.sumOf { if (it.isJoker) 20 else (it.number ?: 0) }
                val elapsed = 0L // processing time not critical here
                val result = com.lorus.rummikubtracker.counter.model.AnalysisResult(
                    tiles = tiles,
                    totalScore = totalScore,
                    tileCount = tiles.size,
                    processingTimeMs = elapsed
                )

                // Save to counter history (thumbnail to save memory)
                try {
                    val thumbnail = Bitmap.createScaledBitmap(orientedBitmap!!, 400, 400, true)
                    historyRepository.saveResult(result, tiles, thumbnail)
                    thumbnail.recycle()
                } catch (_: Exception) { }

                uiState = uiState.copy(
                    isScanning = uiState.isScanning - playerName,
                    scores = uiState.scores + (playerName to totalScore.toString()),
                    validationError = null
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isScanning = uiState.isScanning - playerName,
                    validationError = "Scan failed: ${e.message?.take(60) ?: "unknown"}"
                )
            }
        }
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
    val effectiveTotalMs by viewModel.timerEngine.effectiveTotalMs.collectAsState()
    val extensionsUsed by viewModel.timerEngine.extensionsUsed.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Gallery image picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            viewModel.onImageCaptured(bitmap)
        }
    }

    // Camera photo capture
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { viewModel.onImageCaptured(it) }
    }

    // Show scan source dialog when triggered
    if (state.showScanSourceDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelScanDialog() },
            title = { Text(stringResource(R.string.scan_tile)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.onDialogLaunchCamera()
                            cameraLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.take_photo), modifier = Modifier.weight(1f))
                    }
                    TextButton(
                        onClick = {
                            viewModel.onDialogLaunchGallery()
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.pick_gallery), modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.cancelScanDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LaunchedEffect(gameId) {
        viewModel.loadGame(gameId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            state.game?.name ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildString {
                                append(stringResource(R.string.round_label, (state.game?.currentRound ?: 0) + 1))
                                val elapsed = ((System.currentTimeMillis() - (state.game?.startTime ?: 0L)) / 1000).toInt()
                                append(" · ${elapsed / 60}m")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    onScanTile = { playerName -> viewModel.startTileScan(playerName) }
                )
            } else {
                // Active game view — redesigned layout
                val currentPlayer = game.players.getOrNull(game.currentPlayerIndex)
                val currentPlayerName = currentPlayer?.name ?: ""
                val currentPlayerImage = currentPlayer?.imagePath
                val elapsedSeconds = ((System.currentTimeMillis() - game.startTime) / 1000).toInt()
                val elapsedMin = elapsedSeconds / 60

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // === Player card (purple gradient) ===
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF5E35C2))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            PlayerAvatar(
                                name = currentPlayerName,
                                imagePath = currentPlayerImage,
                                size = 56
                            )
                            Spacer(Modifier.width(12.dp))
                            // Name
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.turn_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = currentPlayerName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            // Trophy button
                            IconButton(
                                onClick = { viewModel.declareWinner() },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = stringResource(R.string.declare_winner),
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            // Next Player button
                            IconButton(
                                onClick = { viewModel.skipPlayer() },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = stringResource(R.string.skip),
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // === Large centered timer — as wide as screen ===
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val clockSize = maxWidth - 32.dp
                        AnalogClock(
                            remainingMs = remainingMs,
                            totalMs = effectiveTotalMs,
                            isPaused = timerState == TimerState.PAUSED,
                            size = clockSize,
                            onClick = { viewModel.skipPlayer() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // === Extension button with badge ===
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        Button(
                            onClick = { viewModel.extendTimer() },
                            enabled = game.maxExtensions - extensionsUsed > 0,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1565C0),
                                disabledContainerColor = Color(0xFF9E9E9E),
                                disabledContentColor = Color.White.copy(alpha = 0.5f)
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.add_30_seconds),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        // Badge
                        val remaining = game.maxExtensions - extensionsUsed
                        Badge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 12.dp, y = (-10).dp),
                            containerColor = Color(0xFFE53935)
                        ) {
                            Text("$remaining", color = Color.White, style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // === Control row: play/pause, reset, skip, lock ===
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play/Pause
                        FilledIconButton(
                            onClick = { viewModel.toggleTimer() },
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                if (timerState == TimerState.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (timerState == TimerState.RUNNING) stringResource(R.string.pause) else stringResource(R.string.play),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        // Reset
                        FilledIconButton(
                            onClick = { viewModel.resetTimer() },
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.reset),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        // Lock/fullscreen
                        FilledIconButton(
                            onClick = { viewModel.toggleScrollLock() },
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (state.scrollLocked)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                if (state.scrollLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = stringResource(R.string.lock_screen),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // === Bottom: score table + end game ===
                    if (game.rounds.isNotEmpty()) {
                        ScoreSummaryTable(game = game)
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedButton(
                        onClick = { viewModel.showEndGameDialog() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.end_game))
                    }
                    Spacer(Modifier.height(8.dp))
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
                val isWinner = player.name == winnerName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .then(
                            if (isWinner) Modifier.background(
                                Color(0xFF4CAF50).copy(alpha = 0.15f),
                                MaterialTheme.shapes.medium
                            )
                            else Modifier
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = player.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                        color = if (isWinner) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
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

private fun formatDurationShort(ms: Int): String {
    return when (ms) {
        30_000 -> "30s"
        45_000 -> "45s"
        60_000 -> "1m"
        90_000 -> "1m 30s"
        120_000 -> "2m"
        180_000 -> "3m"
        300_000 -> "5m"
        else -> "${ms / 1000}s"
    }
}

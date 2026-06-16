package com.lorus.rummikubtracker.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import com.lorus.rummikubtracker.ui.components.ScrollIndicator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ActiveGameUiState(
    val game: Game? = null,
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

    fun loadGame(id: Long) {
        gameId = id
        timerEngine.onTickSound = { audioEngine.playTick() }
        timerEngine.onTimeUp = { skipPlayer() }
        scope.launch {
            val initialGame = gameRepository.getGameById(id).first() ?: return@launch
            // Configure only for truly new game or if timer was stopped
            if (timerEngine.timerState.value == TimerState.STOPPED) {
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
            } else if (timerEngine.isPaused()) {
                // Auto-resume timer when returning from main menu
                timerEngine.resume()
            }

            // Collect subsequent changes for UI only — don't reconfigure timer
            gameRepository.getGameById(id).collect { game ->
                game?.let { g ->
                    uiState = uiState.copy(game = g)
                }
            }
        }

        // Observe TTS language changes from settings and sync to game + AudioEngine
        scope.launch {
            preferencesDataStore.preferences.collect { prefs ->
                val game = uiState.game ?: return@collect
                if (prefs.ttsLanguage != game.ttsLanguage) {
                    audioEngine.setTtsLanguage(prefs.ttsLanguage)
                    val updated = game.copy(ttsLanguage = prefs.ttsLanguage)
                    gameRepository.updateGame(updated)
                    uiState = uiState.copy(game = updated)
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

        // Reset timer for new round, but keep paused — user starts manually
        timerEngine.reset()

        scope.launch {
            gameManager.saveRound(game, round)

            // Restore next player's extension count and updated maxExtensions
            val updatedGame = gameRepository.getGameById(gameId).first()
            updatedGame?.let { g ->
                timerEngine.setMaxExtensions(g.maxExtensions)
                val nextPlayer = g.players.getOrNull(g.currentPlayerIndex)
                nextPlayer?.let {
                    timerEngine.setExtensionsUsed(it.extensionsUsed)
                }
            }

            audioEngine.playTurnNotification()
            updatedGame?.let { g ->
                val nextPlayer = g.players.getOrNull(g.currentPlayerIndex)
                nextPlayer?.let {
                    audioEngine.announcePlayer(it.name)
                }
            }
        }
    }

    fun endGame() {
        val game = uiState.game ?: return
        scope.launch {
            gameManager.endGame(game)
            timerEngine.stop()
        }
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
                val startTime = System.currentTimeMillis()
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
                val elapsed = System.currentTimeMillis() - startTime
                val result = com.lorus.rummikubtracker.counter.model.AnalysisResult(
                    tiles = tiles,
                    totalScore = totalScore,
                    tileCount = tiles.size,
                    processingTimeMs = elapsed,
                    imageWidth = orientedBitmap!!.width,
                    imageHeight = orientedBitmap.height
                )

                // Save to counter history with original image
                try {
                    historyRepository.saveResult(result, tiles, orientedBitmap)
                } catch (_: Exception) { }

                uiState = uiState.copy(
                    isScanning = uiState.isScanning - playerName,
                    scores = uiState.scores + (playerName to totalScore.toString()),
                    validationError = null
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isScanning = uiState.isScanning - playerName,
                    validationError = e.message?.let { "Scan failed: ${it.take(60)}" } ?: "scan_failed"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Pause timer on back navigation — singleton persists, auto-resumes on return
        if (timerEngine.isRunning()) {
            timerEngine.pause()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveGameScreen(
    gameId: Long,
    onGameEnded: () -> Unit,
    onBack: () -> Unit,
    onScoreboard: (Long) -> Unit = {},
    viewModel: ActiveGameViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state = viewModel.uiState
    val timerState by viewModel.timerEngine.timerState.collectAsState()
    val remainingMs by viewModel.timerEngine.remainingMs.collectAsState()
    val effectiveTotalMs by viewModel.timerEngine.effectiveTotalMs.collectAsState()
    val extensionsUsed by viewModel.timerEngine.extensionsUsed.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var showClockHint by remember { mutableStateOf(true) }

    // Gallery image picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            viewModel.onImageCaptured(bitmap)
        }
    }

    // Camera photo capture — use TakePicture for full resolution
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri ->
                try {
                    val bitmap = BitmapFactory.decodeStream(
                        context.contentResolver.openInputStream(uri)
                    )
                    if (bitmap != null) {
                        viewModel.onImageCaptured(bitmap)
                    }
                } catch (_: Exception) { }
                try { File(uri.path!!).delete() } catch (_: Exception) { }
            }
        }
    }

    // Helper to create a temp URI and launch the camera
    fun launchCameraWithUri() {
        val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        photoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        photoUri?.let { cameraLauncher.launch(it) }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraWithUri()
        } else {
            Toast.makeText(context, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
        }
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
                            val hasPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (hasPermission) {
                                launchCameraWithUri()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
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
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            state.game?.name ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = buildString {
                                append(stringResource(R.string.round_label, (state.game?.currentRound ?: 0) + 1))
                                val elapsed = ((System.currentTimeMillis() - (state.game?.startTime ?: 0L)) / 1000).toInt()
                                val mins = elapsed / 60
                                val dur = if (mins < 60) stringResource(R.string.duration_minutes, mins) else stringResource(R.string.duration_hours, mins / 60, mins % 60)
                                append(" · $dur")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
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
                            // Trophy button — declare winner
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(Color(0xFFFFB300).copy(alpha = 0.2f))
                                    .border(2.dp, Color(0xFFFFB300), MaterialTheme.shapes.medium)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { viewModel.declareWinner() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = stringResource(R.string.declare_winner),
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
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

                    // === Large centered timer + touch hint overlay ===
                    // Both clock and hint share one BoxWithConstraints so the ripple
                    // canvas is drawn inside the clock area — no parent-clipping issues.
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val clockSize = maxWidth - 32.dp

                        // Clock
                        AnalogClock(
                            remainingMs = remainingMs,
                            totalMs = effectiveTotalMs,
                            isPaused = timerState == TimerState.PAUSED,
                            size = clockSize,
                            onClick = {
                                showClockHint = false
                                viewModel.skipPlayer()
                            },
                            modifier = Modifier.align(Alignment.Center)
                        )

                        // Touch hint — overlaid on the clock so ripple draws inside it
                        if (showClockHint) {
                            val transition = rememberInfiniteTransition(label = "hintTap")
                            val fingerPhase by transition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(4000),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "fingerPhase"
                            )

                            // Timing: up 0..0.22 | hold 0.22..0.62 | down 0.62..0.85 | pause 0.85..1.0
                            val fingerY = when {
                                fingerPhase < 0.22f -> fingerPhase / 0.22f
                                fingerPhase < 0.62f -> 1f
                                fingerPhase < 0.85f -> 1f - (fingerPhase - 0.62f) / 0.23f
                                else -> 0f
                            }

                            val rippleProgress = if (fingerPhase in 0.22f..0.62f) {
                                (fingerPhase - 0.22f) / 0.40f
                            } else 0f

                            // Finger travels from 96% → 74% of clockSize from the top (in Dp)
                            val fingerPosY = clockSize * (0.96f - 0.22f * fingerY)

                            // Ripple canvas covers the full clock face — no offsets, no clipping
                            Canvas(
                                modifier = Modifier
                                    .size(maxWidth, clockSize)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        showClockHint = false
                                        viewModel.skipPlayer()
                                    }
                            ) {
                                if (rippleProgress > 0f) {
                                    val tipX = size.width / 2f
                                    val tipY = clockSize.toPx() * (0.96f - 0.22f * fingerY)
                                    val maxR = 90.dp.toPx()
                                    val r    = maxR * rippleProgress
                                    val fade = 1f - rippleProgress * 0.55f
                                    // Inner filled pulse
                                    drawCircle(Color.White.copy(alpha = 0.30f), r * 0.28f, Offset(tipX, tipY))
                                    // Ring 1
                                    drawCircle(Color.White.copy(alpha = 0.20f * fade), r * 0.55f, Offset(tipX, tipY), style = Stroke(2.dp.toPx()))
                                    // Ring 2
                                    drawCircle(Color.White.copy(alpha = 0.12f * fade), r * 0.78f, Offset(tipX, tipY), style = Stroke(1.5f.dp.toPx()))
                                    // Outer ring
                                    drawCircle(Color.White.copy(alpha = 0.07f * fade), r,        Offset(tipX, tipY), style = Stroke(1.dp.toPx()))
                                }
                            }

                            // Finger emoji — same position mapping as the Canvas above
                            Text(
                                text = "👆",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = fingerPosY)
                            )


                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (showClockHint) {
                        Text(
                            text = stringResource(R.string.touch_for_next_player),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // === Extension button with badge ===
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        Button(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.extendTimer()
                            },
                            enabled = game.maxExtensions - extensionsUsed > 0,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E399F),
                                disabledContainerColor = Color(0xFF9E9E9E),
                                disabledContentColor = Color.White.copy(alpha = 0.5f)
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color(0xFF4195ED))
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

                    Spacer(Modifier.weight(1f))

                    // === Control row: play/pause, reset ===
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play/Pause
                        FilledIconButton(
                            onClick = { viewModel.toggleTimer() },
                            modifier = Modifier.size(72.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                if (timerState == TimerState.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (timerState == TimerState.RUNNING) stringResource(R.string.pause) else stringResource(R.string.play),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        // Reset
                        FilledIconButton(
                            onClick = { viewModel.resetTimer() },
                            modifier = Modifier.size(72.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.reset),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // === Bottom: Scoreboard button + end game ===
                    if (game.rounds.isNotEmpty()) {
                        Button(
                            onClick = { onScoreboard(gameId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E232F)
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF3788C3))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.scoreboard),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFC9CCCF)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
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

        val scrollState = rememberScrollState()
        val canScrollForward by remember { derivedStateOf { scrollState.canScrollForward } }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
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
                modifier = Modifier.widthIn(min = 200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val winnerPlayer = players.find { it.name == winnerName }
                    PlayerAvatar(name = winnerName, imagePath = winnerPlayer?.imagePath, size = 64)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = winnerName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
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

                    IconButton(
                        onClick = { onScanTile(player.name) },
                        enabled = player.name !in isScanning
                    ) {
                        if (player.name in isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = stringResource(R.string.scan_tile),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Validation error
            validationError?.let { error ->
                Text(
                    text = stringResource(getValidationErrorResId(error)),
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

        // Scroll indicator
        ScrollIndicator(
            canScrollForward = canScrollForward,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
                    text = "${stringResource(R.string.round_abbr)}${round.roundNumber + 1}",
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

@androidx.annotation.StringRes
private fun getValidationErrorResId(key: String): Int {
    return when (key) {
        "all_scores_required" -> R.string.all_scores_required
        "one_zero_required" -> R.string.one_zero_required
        "scan_failed" -> R.string.scan_failed
        else -> R.string.error_occurred
    }
}

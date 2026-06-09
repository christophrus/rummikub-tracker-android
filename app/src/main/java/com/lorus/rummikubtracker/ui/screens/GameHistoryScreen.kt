package com.lorus.rummikubtracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
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
    val context = LocalContext.current

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
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Screenshot button
                                        IconButton(onClick = {
                                            takeScreenshot(context, game)
                                        }) {
                                            Icon(
                                                Icons.Default.CameraAlt,
                                                contentDescription = "Screenshot",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        // Delete button
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

private fun takeScreenshot(context: android.content.Context, game: Game) {
    try {
        val density = context.resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val colWidth = (70 * density).toInt()
        val rowHeight = (36 * density).toInt()
        val headerHeight = (40 * density).toInt()
        val roundLabelWidth = (60 * density).toInt()
        val titleHeight = (52 * density).toInt()
        val textSize = 12f * density
        val titleSize = 16f * density
        val smallSize = 10f * density

        val numCols = 1 + game.players.size // round label + players
        val numRows = 1 + game.rounds.size + 1 // header + rounds + total
        val imgWidth = roundLabelWidth + colWidth * game.players.size + pad * 2
        val imgHeight = titleHeight + headerHeight + rowHeight * (game.rounds.size + 1) + pad * 3

        val bitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Background
        val bgPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#1A1A2E")
            it.style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, imgWidth.toFloat(), imgHeight.toFloat(), bgPaint)

        // Title
        val titlePaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.WHITE
            it.textSize = titleSize.toFloat()
            it.isAntiAlias = true
            it.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText(game.name, pad.toFloat(), pad + titleSize, titlePaint)
        val subPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#B0B0B0")
            it.textSize = smallSize.toFloat()
            it.isAntiAlias = true
        }
        canvas.drawText("${game.rounds.size} rounds · ${game.players.size} players", pad.toFloat(), pad + titleSize + smallSize + 4, subPaint)

        val tableTop = pad.toFloat() + titleHeight

        // Table background card
        val cardPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#0F3460")
            it.style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRoundRect(pad.toFloat(), tableTop, (imgWidth - pad).toFloat(), (imgHeight - pad).toFloat(), 16f * density, 16f * density, cardPaint)

        val headerPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#B0B0B0")
            it.textSize = smallSize.toFloat()
            it.isAntiAlias = true
            it.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val cellPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.WHITE
            it.textSize = textSize.toFloat()
            it.isAntiAlias = true
        }
        val goldPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#FFB300")
            it.textSize = textSize.toFloat()
            it.isAntiAlias = true
            it.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val totalPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#FFB300")
            it.textSize = textSize.toFloat()
            it.isAntiAlias = true
            it.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val linePaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#33FFFFFF")
            it.strokeWidth = 1f
        }
        val rowBgPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#08FFFFFF")
            it.style = android.graphics.Paint.Style.FILL
        }

        val startY = tableTop + pad
        val startX = pad.toFloat() + pad

        // Header
        canvas.drawText("#", startX + roundLabelWidth / 2 - cellPaint.measureText("#") / 2, startY + headerHeight * 0.65f, headerPaint)
        var x = startX + roundLabelWidth
        game.players.forEach { player ->
            val text = player.name.take(8)
            canvas.drawText(text, x + colWidth / 2 - cellPaint.measureText(text) / 2, startY + headerHeight * 0.65f, headerPaint)
            x += colWidth
        }

        // Header line
        canvas.drawLine(startX, startY + headerHeight, startX + roundLabelWidth + colWidth * game.players.size, startY + headerHeight, linePaint)

        // Cumulative tracking
        val cumulative = mutableMapOf<String, Int>()
        game.players.forEach { cumulative[it.name] = 0 }

        // Round rows
        game.rounds.forEachIndexed { rIdx, round ->
            val rowY = startY + headerHeight + rIdx * rowHeight
            // Alternating row background
            if (rIdx % 2 == 0) {
                canvas.drawRect(startX, rowY, startX + roundLabelWidth + colWidth * game.players.size, rowY + rowHeight, rowBgPaint)
            }

            // Round label
            canvas.drawText("R${rIdx + 1}", startX + roundLabelWidth / 2 - cellPaint.measureText("R${rIdx + 1}") / 2, rowY + rowHeight * 0.65f, cellPaint)

            var px = startX + roundLabelWidth
            game.players.forEach { player ->
                val score = round.scores[player.name] ?: 0
                cumulative[player.name] = (cumulative[player.name] ?: 0) + score
                val isWinner = round.winnerPlayerName == player.name
                val paint = if (isWinner) goldPaint else cellPaint
                val displayText = if (isWinner) "0 🏆" else "$score"
                canvas.drawText(displayText, px + colWidth / 2 - paint.measureText(displayText) / 2, rowY + rowHeight * 0.65f, paint)
                px += colWidth
            }

            if (rIdx < game.rounds.size - 1) {
                canvas.drawLine(startX, rowY + rowHeight, startX + roundLabelWidth + colWidth * game.players.size, rowY + rowHeight, linePaint)
            }
        }

        // Totals row
        val totalY = startY + headerHeight + game.rounds.size * rowHeight
        val totalBgPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#10FFB300")
            it.style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(startX, totalY, startX + roundLabelWidth + colWidth * game.players.size, totalY + rowHeight, totalBgPaint)

        val goldLinePaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#FFB300")
            it.strokeWidth = 2f
        }
        canvas.drawLine(startX, totalY, startX + roundLabelWidth + colWidth * game.players.size, totalY, goldLinePaint)

        val totalLabel = "Total"
        canvas.drawText(totalLabel, startX + roundLabelWidth / 2 - totalPaint.measureText(totalLabel) / 2, totalY + rowHeight * 0.65f, totalPaint)

        var tx = startX + roundLabelWidth
        game.players.forEach { player ->
            val total = cumulative[player.name] ?: 0
            canvas.drawText("$total", tx + colWidth / 2 - totalPaint.measureText("$total") / 2, totalY + rowHeight * 0.65f, totalPaint)
            tx += colWidth
        }

        // Save to gallery
        val filename = "Rummikub_${game.name.replace(" ", "_")}_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Rummikub")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        } else {
            val dir = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Rummikub"
            )
            dir.mkdirs()
            val file = java.io.File(dir, filename)
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }

        bitmap.recycle()
        Toast.makeText(context, "Scoreboard saved to Pictures/Rummikub", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Screenshot failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

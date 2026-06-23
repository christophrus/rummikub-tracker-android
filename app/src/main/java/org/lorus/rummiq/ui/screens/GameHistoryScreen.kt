package org.lorus.rummiq.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import org.lorus.rummiq.R
import org.lorus.rummiq.data.repository.GameRepository
import org.lorus.rummiq.domain.model.Game
import org.lorus.rummiq.ui.components.ScrollIndicator
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

    // Edit score dialog state
    var editingScore by mutableStateOf<EditingScore?>(null)

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

    fun updateScore(roundId: Long, playerName: String, newScore: Int) {
        scope.launch {
            gameRepository.updateRoundScore(roundId, playerName, newScore)
            editingScore = null
            // Refresh games list
            gameRepository.getCompletedGames().collect { completed ->
                games = completed
            }
        }
    }
}

data class EditingScore(
    val roundId: Long,
    val playerName: String,
    val currentScore: Int,
    val gameId: Long
)

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
            val lazyListState = rememberLazyListState()
            val canScrollForward by remember { derivedStateOf { lazyListState.canScrollForward } }

            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize()
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
                                    // Duration
                                    val duration = game.endTime?.let { end ->
                                        val elapsed = end - game.startTime
                                        val mins = (elapsed / 60000).toInt()
                                        if (mins < 60) "${mins}m" else "${mins / 60}h ${mins % 60}m"
                                    }
                                    duration?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
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
                                                        text = "${stringResource(R.string.round_abbr)}${round.roundNumber + 1}",
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
                                                        // Highlight lowest score in green
                                                        val minScore = round.scores.values.minOrNull() ?: 0
                                                        val isBest = score == minScore && round.scores.size > 1
                                                        Text(
                                                            text = "$score",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (isBest) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                                                            modifier = Modifier
                                                                .clickable {
                                                                    viewModel.editingScore = EditingScore(
                                                                        roundId = round.id,
                                                                        playerName = player.name,
                                                                        currentScore = score,
                                                                        gameId = game.id
                                                                    )
                                                                }
                                                                .padding(vertical = 2.dp)
                                                        )
                                                    }
                                                    val playerTotal = game.getPlayerTotal(player.name)
                                                    val allTotals = game.players.map { game.getPlayerTotal(it.name) }
                                                    val minTotal = allTotals.minOrNull() ?: 0
                                                    val isBestTotal = playerTotal == minTotal && game.players.size > 1
                                                    Text(
                                                        text = "$playerTotal",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isBestTotal) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
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
                                        // Share button
                                        IconButton(onClick = {
                                            takeScreenshot(context, game)
                                        }) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = stringResource(R.string.share_label),
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

                ScrollIndicator(
                    canScrollForward = canScrollForward,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
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

    // Edit score dialog
    viewModel.editingScore?.let { edit ->
        var scoreText by remember { mutableStateOf(edit.currentScore.toString()) }
        AlertDialog(
            onDismissRequest = { viewModel.editingScore = null },
            title = { Text(stringResource(R.string.edit_score)) },
            text = {
                Column {
                    Text(
                        text = "${edit.playerName} · ${stringResource(R.string.round_label, edit.roundId)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = scoreText,
                        onValueChange = { scoreText = it.filter { c -> c.isDigit() || c == '-' } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        label = { Text(stringResource(R.string.score)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newScore = scoreText.toIntOrNull()
                        if (newScore != null) {
                            viewModel.updateScore(edit.roundId, edit.playerName, newScore)
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.editingScore = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private fun takeScreenshot(context: android.content.Context, game: Game) {
    try {
        val density = context.resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val rowHeight = (44 * density).toInt()
        val headerHeight = (44 * density).toInt()
        val roundLabelWidth = (52 * density).toInt()
        val titleHeight = (76 * density).toInt()
        val textSize = 14f * density
        val titleSize = 18f * density
        val smallSize = 11f * density

        // Calculate column width dynamically based on longest content
        val measurePaint = android.graphics.Paint().also { it.textSize = textSize }
        val boldMeasurePaint = android.graphics.Paint().also {
            it.textSize = textSize
            it.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val minColWidth = (72 * density).toInt()
        val maxColWidth = (120 * density).toInt()
        val colWidth = game.players.fold(minColWidth) { max, player ->
            val nameW = measurePaint.measureText(player.name).toInt() + (8 * density).toInt()
            val maxScore = game.rounds.maxOfOrNull { it.scores[player.name]?.toString()?.let { s -> measurePaint.measureText(s).toInt() } ?: 0 } ?: 0
            val totalW = boldMeasurePaint.measureText("${game.getPlayerTotal(player.name)}").toInt()
            maxOf(max, nameW, maxScore, totalW).coerceAtMost(maxColWidth)
        }
        val tableContentWidth = roundLabelWidth + colWidth * game.players.size
        val imgWidth = tableContentWidth + pad * 2
        val imgHeight = titleHeight + headerHeight + rowHeight * (game.rounds.size + 1) + pad * 4

        val bitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Background gradient
        val bgPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#16213E")
            it.style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, imgWidth.toFloat(), imgHeight.toFloat(), bgPaint)

        // Title
        val titlePaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.WHITE
            it.textSize = titleSize
            it.isAntiAlias = true
            it.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val titleX = (imgWidth / 2f) - titlePaint.measureText(game.name) / 2f
        canvas.drawText(game.name, titleX, pad + titleSize, titlePaint)

        val subPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#8899AA")
            it.textSize = smallSize
            it.isAntiAlias = true
        }
        val subText = buildString {
            append("${game.rounds.size} ")
            append(context.getString(R.string.rounds_label, 0).substringBefore(":"))
            append(" · ${game.players.size} ")
            append(context.getString(R.string.players))
            game.endTime?.let { end ->
                val elapsed = end - game.startTime
                val mins = (elapsed / 60000).toInt()
                val dur = if (mins < 60) context.getString(R.string.duration_minutes, mins) else context.getString(R.string.duration_hours, mins / 60, mins % 60)
                append(" · $dur")
            }
        }
        val subX = (imgWidth / 2f) - subPaint.measureText(subText) / 2f
        canvas.drawText(subText, subX, pad + titleSize + smallSize + 6, subPaint)

        val tableTop = pad.toFloat() + titleHeight

        // Winner line — just above the table (all players tied for lowest total)
        val overallTotals = game.players.associate { it.name to game.getPlayerTotal(it.name) }
        val minOverall = overallTotals.values.minOrNull() ?: 0
        val overallWinners = overallTotals.filter { it.value == minOverall }.keys
        if (overallWinners.isNotEmpty() && game.players.size > 1) {
            val winnerText = "${context.getString(R.string.winner_label, "").substringBefore(":")}: ${overallWinners.joinToString(", ")} 🏆"
            val winnerPaint = android.graphics.Paint().also {
                it.color = android.graphics.Color.parseColor("#FFD700")
                it.textSize = textSize
                it.isAntiAlias = true
                it.typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            canvas.drawText(winnerText, pad.toFloat(), tableTop - smallSize - 2, winnerPaint)
        }

        // Table card background
        val cardPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#1A1A2E")
            it.style = android.graphics.Paint.Style.FILL
        }
        val tableBottom = (imgHeight - pad).toFloat()
        canvas.drawRoundRect((pad / 2).toFloat(), tableTop, (imgWidth - pad / 2).toFloat(), tableBottom, 12f * density, 12f * density, cardPaint)

        // Paints
        val headerPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#8899AA")
            it.textSize = smallSize
            it.isAntiAlias = true
            it.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val cellPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#E0E0E0")
            it.textSize = textSize
            it.isAntiAlias = true
        }
        val winnerPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#FFD700")
            it.textSize = textSize
            it.isAntiAlias = true
            it.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val totalPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#FFD700")
            it.textSize = textSize
            it.isAntiAlias = true
            it.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val totalLabelPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#FFD700")
            it.textSize = smallSize
            it.isAntiAlias = true
            it.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val linePaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#1FFFFFFF")
            it.strokeWidth = 0.5f * density
        }
        val rowBgEven = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#08FFFFFF")
            it.style = android.graphics.Paint.Style.FILL
        }
        val winnerBgPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#15FFD700")
            it.style = android.graphics.Paint.Style.FILL
        }
        // Small star paint for winners
        val starPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#FFD700")
            it.style = android.graphics.Paint.Style.FILL
            it.isAntiAlias = true
        }

        val startY = tableTop + pad
        val startX = pad.toFloat()
        val contentRight = startX + tableContentWidth

        // Header background
        val headerBgPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#0AFFFFFF")
            it.style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(startX, startY, contentRight, startY + headerHeight, headerBgPaint)

        // Header texts
        canvas.drawText("#", startX + roundLabelWidth / 2 - headerPaint.measureText("#") / 2, startY + headerHeight * 0.6f, headerPaint)
        var x = startX + roundLabelWidth
        game.players.forEach { player ->
            val nameText = player.name
            // Truncate with ellipsis if needed
            val displayName = if (headerPaint.measureText(nameText) > colWidth - 8 * density) {
                var truncated = nameText
                while (headerPaint.measureText(truncated + "…") > colWidth - 8 * density && truncated.length > 1) {
                    truncated = truncated.dropLast(1)
                }
                truncated + "…"
            } else nameText
            canvas.drawText(displayName, x + colWidth / 2 - headerPaint.measureText(displayName) / 2, startY + headerHeight * 0.6f, headerPaint)
            x += colWidth
        }

        // Header bottom line
        val headerLinePaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#33FFFFFF")
            it.strokeWidth = 1.2f * density
        }
        canvas.drawLine(startX, startY + headerHeight, contentRight, startY + headerHeight, headerLinePaint)

        // Cumulative tracking
        val cumulative = mutableMapOf<String, Int>()
        game.players.forEach { cumulative[it.name] = 0 }

        // Round rows
        game.rounds.forEachIndexed { rIdx, round ->
            val rowY = startY + headerHeight + rIdx * rowHeight

            // Row background (alternating)
            if (rIdx % 2 == 0) {
                canvas.drawRect(startX, rowY, contentRight, rowY + rowHeight, rowBgEven)
            }

            // Round label
            val roundLabel = "${context.getString(R.string.round_abbr)}${rIdx + 1}"
            canvas.drawText(roundLabel, startX + roundLabelWidth / 2 - cellPaint.measureText(roundLabel) / 2, rowY + rowHeight * 0.6f, cellPaint)

            var px = startX + roundLabelWidth
            val lowestScore = round.scores.values.filter { it >= 0 }.minOrNull() ?: 0
            game.players.forEach { player ->
                val score = round.scores[player.name] ?: 0
                cumulative[player.name] = (cumulative[player.name] ?: 0) + score
                val isWinner = score == lowestScore

                if (isWinner) {
                    // Gold background for winner cell
                    canvas.drawRect(px + 2 * density, rowY + 2 * density, px + colWidth - 2 * density, rowY + rowHeight - 2 * density, winnerBgPaint)
                }

                val scoreText = "$score"
                val scoreX = px + colWidth / 2 - cellPaint.measureText(scoreText) / 2
                val scoreY = rowY + rowHeight * 0.6f

                if (isWinner) {
                    // Score in gold bold
                    canvas.drawText(scoreText, scoreX, scoreY, winnerPaint)
                    // Draw small gold star next to score
                    val starCx = scoreX + winnerPaint.measureText(scoreText) + 6 * density
                    val starCy = scoreY - 5 * density
                    val starR = 5 * density
                    drawStar(canvas, starCx, starCy, starR, starPaint)
                } else {
                    canvas.drawText(scoreText, scoreX, scoreY, cellPaint)
                }
                px += colWidth
            }

            // Row separator
            if (rIdx < game.rounds.size - 1) {
                canvas.drawLine(startX, rowY + rowHeight, contentRight, rowY + rowHeight, linePaint)
            }
        }

        // Totals row
        val totalY = startY + headerHeight + game.rounds.size * rowHeight
        val totalBgPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#12FFD700")
            it.style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(startX, totalY, contentRight, totalY + rowHeight, totalBgPaint)

        // Gold top line for totals
        val goldLinePaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#FFD700")
            it.strokeWidth = 1.5f * density
        }
        canvas.drawLine(startX, totalY, contentRight, totalY, goldLinePaint)

        // Total label with symbol
        val totalLabel = "∑"
        canvas.drawText(totalLabel, startX + roundLabelWidth / 2 - totalPaint.measureText(totalLabel) / 2, totalY + rowHeight * 0.6f, totalPaint)

        // Find lowest total score
        val lowestTotal = game.players.minOfOrNull { cumulative[it.name] ?: Int.MAX_VALUE } ?: 0

        var tx = startX + roundLabelWidth
        game.players.forEach { player ->
            val total = cumulative[player.name] ?: 0
            val isWinner = total == lowestTotal
            val paint = if (isWinner) totalPaint else cellPaint
            canvas.drawText("$total", tx + colWidth / 2 - paint.measureText("$total") / 2, totalY + rowHeight * 0.6f, paint)
            if (isWinner) {
                // Gold star next to winner's total
                val starCx = tx + colWidth / 2 + paint.measureText("$total") / 2 + 6 * density
                val starCy = totalY + rowHeight * 0.35f
                drawStar(canvas, starCx, starCy, 5 * density, starPaint)
            }
            tx += colWidth
        }

        // Rounded bottom corners on table (overdraw corners)
        val cornerR = 12f * density
        val cornerPaint = android.graphics.Paint().also {
            it.color = android.graphics.Color.parseColor("#16213E")
            it.style = android.graphics.Paint.Style.FILL
            it.isAntiAlias = true
        }
        // Bottom-left corner
        canvas.drawRect(0f, tableBottom - cornerR, startX + cornerR, tableBottom + pad, cornerPaint)
        // Bottom-right corner
        canvas.drawRect(contentRight - cornerR, tableBottom - cornerR, imgWidth.toFloat(), tableBottom + pad, cornerPaint)

        // Save to cache and return URI for sharing
        val filename = "RummiQ_${game.name.replace(" ", "_")}.png"
        val cacheDir = java.io.File(context.cacheDir, "share")
        cacheDir.mkdirs()
        val file = java.io.File(cacheDir, filename)
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_scoreboard)))
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.screenshot_failed), Toast.LENGTH_SHORT).show()
    }
}

/** Draws a 5-pointed star at (cx, cy) with the given radius. */
private fun drawStar(canvas: android.graphics.Canvas, cx: Float, cy: Float, r: Float, paint: android.graphics.Paint) {
    val path = android.graphics.Path()
    val innerR = r * 0.4f
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) r else innerR
        val angle = (Math.PI / 5 * i) - Math.PI / 2
        val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
        val y = cy + (radius * kotlin.math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    canvas.drawPath(path, paint)
}

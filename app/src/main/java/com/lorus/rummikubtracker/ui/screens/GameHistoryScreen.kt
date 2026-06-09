package com.lorus.rummikubtracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    var shareUri by mutableStateOf<Uri?>(null)

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
                                            val uri = createScoreboardBitmap(context, game)
                                            viewModel.shareUri = uri
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

    // Custom share bottom sheet
    val shareUri = viewModel.shareUri
    if (shareUri != null) {
        ShareBottomSheet(
            uri = shareUri,
            onDismiss = { viewModel.shareUri = null }
        )
    }
}

private fun createScoreboardBitmap(context: android.content.Context, game: Game): Uri? {
    try {
        val density = context.resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val rowHeight = (44 * density).toInt()
        val headerHeight = (44 * density).toInt()
        val roundLabelWidth = (52 * density).toInt()
        val titleHeight = (60 * density).toInt()
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
        val subText = "${game.rounds.size} rounds · ${game.players.size} players"
        val subX = (imgWidth / 2f) - subPaint.measureText(subText) / 2f
        canvas.drawText(subText, subX, pad + titleSize + smallSize + 6, subPaint)

        val tableTop = pad.toFloat() + titleHeight

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
            val roundLabel = "R${rIdx + 1}"
            canvas.drawText(roundLabel, startX + roundLabelWidth / 2 - cellPaint.measureText(roundLabel) / 2, rowY + rowHeight * 0.6f, cellPaint)

            var px = startX + roundLabelWidth
            game.players.forEach { player ->
                val score = round.scores[player.name] ?: 0
                cumulative[player.name] = (cumulative[player.name] ?: 0) + score
                val isWinner = round.winnerPlayerName == player.name

                if (isWinner) {
                    // Gold background for winner cell
                    canvas.drawRect(px + 2 * density, rowY + 2 * density, px + colWidth - 2 * density, rowY + rowHeight - 2 * density, winnerBgPaint)
                }

                val scoreText = "$score"
                val scoreX = px + colWidth / 2 - cellPaint.measureText(scoreText) / 2
                val scoreY = rowY + rowHeight * 0.6f

                if (isWinner) {
                    // "0" in gold bold
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

        var tx = startX + roundLabelWidth
        game.players.forEach { player ->
            val total = cumulative[player.name] ?: 0
            canvas.drawText("$total", tx + colWidth / 2 - totalPaint.measureText("$total") / 2, totalY + rowHeight * 0.6f, totalPaint)
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
        val filename = "Rummikub_${game.name.replace(" ", "_")}.png"
        val cacheDir = java.io.File(context.cacheDir, "share")
        cacheDir.mkdirs()
        val file = java.io.File(cacheDir, filename)
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) {
        Toast.makeText(context, "Screenshot failed: ${e.message}", Toast.LENGTH_SHORT).show()
        return null
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

/** Custom share bottom sheet showing all image-sharing apps in a 2-column grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareBottomSheet(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pm = remember { context.packageManager }

    // Discover all apps that can handle image/png sharing
    val shareIntent = remember {
        Intent(Intent.ACTION_SEND).apply { type = "image/png" }
    }
    val apps = remember {
        pm.queryIntentActivities(shareIntent, PackageManager.MATCH_ALL).map { ri ->
            ShareAppInfo(
                label = ri.loadLabel(pm).toString(),
                icon = ri.loadIcon(pm),
                packageName = ri.activityInfo.packageName,
                activityName = ri.activityInfo.name
            )
        }.distinctBy { it.packageName }
    }

    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Share Scoreboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 2-column grid of share targets
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 400.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(apps) { app ->
                    Surface(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                setClassName(app.packageName, app.activityName)
                            }
                            context.startActivity(intent)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.06f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = drawableToBitmap(app.icon).asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ShareAppInfo(
    val label: String,
    val icon: Drawable,
    val packageName: String,
    val activityName: String
)

/** Safely converts any Drawable to a Bitmap (handles VectorDrawable, AdaptiveIconDrawable, etc.). */
private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(canvas)
    return bitmap
}

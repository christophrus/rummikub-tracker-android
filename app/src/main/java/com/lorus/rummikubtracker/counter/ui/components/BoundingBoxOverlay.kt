package com.lorus.rummikubtracker.counter.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.lorus.rummikubtracker.counter.model.DetectedTile
import com.lorus.rummikubtracker.counter.ui.theme.JokerColor
import com.lorus.rummikubtracker.counter.ui.theme.TileBlue
import com.lorus.rummikubtracker.counter.ui.theme.TileOrange
import com.lorus.rummikubtracker.counter.ui.theme.TileRed

@Composable
fun BoundingBoxOverlay(
    bitmap: Bitmap,
    tiles: List<DetectedTile>,
    modifier: Modifier = Modifier
) {
    val imageBitmap = bitmap.asImageBitmap()
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw the original image scaled to fill the canvas
        val canvasW = size.width
        val canvasH = size.height
        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()

        val scale = minOf(canvasW / imgW, canvasH / imgH)
        val drawW = imgW * scale
        val drawH = imgH * scale
        val offsetX = (canvasW - drawW) / 2f
        val offsetY = (canvasH - drawH) / 2f

        drawImage(
            image = imageBitmap,
            dstSize = IntSize(drawW.toInt(), drawH.toInt()),
            dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt())
        )

        // Draw bounding boxes
        for (tile in tiles) {
            val boxColor = getTileColor(tile)
            val left = offsetX + tile.x * scale
            val top = offsetY + tile.y * scale
            val width = maxOf(1f, tile.width * scale)
            val height = maxOf(1f, tile.height * scale)

            // Box outline
            drawRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 3f)
            )

            // Label
            val label = if (tile.isJoker) "J" else "${tile.number}"
            val confLabel = "${(tile.confidence * 100).toInt()}%"
            val fullLabel = "$label $confLabel"

            // Label background — clamp to prevent negative coordinates
            val labelHeight = 24f
            val labelTop = if (top > labelHeight) top - labelHeight else top
            val labelY = if (top > labelHeight) top - labelHeight else top + 2f

            drawRect(
                color = boxColor.copy(alpha = 0.7f),
                topLeft = Offset(left, labelTop),
                size = Size(width.coerceAtLeast(60f), labelHeight)
            )

            drawText(
                textMeasurer = textMeasurer,
                text = fullLabel,
                topLeft = Offset(left + 4f, labelY),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

private fun getTileColor(tile: DetectedTile): Color {
    if (tile.isJoker) return JokerColor
    return when ((tile.number ?: 1) % 4) {
        1 -> TileRed
        2 -> TileBlue
        3 -> TileOrange
        else -> Color.DarkGray
    }
}

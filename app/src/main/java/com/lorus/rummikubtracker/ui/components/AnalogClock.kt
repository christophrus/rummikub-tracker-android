package com.lorus.rummikubtracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lorus.rummikubtracker.domain.model.Config
import com.lorus.rummikubtracker.ui.theme.*
import kotlin.math.*

@Composable
fun AnalogClock(
    remainingMs: Long,
    totalMs: Long,
    isPaused: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    onClick: (() -> Unit)? = null
) {
    val seconds = remainingMs / 1000f
    val totalSeconds = totalMs / 1000f
    // Clamp progress to 0..1 so extensions don't push the hand beyond the circle
    val progress = if (totalSeconds > 0) (seconds / totalSeconds).coerceIn(0f, 1f) else 0f

    val clockColor = when {
        seconds <= Config.CLOCK_COLOR_RED_SECONDS -> TimerRed
        seconds <= Config.CLOCK_COLOR_YELLOW_SECONDS -> TimerYellow
        else -> TimerBlue
    }

    val minutes = (remainingMs / 60000).toInt()
    val secs = ((remainingMs % 60000) / 1000).toInt()
    val timeText = String.format("%d:%02d", minutes, secs)

    Column(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier.size(size)
            ) {
                val strokeWidth = size.toPx() * 0.06f
                val radius = (size.toPx() - strokeWidth) / 2f
                val center = Offset(size.toPx() / 2f, size.toPx() / 2f)

                // Background circle
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.2f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress arc
                if (progress > 0) {
                    drawArc(
                        color = clockColor,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                        size = Size(size.toPx() - strokeWidth, size.toPx() - strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Tick marks
                for (i in 0 until 12) {
                    val angle = Math.toRadians((i * 30 - 90).toDouble())
                    val innerRadius = radius - strokeWidth * 1.5f
                    val outerRadius = radius - strokeWidth * 0.5f
                    val startX = center.x + innerRadius * cos(angle).toFloat()
                    val startY = center.y + innerRadius * sin(angle).toFloat()
                    val endX = center.x + outerRadius * cos(angle).toFloat()
                    val endY = center.y + outerRadius * sin(angle).toFloat()
                    drawLine(
                        color = Color.Gray,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeWidth * 0.3f
                    )
                }

                // Hand
                if (progress > 0) {
                    val handAngle = Math.toRadians((progress * 360 - 90).toDouble())
                    val handLength = radius * 0.6f
                    val handX = center.x + handLength * cos(handAngle).toFloat()
                    val handY = center.y + handLength * sin(handAngle).toFloat()
                    drawLine(
                        color = clockColor,
                        start = center,
                        end = Offset(handX, handY),
                        strokeWidth = strokeWidth * 0.3f,
                        cap = StrokeCap.Round
                    )
                    // Center dot
                    drawCircle(
                        color = clockColor,
                        radius = strokeWidth * 0.5f,
                        center = center
                    )
                }
            }

            // Digital time overlay
            Box(contentAlignment = Alignment.TopCenter) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(size * 0.32f))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = (size.value / 6.5f).sp
                        ),
                        color = clockColor
                    )
                }
                if (isPaused) {
                    Text(
                        text = "⏸",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .padding(top = size * 0.32f + (size / 6.5f) * 1.2f)
                    )
                }
            }
        }
    }
}

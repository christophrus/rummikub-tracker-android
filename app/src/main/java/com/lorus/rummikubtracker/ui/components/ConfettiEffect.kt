package com.lorus.rummikubtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class ConfettiShape { RECT, CIRCLE, STAR }

data class ConfettiParticle(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    var opacity: Float,
    var color: Color,
    var size: Float,
    var shape: ConfettiShape
)

@Composable
fun ConfettiEffect(
    modifier: Modifier = Modifier,
    particleCount: Int = 200,
    colors: List<Color> = listOf(
        Color(0xFFFF4757), // bright red
        Color(0xFF2ED573), // bright green
        Color(0xFFFFA502), // orange
        Color(0xFF5352ED), // purple
        Color(0xFF1E90FF), // dodger blue
        Color(0xFFFF6B81), // pink
        Color(0xFF7BED9F), // mint
        Color(0xFFFFD700), // gold
        Color(0xFFFF6348), // tomato
        Color(0xFF00D2D3)  // cyan
    )
) {
    val particles = remember {
        List(particleCount) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -1.5f,
                velocityX = (Random.nextFloat() - 0.5f) * 0.6f,
                velocityY = Random.nextFloat() * 0.7f + 0.2f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 6f,
                opacity = Random.nextFloat() * 0.3f + 0.7f,
                color = colors[Random.nextInt(colors.size)],
                size = Random.nextFloat() * 16f + 10f,
                shape = ConfettiShape.entries[Random.nextInt(ConfettiShape.entries.size)]
            )
        }
    }

    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16) // ~60fps
            frame++
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val sway = sin(frame * 0.025f) * 0.8f

        particles.forEach { particle ->
            val px = (particle.x + sway * particle.velocityY) * width
            val py = particle.y * height

            if (particle.opacity > 0 && py < height + particle.size && py > -particle.size) {
                rotate(particle.rotation, Offset(px, py)) {
                    val alpha = particle.opacity
                    val halfSize = particle.size / 2f
                    when (particle.shape) {
                        ConfettiShape.RECT -> {
                            drawRect(
                                color = particle.color.copy(alpha = alpha),
                                topLeft = Offset(px - halfSize, py - halfSize * 0.5f),
                                size = Size(particle.size, particle.size * 0.55f)
                            )
                        }
                        ConfettiShape.CIRCLE -> {
                            drawCircle(
                                color = particle.color.copy(alpha = alpha),
                                radius = halfSize * 0.6f,
                                center = Offset(px, py)
                            )
                        }
                        ConfettiShape.STAR -> {
                            drawStar(
                                color = particle.color.copy(alpha = alpha),
                                center = Offset(px, py),
                                radius = halfSize
                            )
                        }
                    }
                }
            }

            // Update physics
            particle.x += particle.velocityX * 0.008f
            particle.y += particle.velocityY * 0.012f
            particle.velocityY += 0.002f // gravity
            particle.rotation += particle.rotationSpeed
            if (particle.y > 1.3f) {
                particle.y = -0.15f - Random.nextFloat() * 0.2f
                particle.x = Random.nextFloat()
                particle.velocityX = (Random.nextFloat() - 0.5f) * 0.6f
                particle.velocityY = Random.nextFloat() * 0.7f + 0.2f
                particle.opacity = Random.nextFloat() * 0.3f + 0.7f
                particle.color = colors[Random.nextInt(colors.size)]
            }
        }
    }
}

private fun DrawScope.drawStar(
    color: Color,
    center: Offset,
    radius: Float,
    points: Int = 5
) {
    val path = Path()
    val innerRadius = radius * 0.4f
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = (PI / points * i) - PI / 2
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Fill)
}

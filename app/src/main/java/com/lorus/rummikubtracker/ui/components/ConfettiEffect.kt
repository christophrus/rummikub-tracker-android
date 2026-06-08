package com.lorus.rummikubtracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class ConfettiParticle(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    var opacity: Float,
    var color: Color,
    var size: Float
)

@Composable
fun ConfettiEffect(
    modifier: Modifier = Modifier,
    particleCount: Int = 100,
    colors: List<Color> = listOf(
        Color(0xFFFF6B6B),
        Color(0xFF4ECDC4),
        Color(0xFFFFE66D),
        Color(0xFF6C5CE7),
        Color(0xFFA8E6CF),
        Color(0xFFFF8A5C),
        Color(0xFF45B7D1),
        Color(0xFFF7DC6F)
    )
) {
    val particles = remember {
        List(particleCount) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -1f,
                velocityX = (Random.nextFloat() - 0.5f) * 0.3f,
                velocityY = Random.nextFloat() * 0.4f + 0.1f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 4f,
                opacity = Random.nextFloat() * 0.5f + 0.5f,
                color = colors[Random.nextInt(colors.size)],
                size = Random.nextFloat() * 8f + 4f
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
        val sway = sin(frame * 0.02f) * 0.3f

        particles.forEach { particle ->
            val px = (particle.x + sway * particle.velocityY) * width
            val py = particle.y * height

            if (particle.opacity > 0) {
                rotate(particle.rotation, Offset(px, py)) {
                    drawRect(
                        color = particle.color.copy(alpha = particle.opacity),
                        topLeft = Offset(px - particle.size / 2, py - particle.size / 2),
                        size = Size(particle.size, particle.size * 0.6f)
                    )
                }
            }

            // Update
            particle.x += particle.velocityX * 0.01f
            particle.y += particle.velocityY * 0.01f
            particle.rotation += particle.rotationSpeed
            if (particle.y > 1.2f) {
                particle.y = -0.1f
                particle.x = Random.nextFloat()
                particle.opacity = Random.nextFloat() * 0.5f + 0.5f
            }
        }
    }
}

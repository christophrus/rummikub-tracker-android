package com.lorus.rummikubtracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.lorus.rummikubtracker.R
import com.lorus.rummikubtracker.ui.theme.Indigo40
import com.lorus.rummikubtracker.ui.theme.Purple40
import java.io.File

@Composable
fun PlayerAvatar(
    name: String,
    imagePath: String? = null,
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    val file = imagePath?.let { File(it) }
    val hasImage = file?.exists() == true

    if (hasImage && file != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build(),
            contentDescription = name,
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        // Fallback: initial letter with gradient background
        val initial = name.firstOrNull()?.uppercase() ?: "?"
        val gradient = Brush.linearGradient(
            colors = listOf(Indigo40, Purple40)
        )

        Box(
            modifier = modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PlayerCard(
    name: String,
    imagePath: String? = null,
    isCurrentPlayer: Boolean = false,
    score: Int = 0,
    modifier: Modifier = Modifier
) {
    val gradient = if (isCurrentPlayer) {
        Brush.linearGradient(listOf(Indigo40, Purple40))
    } else {
        null
    }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (gradient != null) Modifier.background(gradient)
                    else Modifier
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayerAvatar(
                name = name,
                imagePath = imagePath,
                size = 40
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrentPlayer) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentPlayer) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${stringResource(R.string.score)}: $score",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrentPlayer) Color.White.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

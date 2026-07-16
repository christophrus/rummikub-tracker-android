package org.lorus.rummiq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.lorus.rummiq.ui.theme.Indigo40
import org.lorus.rummiq.ui.theme.Purple40
import java.io.File

@Composable
fun PlayerAvatar(
    name: String,
    imagePath: String? = null,
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    // Stat the file once per path instead of on every recomposition (filesystem I/O).
    val fileInfo = remember(imagePath) {
        val f = imagePath?.let { File(it) }
        if (f?.exists() == true) Pair(f, f.lastModified()) else null
    }

    if (fileInfo != null) {
        val (file, lastMod) = fileInfo
        // Caching stays enabled; the lastModified-based key busts the cache when the avatar
        // file is overwritten under the same name (previously both caches were fully disabled,
        // forcing a fresh decode from disk on every display).
        val cacheKey = "${file.absolutePath}:$lastMod"
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
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

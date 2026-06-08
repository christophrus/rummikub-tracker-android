package com.lorus.rummikubtracker.counter.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lorus.rummikubtracker.R
import com.lorus.rummikubtracker.counter.model.AnalysisResult
import com.lorus.rummikubtracker.counter.ui.components.BoundingBoxOverlay
import com.lorus.rummikubtracker.counter.ui.components.FullscreenImageDialog
import com.lorus.rummikubtracker.counter.ui.components.ScoreDisplay
import com.lorus.rummikubtracker.counter.ui.components.TileCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    bitmap: Bitmap,
    result: AnalysisResult,
    onNewPhoto: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showFullscreen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewPhoto,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = stringResource(R.string.new_photo),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Image with bounding boxes
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                        .clickable { showFullscreen = true }
                ) {
                    BoundingBoxOverlay(
                        bitmap = bitmap,
                        tiles = result.tiles
                    )
                }
            }

            // Score display
            item {
                ScoreDisplay(
                    totalScore = result.totalScore,
                    tileCount = result.tileCount,
                    processingTimeMs = result.processingTimeMs
                )
            }

            // Tile list header
            if (result.tiles.isNotEmpty()) {
                item {
                    Text(
                        text = "Erkannte Steine",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.no_detections),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }

            // Tile cards
            items(result.tiles) { tile ->
                TileCard(tile = tile)
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showFullscreen) {
        FullscreenImageDialog(
            bitmap = bitmap,
            onDismiss = { showFullscreen = false }
        )
    }
}

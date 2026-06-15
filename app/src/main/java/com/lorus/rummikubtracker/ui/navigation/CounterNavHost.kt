package com.lorus.rummikubtracker.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lorus.rummikubtracker.R
import com.lorus.rummikubtracker.counter.ui.screens.*
import com.lorus.rummikubtracker.counter.viewmodel.AnalysisViewModel
import com.lorus.rummikubtracker.counter.viewmodel.HistoryViewModel

private object CounterRoutes {
    const val MAIN_MENU = "counter_main_menu"
    const val RESULT = "counter_result"
    const val HISTORY = "counter_history"
    const val HISTORY_DETAIL = "counter_history_detail"
}

@Composable
fun CounterNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    onBackToTracker: () -> Unit
) {
    val viewModel: AnalysisViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> bitmap?.let { viewModel.analyze(it) } }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val bitmap = android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            viewModel.analyze(bitmap)
        }
    }

    // Camera permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_LONG).show()
        }
    }

    fun launchCamera() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                cameraLauncher.launch(null)
            }
            else -> {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // Navigate to result when analysis completes
    if (uiState.result != null && !uiState.isLoading) {
        androidx.compose.runtime.LaunchedEffect(uiState.result) {
            navController.navigate(CounterRoutes.RESULT) { launchSingleTop = true }
        }
    }

    Box(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = CounterRoutes.MAIN_MENU,
            modifier = Modifier.fillMaxSize()
        ) {
        composable(CounterRoutes.MAIN_MENU) {
            MainMenuScreen(
                onTakePhoto = { launchCamera() },
                onPickGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onNavigateToHistory = { navController.navigate(CounterRoutes.HISTORY) },
                onBack = onBackToTracker
            )
        }

        composable(CounterRoutes.RESULT) {
            BackHandler {
                viewModel.reset()
                navController.popBackStack()
            }

            val bitmap = uiState.originalBitmap
            val result = uiState.result
            if (bitmap != null && result != null) {
                ResultScreen(
                    bitmap = bitmap,
                    result = result,
                    onNewPhoto = {
                        viewModel.reset()
                    },
                    onBack = {
                        viewModel.reset()
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(CounterRoutes.HISTORY) {
            val historyDetailState by historyViewModel.detailState.collectAsState()

            HistoryScreen(
                onBack = { navController.popBackStack() },
                onEntryClick = { resultId -> historyViewModel.loadDetail(resultId) }
            )

            if (historyDetailState.result != null && !historyDetailState.isLoading) {
                androidx.compose.runtime.LaunchedEffect(historyDetailState.result) {
                    navController.navigate(CounterRoutes.HISTORY_DETAIL) {
                        launchSingleTop = true
                    }
                }
            }
        }

        composable(CounterRoutes.HISTORY_DETAIL) {
            BackHandler {
                historyViewModel.clearSelection()
                navController.popBackStack()
            }

            val detailState by historyViewModel.detailState.collectAsState()
            val bitmap = detailState.bitmap
            val result = detailState.result
            if (bitmap != null && result != null) {
                ResultScreen(
                    bitmap = bitmap,
                    result = result,
                    onNewPhoto = {
                        historyViewModel.clearSelection()
                        viewModel.reset()
                        navController.popBackStack(CounterRoutes.MAIN_MENU, inclusive = false)
                    },
                    onBack = {
                        historyViewModel.clearSelection()
                        navController.popBackStack()
                    },
                    showFab = false
                )
            }
        }
    }

        // Loading overlay shown while ML analysis is running
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.analyzing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
}

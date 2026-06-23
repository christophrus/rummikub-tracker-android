package org.lorus.rummiq.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.lorus.rummiq.R
import org.lorus.rummiq.counter.ui.screens.*
import org.lorus.rummiq.counter.viewmodel.AnalysisViewModel
import org.lorus.rummiq.counter.viewmodel.HistoryViewModel
import java.io.File

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

    // Create a temp file URI for full-resolution camera capture
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri ->
                try {
                    val bitmap = BitmapFactory.decodeStream(
                        context.contentResolver.openInputStream(uri)
                    )
                    if (bitmap != null) {
                        viewModel.analyze(bitmap)
                    }
                } catch (_: Exception) { }
                // Clean up temp file
                try { File(uri.path!!).delete() } catch (_: Exception) { }
            }
        }
    }

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
            val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            file.parentFile?.mkdirs()
            photoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            photoUri?.let { cameraLauncher.launch(it) }
        } else {
            Toast.makeText(context, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    fun launchCamera() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                file.parentFile?.mkdirs()
                photoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                photoUri?.let { cameraLauncher.launch(it) }
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
                    },
                    showFab = false
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

        // Orientation confirmation dialog
        if (uiState.showOrientationConfirm) {
            val confirmBitmap = uiState.orientationConfirmBitmap
            if (confirmBitmap != null) {
                OrientationConfirmDialog(
                    bitmap = confirmBitmap,
                    correctionDegrees = uiState.orientationCorrectionDegrees,
                    maxConfidence = uiState.orientationMaxConfidence,
                    onRotateLeft = { viewModel.rotateOrientationLeft() },
                    onRotateRight = { viewModel.rotateOrientationRight() },
                    onConfirm = { viewModel.confirmOrientation() },
                    onCancel = { viewModel.cancelOrientationConfirmation() }
                )
            }
        }
    }
}

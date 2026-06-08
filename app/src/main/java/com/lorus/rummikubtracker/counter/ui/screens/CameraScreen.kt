package com.lorus.rummikubtracker.counter.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lorus.rummikubtracker.R
import com.lorus.rummikubtracker.counter.ml.ImagePreprocessor
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import java.io.InputStream

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    isLoading: Boolean,
    error: String? = null,
    onImageCaptured: (Bitmap) -> Unit,
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream: InputStream? = context.contentResolver.openInputStream(it)
            inputStream?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    onImageCaptured(bitmap)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Back button (top-left)
        androidx.compose.material3.IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 8.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                .size(44.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        if (cameraPermissionState.status.isGranted) {
            // Camera preview
            val imageCapture = remember { ImageCapture.Builder().build() }

            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Bottom controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button
                IconButton(
                    onClick = {
                        galleryLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = stringResource(R.string.pick_gallery),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(48.dp))

                // Capture button
                FloatingActionButton(
                    onClick = {
                        if (!isLoading) {
                            imageCapture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                        val bitmap = imageProxy.toBitmap()
                                        val rotated = ImagePreprocessor.rotateBitmap(
                                            bitmap, imageProxy.imageInfo.rotationDegrees
                                        )
                                        onImageCaptured(rotated)
                                        imageProxy.close()
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("CameraScreen", "Image capture failed", exception)
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.take_photo),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Spacer for symmetry
                Spacer(modifier = Modifier.width(48.dp))
                Spacer(modifier = Modifier.size(56.dp))
            }

            // Loading overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = stringResource(R.string.analyzing),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }

            // Error overlay
            if (error != null && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.error_occurred),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = error,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.size(24.dp))
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        } else {
            // Permission not granted - show explanation
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.camera_permission_title),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(R.string.camera_permission_text),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text(stringResource(R.string.grant_permission))
                }

                // Gallery fallback even without camera
                Button(
                    onClick = {
                        galleryLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.pick_gallery))
                }
            }
        }
    }
}

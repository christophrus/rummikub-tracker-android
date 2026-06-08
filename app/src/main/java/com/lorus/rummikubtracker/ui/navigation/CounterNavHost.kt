package com.lorus.rummikubtracker.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lorus.rummikubtracker.counter.ui.screens.*
import com.lorus.rummikubtracker.counter.viewmodel.AnalysisViewModel
import com.lorus.rummikubtracker.counter.viewmodel.HistoryViewModel

private object CounterRoutes {
    const val MAIN_MENU = "counter_main_menu"
    const val CAMERA = "counter_camera"
    const val RESULT = "counter_result"
    const val HISTORY = "counter_history"
    const val HISTORY_DETAIL = "counter_history_detail"
    const val SETTINGS = "counter_settings"
    const val ABOUT = "counter_about"
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

    NavHost(
        navController = navController,
        startDestination = CounterRoutes.MAIN_MENU,
        modifier = modifier
    ) {
        composable(CounterRoutes.MAIN_MENU) {
            MainMenuScreen(
                onNavigateToCamera = { navController.navigate(CounterRoutes.CAMERA) },
                onNavigateToHistory = { navController.navigate(CounterRoutes.HISTORY) },
                onNavigateToSettings = { navController.navigate(CounterRoutes.SETTINGS) },
                onNavigateToAbout = { navController.navigate(CounterRoutes.ABOUT) }
            )
        }

        composable(CounterRoutes.CAMERA) {
            CameraScreen(
                isLoading = uiState.isLoading,
                error = uiState.error,
                onImageCaptured = { bitmap -> viewModel.analyze(bitmap) },
                onRetry = { viewModel.reset() },
                onBack = {
                    viewModel.reset()
                    navController.popBackStack()
                }
            )

            if (uiState.result != null && !uiState.isLoading) {
                androidx.compose.runtime.LaunchedEffect(uiState.result) {
                    navController.navigate(CounterRoutes.RESULT) {
                        launchSingleTop = true
                    }
                }
            }
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
                        navController.popBackStack(CounterRoutes.CAMERA, inclusive = false)
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
                    onNewPhoto = { navController.navigate(CounterRoutes.CAMERA) },
                    onBack = {
                        historyViewModel.clearSelection()
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(CounterRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(CounterRoutes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

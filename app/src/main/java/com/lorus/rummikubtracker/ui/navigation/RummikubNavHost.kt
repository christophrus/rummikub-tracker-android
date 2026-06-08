package com.lorus.rummikubtracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lorus.rummikubtracker.ui.screens.*

object Routes {
    const val HOME = "home"
    const val NEW_GAME = "new_game"
    const val PLAYER_SELECTION = "player_selection/{gameId}"
    const val ACTIVE_GAME = "active_game/{gameId}"
    const val MANAGE_PLAYERS = "manage_players"
    const val GAME_HISTORY = "game_history"
    const val SETTINGS = "settings"

    fun playerSelection(gameId: Long) = "player_selection/$gameId"
    fun activeGame(gameId: Long) = "active_game/$gameId"
}

@Composable
fun RummikubNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNewGame = { navController.navigate(Routes.NEW_GAME) },
                onResumeGame = { gameId -> navController.navigate(Routes.activeGame(gameId)) },
                onManagePlayers = { navController.navigate(Routes.MANAGE_PLAYERS) },
                onGameHistory = { navController.navigate(Routes.GAME_HISTORY) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.NEW_GAME) {
            NewGameScreen(
                onStartGame = { gameId ->
                    navController.navigate(Routes.playerSelection(gameId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PLAYER_SELECTION,
            arguments = listOf(navArgument("gameId") { type = NavType.LongType })
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong("gameId") ?: return@composable
            PlayerSelectionScreen(
                gameId = gameId,
                onPlayerSelected = {
                    navController.navigate(Routes.activeGame(gameId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ACTIVE_GAME,
            arguments = listOf(navArgument("gameId") { type = NavType.LongType })
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong("gameId") ?: return@composable
            ActiveGameScreen(
                gameId = gameId,
                onGameEnded = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MANAGE_PLAYERS) {
            ManagePlayersScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.GAME_HISTORY) {
            GameHistoryScreen(
                onBack = { navController.popBackStack() },
                onViewGame = { gameId -> navController.navigate(Routes.activeGame(gameId)) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

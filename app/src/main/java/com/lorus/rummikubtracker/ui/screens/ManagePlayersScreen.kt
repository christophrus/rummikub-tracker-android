package com.lorus.rummikubtracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lorus.rummikubtracker.R
import com.lorus.rummikubtracker.domain.model.Player
import com.lorus.rummikubtracker.domain.usecase.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManagePlayersViewModel @Inject constructor(
    private val playerManager: PlayerManager
) : androidx.lifecycle.ViewModel() {
    var players by mutableStateOf<List<Player>>(emptyList())
    var playerToDelete by mutableStateOf<String?>(null)

    private val scope = kotlinx.coroutines.MainScope()

    init {
        scope.launch {
            playerManager.getAllPlayers().collect { saved ->
                players = saved
            }
        }
    }

    fun deletePlayer(name: String) {
        scope.launch {
            playerManager.deletePlayer(name)
            playerToDelete = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePlayersScreen(
    onBack: () -> Unit,
    viewModel: ManagePlayersViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_players_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.players.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_saved_players),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(viewModel.players) { player ->
                    ListItem(
                        headlineContent = { Text(player.name) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.playerToDelete = player.name }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete_player),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Delete confirmation dialog
    viewModel.playerToDelete?.let { playerName ->
        AlertDialog(
            onDismissRequest = { viewModel.playerToDelete = null },
            title = { Text(stringResource(R.string.delete_player)) },
            text = { Text(stringResource(R.string.confirm_delete_player, playerName)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deletePlayer(playerName) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.playerToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

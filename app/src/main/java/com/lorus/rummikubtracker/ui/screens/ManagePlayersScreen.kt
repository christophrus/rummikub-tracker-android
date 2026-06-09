package com.lorus.rummikubtracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
    var editingPlayer by mutableStateOf<Player?>(null)
    var newPlayerName by mutableStateOf("")
    var showAddDialog by mutableStateOf(false)

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

    fun openAddDialog() {
        newPlayerName = ""
        showAddDialog = true
    }

    fun openEditDialog(player: Player) {
        editingPlayer = player
        newPlayerName = player.name
        showAddDialog = true
    }

    fun savePlayer() {
        val name = newPlayerName.trim()
        if (name.isEmpty()) return

        scope.launch {
            // If editing and name changed, delete old name first
            val oldName = editingPlayer?.name
            if (oldName != null && oldName != name) {
                playerManager.deletePlayer(oldName)
            }
            playerManager.savePlayer(name)
            showAddDialog = false
            editingPlayer = null
            newPlayerName = ""
        }
    }

    fun dismissDialog() {
        showAddDialog = false
        editingPlayer = null
        newPlayerName = ""
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
                },
                actions = {
                    IconButton(onClick = { viewModel.openAddDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_player))
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.players.isEmpty() && !viewModel.showAddDialog) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.no_saved_players),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.openAddDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_player))
                    }
                }
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
                            Row {
                                IconButton(onClick = { viewModel.openEditDialog(player) }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.edit),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { viewModel.playerToDelete = player.name }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete_player),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Add/Edit dialog
    if (viewModel.showAddDialog) {
        val isEditing = viewModel.editingPlayer != null
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = {
                Text(
                    if (isEditing) "Edit Player"
                    else stringResource(R.string.add_player)
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isEditing) "Change the name for ${viewModel.editingPlayer?.name}"
                               else "Enter a name for the new player",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = viewModel.newPlayerName,
                        onValueChange = { viewModel.newPlayerName = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.player)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.savePlayer() },
                    enabled = viewModel.newPlayerName.trim().isNotEmpty()
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDialog() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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

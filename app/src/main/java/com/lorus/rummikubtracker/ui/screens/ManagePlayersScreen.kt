package com.lorus.rummikubtracker.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lorus.rummikubtracker.R
import com.lorus.rummikubtracker.domain.model.Player
import com.lorus.rummikubtracker.domain.usecase.PlayerManager
import com.lorus.rummikubtracker.ui.components.PlayerAvatar
import com.lorus.rummikubtracker.ui.components.ScrollIndicator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManagePlayersViewModel @Inject constructor(
    private val playerManager: PlayerManager
) : androidx.lifecycle.ViewModel() {
    var players by mutableStateOf<List<Player>>(emptyList())
    var playerListVersion by mutableStateOf(0)
    var playerToDelete by mutableStateOf<String?>(null)
    var editingPlayer by mutableStateOf<Player?>(null)
    var newPlayerName by mutableStateOf("")
    var showAddDialog by mutableStateOf(false)
    var pendingImageUri by mutableStateOf<Uri?>(null)
    var pendingImagePath by mutableStateOf<String?>(null)
    var removePhoto by mutableStateOf(false)

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
        editingPlayer = null
        pendingImageUri = null
        pendingImagePath = null
        removePhoto = false
        showAddDialog = true
    }

    fun openEditDialog(player: Player) {
        editingPlayer = player
        newPlayerName = player.name
        pendingImageUri = null
        pendingImagePath = player.imagePath
        removePhoto = false
        showAddDialog = true
    }

    fun onImageSelected(uri: Uri) {
        pendingImageUri = uri
        pendingImagePath = null
        removePhoto = false
    }

    fun onRemovePhoto() {
        pendingImageUri = null
        pendingImagePath = null
        removePhoto = true
    }

    fun getPreviewPath(context: android.content.Context): String? {
        if (removePhoto) return null
        return pendingImagePath ?: pendingImageUri?.let { uri ->
            try {
                val tempFile = java.io.File(context.cacheDir, "preview_avatar")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.absolutePath
            } catch (_: Exception) { null }
        }
    }

    fun savePlayer(context: android.content.Context) {
        val name = newPlayerName.trim()
        if (name.isEmpty()) return

        scope.launch {
            var imagePath = editingPlayer?.imagePath

            // Handle photo removal
            if (removePhoto) {
                // Delete old avatar file if exists
                editingPlayer?.imagePath?.let { java.io.File(it).delete() }
                imagePath = null
            } else {
                // Compress and save image if a new one was picked
                val uri = pendingImageUri
                if (uri != null) {
                    // Copy to temp file first for compressAndSaveImage
                    val tempFile = java.io.File(context.cacheDir, "temp_avatar_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    imagePath = playerManager.compressAndSaveImage(name, tempFile.absolutePath)
                    tempFile.delete()
                }
            }

            // If editing and name changed, delete old name first
            val oldName = editingPlayer?.name
            if (oldName != null && oldName != name) {
                playerManager.deletePlayer(oldName)
            }
            playerManager.savePlayer(name, imagePath)
            playerListVersion++
            showAddDialog = false
            editingPlayer = null
            newPlayerName = ""
            pendingImageUri = null
            pendingImagePath = null
        }
    }

    fun dismissDialog() {
        showAddDialog = false
        editingPlayer = null
        newPlayerName = ""
        pendingImageUri = null
        pendingImagePath = null
        removePhoto = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePlayersScreen(
    onBack: () -> Unit,
    viewModel: ManagePlayersViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Gallery picker for avatar
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.onImageSelected(it) } }

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
            val lazyListState = rememberLazyListState()
            val canScrollForward by remember { derivedStateOf { lazyListState.canScrollForward } }

            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                items(viewModel.players, key = { "${it.name}_${viewModel.playerListVersion}" }) { player ->
                    ListItem(
                        headlineContent = { Text(player.name) },
                        leadingContent = {
                            PlayerAvatar(name = player.name, imagePath = player.imagePath, size = 40)
                        },
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

                ScrollIndicator(
                    canScrollForward = canScrollForward,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    // Add/Edit dialog
    if (viewModel.showAddDialog) {
        val isEditing = viewModel.editingPlayer != null
        val previewPath = viewModel.pendingImagePath
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = {
                Text(
                    if (isEditing) stringResource(R.string.edit)
                    else stringResource(R.string.add_player)
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Avatar preview
                    PlayerAvatar(
                        name = viewModel.newPlayerName.ifEmpty { "?" },
                        imagePath = viewModel.getPreviewPath(context),
                        size = 72
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.select_photo))
                    }
                    // Show delete button when a photo exists, below the select button
                    if (viewModel.getPreviewPath(context) != null) {
                        TextButton(onClick = { viewModel.onRemovePhoto() }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.remove_photo),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
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
                    onClick = { viewModel.savePlayer(context) },
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

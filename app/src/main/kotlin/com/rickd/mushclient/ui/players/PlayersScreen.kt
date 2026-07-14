package com.rickd.mushclient.ui.players

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rickd.mushclient.data.CharacterWithWorld
import com.rickd.mushclient.data.World

@Composable
fun PlayersScreen(
    onBack: () -> Unit,
    onConnect: (host: String, port: Int, characterName: String, characterPassword: String) -> Unit,
    viewModel: PlayersViewModel = viewModel(),
) {
    val players by viewModel.players.collectAsState()
    val worlds by viewModel.worlds.collectAsState()
    var editing by remember { mutableStateOf<CharacterWithWorld?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< Back") }
            Text("Players", style = MaterialTheme.typography.titleLarge)
        }

        if (players.isEmpty()) {
            Text(
                text = "No saved characters yet, across any world.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(players, key = { it.character.id }) { player ->
                PlayerRow(
                    player = player,
                    onClick = {
                        onConnect(player.worldHost, player.worldPort, player.character.name, player.character.password)
                    },
                    onEdit = {
                        editing = player
                        showEditor = true
                    },
                    onDelete = { viewModel.delete(player.character) },
                )
            }
        }

        Button(
            onClick = {
                editing = null
                showEditor = true
            },
            enabled = worlds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("New Player")
        }
        if (worlds.isEmpty()) {
            Text(
                text = "Add a world first, then players can be saved against it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showEditor) {
        PlayerEditorDialog(
            initial = editing,
            worlds = worlds,
            onDismiss = { showEditor = false },
            onSave = { worldId, name, password ->
                viewModel.save(editing?.character?.id, worldId, name, password)
                showEditor = false
            },
        )
    }
}

@Composable
private fun PlayerRow(
    player: CharacterWithWorld,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = player.character.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${player.worldName} — ${player.worldHost}:${player.worldPort}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text("Edit") }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

@Composable
private fun PlayerEditorDialog(
    initial: CharacterWithWorld?,
    worlds: List<World>,
    onDismiss: () -> Unit,
    onSave: (worldId: Long, name: String, password: String) -> Unit,
) {
    var selectedWorldId by remember { mutableStateOf(initial?.character?.worldId ?: worlds.firstOrNull()?.id) }
    var name by remember { mutableStateOf(initial?.character?.name ?: "") }
    var password by remember { mutableStateOf(initial?.character?.password ?: "") }

    val isValid = selectedWorldId != null && name.isNotBlank() && password.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Player" else "Edit Player") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorldSelector(worlds = worlds, selectedWorldId = selectedWorldId, onSelect = { selectedWorldId = it })
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedWorldId!!, name, password) }, enabled = isValid) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun WorldSelector(
    worlds: List<World>,
    selectedWorldId: Long?,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = worlds.firstOrNull { it.id == selectedWorldId }

    Column {
        Text(
            text = "World",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(selected?.name ?: "Select a world")
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                worlds.forEach { world ->
                    DropdownMenuItem(
                        text = { Text(world.name) },
                        onClick = {
                            onSelect(world.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

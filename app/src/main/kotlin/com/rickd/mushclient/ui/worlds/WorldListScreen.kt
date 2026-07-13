package com.rickd.mushclient.ui.worlds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rickd.mushclient.data.World

@Composable
fun WorldListScreen(
    onSelectWorld: (World) -> Unit,
    onConnectManually: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: WorldListViewModel = viewModel(),
) {
    val worlds by viewModel.worlds.collectAsState()
    var editing by remember { mutableStateOf<World?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Worlds", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.MoreVert, contentDescription = "Settings")
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(worlds, key = { it.id }) { world ->
                WorldRow(
                    world = world,
                    onClick = { onSelectWorld(world) },
                    onEdit = {
                        editing = world
                        showEditor = true
                    },
                    onDelete = { viewModel.delete(world) },
                )
            }
        }

        Button(
            onClick = {
                editing = null
                showEditor = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("New World")
        }
        TextButton(onClick = onConnectManually, modifier = Modifier.fillMaxWidth()) {
            Text("Connect without saving")
        }
    }

    if (showEditor) {
        WorldEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { name, host, port ->
                viewModel.save(editing?.id, name, host, port)
                showEditor = false
            },
        )
    }
}

@Composable
private fun WorldRow(
    world: World,
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
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = world.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${world.host}:${world.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text("Edit") }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

@Composable
private fun WorldEditorDialog(
    initial: World?,
    onDismiss: () -> Unit,
    onSave: (name: String, host: String, port: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf(initial?.port?.toString() ?: "") }

    val portValue = port.toIntOrNull()
    val isValid = name.isNotBlank() && host.isNotBlank() && portValue != null && portValue in 1..65535

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New World" else "Edit World") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") }, singleLine = true)
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, host, portValue!!) }, enabled = isValid) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

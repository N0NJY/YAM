package com.rickd.mushclient.ui.worlds

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rickd.mushclient.data.MushCharacter

@Composable
fun CharacterListScreen(
    worldId: Long,
    onConnect: (host: String, port: Int, characterName: String?, characterPassword: String?) -> Unit,
    onBack: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: CharacterListViewModel = viewModel(
        factory = CharacterListViewModel.factory(application, worldId),
        key = "characters-$worldId",
    )
    val world by viewModel.world.collectAsState()
    val characters by viewModel.characters.collectAsState()
    var editing by remember { mutableStateOf<MushCharacter?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< Back") }
            Text(world?.name ?: "Characters", style = MaterialTheme.typography.titleLarge)
        }
        world?.let {
            Text(
                text = "${it.host}:${it.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(characters, key = { it.id }) { character ->
                CharacterRow(
                    character = character,
                    onClick = {
                        val current = world ?: return@CharacterRow
                        onConnect(current.host, current.port, character.name, character.password)
                    },
                    onEdit = {
                        editing = character
                        showEditor = true
                    },
                    onDelete = { viewModel.delete(character) },
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
            Text("New Character")
        }
        TextButton(
            onClick = {
                val current = world ?: return@TextButton
                onConnect(current.host, current.port, null, null)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Connect without character")
        }
    }

    if (showEditor) {
        CharacterEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { name, password ->
                viewModel.save(editing?.id, name, password)
                showEditor = false
            },
        )
    }
}

@Composable
private fun CharacterRow(
    character: MushCharacter,
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
        Text(text = character.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onEdit) { Text("Edit") }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

@Composable
private fun CharacterEditorDialog(
    initial: MushCharacter?,
    onDismiss: () -> Unit,
    onSave: (name: String, password: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }

    val isValid = name.isNotBlank() && password.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Character" else "Edit Character") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            TextButton(onClick = { onSave(name, password) }, enabled = isValid) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

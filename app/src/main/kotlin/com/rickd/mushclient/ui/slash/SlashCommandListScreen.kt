package com.rickd.mushclient.ui.slash

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rickd.mushclient.data.PatternMatchType
import com.rickd.mushclient.data.SlashCommand
import com.rickd.mushclient.data.SlashCommandWithWorldName
import com.rickd.mushclient.data.World

@Composable
fun SlashCommandListScreen(
    onBack: () -> Unit,
    viewModel: SlashCommandListViewModel = viewModel(),
) {
    val commands by viewModel.commands.collectAsState()
    val worlds by viewModel.worlds.collectAsState()
    var editing by remember { mutableStateOf<SlashCommandWithWorldName?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("< Back") }
            Text("Slash Commands", style = MaterialTheme.typography.titleLarge)
        }

        if (commands.isEmpty()) {
            Text(
                text = "No slash commands yet. Add one to expand /name into a full command.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(commands, key = { it.command.id }) { entry ->
                SlashCommandRow(
                    entry = entry,
                    onToggleEnabled = { viewModel.setEnabled(entry.command, it) },
                    onEdit = {
                        editing = entry
                        showEditor = true
                    },
                    onDelete = { viewModel.delete(entry.command) },
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
            Text("New Slash Command")
        }
    }

    if (showEditor) {
        SlashCommandEditorDialog(
            initial = editing?.command,
            worlds = worlds,
            onDismiss = { showEditor = false },
            onSave = { worldId, name, pattern, matchType, caseSensitive, enabled, sendTemplate ->
                viewModel.save(
                    id = editing?.command?.id,
                    worldId = worldId,
                    name = name,
                    pattern = pattern,
                    matchType = matchType,
                    caseSensitive = caseSensitive,
                    enabled = enabled,
                    sendTemplate = sendTemplate,
                )
                showEditor = false
            },
        )
    }
}

@Composable
private fun SlashCommandRow(
    entry: SlashCommandWithWorldName,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val command = entry.command
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "/${command.name}", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${entry.worldName ?: "Global"} — ${command.sendTemplate.lines().first()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = command.enabled, onCheckedChange = onToggleEnabled)
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

@Composable
private fun SlashCommandEditorDialog(
    initial: SlashCommand?,
    worlds: List<World>,
    onDismiss: () -> Unit,
    onSave: (
        worldId: Long?,
        name: String,
        pattern: String,
        matchType: PatternMatchType,
        caseSensitive: Boolean,
        enabled: Boolean,
        sendTemplate: String,
    ) -> Unit,
) {
    var worldId by remember { mutableStateOf(initial?.worldId) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var pattern by remember { mutableStateOf(initial?.pattern ?: "*") }
    var matchType by remember { mutableStateOf(initial?.matchType ?: PatternMatchType.WILDCARD) }
    var caseSensitive by remember { mutableStateOf(initial?.caseSensitive ?: false) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var sendTemplate by remember { mutableStateOf(initial?.sendTemplate ?: "") }

    val isValid = name.isNotBlank() && pattern.isNotBlank() && sendTemplate.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Slash Command" else "Edit Slash Command") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SlashCommandWorldSelector(worlds = worlds, selectedWorldId = worldId, onSelect = { worldId = it })
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Invoked as /name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Argument pattern") },
                    placeholder = { Text("Matched against text after the name") },
                    singleLine = true,
                )

                Text(
                    "Match type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PatternMatchType.entries.forEach { type ->
                        FilterChip(
                            selected = matchType == type,
                            onClick = { matchType = type },
                            label = { Text(type.label()) },
                        )
                    }
                }

                LabeledCheckbox("Case sensitive", caseSensitive) { caseSensitive = it }
                LabeledCheckbox("Enabled", enabled) { enabled = it }

                OutlinedTextField(
                    value = sendTemplate,
                    onValueChange = { sendTemplate = it },
                    label = { Text("Send") },
                    placeholder = { Text("One command per line. %0-%9 insert captures.") },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(worldId, name, pattern, matchType, caseSensitive, enabled, sendTemplate) },
                enabled = isValid,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun LabeledCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

private fun PatternMatchType.label(): String = when (this) {
    PatternMatchType.WILDCARD -> "Wildcard"
    PatternMatchType.REGEXP -> "Regexp"
    PatternMatchType.CONTAINS -> "Contains"
}

@Composable
private fun SlashCommandWorldSelector(
    worlds: List<World>,
    selectedWorldId: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = worlds.firstOrNull { it.id == selectedWorldId }?.name ?: "Global (all worlds)"

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
                Text(selectedName)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Global (all worlds)") },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
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

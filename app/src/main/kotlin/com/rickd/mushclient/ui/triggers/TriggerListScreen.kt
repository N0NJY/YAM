package com.rickd.mushclient.ui.triggers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rickd.mushclient.ansi.AnsiPalette
import com.rickd.mushclient.data.Trigger
import com.rickd.mushclient.data.PatternMatchType
import com.rickd.mushclient.data.TriggerWithWorldName
import com.rickd.mushclient.data.World
import com.rickd.mushclient.ui.common.ConfirmDialog
import com.rickd.mushclient.ui.common.SettingsIconButton

@Composable
fun TriggerListScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TriggerListViewModel = viewModel(),
) {
    val triggers by viewModel.triggers.collectAsState()
    val worlds by viewModel.worlds.collectAsState()
    var editing by remember { mutableStateOf<TriggerWithWorldName?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TriggerWithWorldName?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("< Back") }
                Text("Triggers", style = MaterialTheme.typography.titleLarge)
            }
            SettingsIconButton(onClick = onOpenSettings)
        }

        if (triggers.isEmpty()) {
            Text(
                text = "No triggers yet. Add one to react automatically to incoming text.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(triggers, key = { it.trigger.id }) { entry ->
                TriggerRow(
                    entry = entry,
                    onToggleEnabled = { viewModel.setEnabled(entry.trigger, it) },
                    onEdit = {
                        editing = entry
                        showEditor = true
                    },
                    onDelete = { deleting = entry },
                )
            }
        }

        Button(
            onClick = {
                editing = null
                showEditor = true
            },
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("New Trigger")
        }
    }

    if (showEditor) {
        TriggerEditorDialog(
            initial = editing?.trigger,
            worlds = worlds,
            onDismiss = { showEditor = false },
            onSave = { worldId, name, pattern, matchType, caseSensitive, enabled, continueMatching,
                omitFromDisplay, recolor, sendText, inputText ->
                viewModel.save(
                    id = editing?.trigger?.id,
                    worldId = worldId,
                    name = name,
                    pattern = pattern,
                    matchType = matchType,
                    caseSensitive = caseSensitive,
                    enabled = enabled,
                    continueMatching = continueMatching,
                    omitFromDisplay = omitFromDisplay,
                    recolorForeground = recolor,
                    sendText = sendText,
                    inputText = inputText,
                )
                showEditor = false
            },
        )
    }

    deleting?.let { entry ->
        ConfirmDialog(
            title = "Delete trigger?",
            message = "\"${entry.trigger.name}\" will be permanently deleted.",
            onConfirm = {
                viewModel.delete(entry.trigger)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun TriggerRow(
    entry: TriggerWithWorldName,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val trigger = entry.trigger
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = trigger.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${entry.worldName ?: "Global"} — ${trigger.pattern}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = trigger.enabled, onCheckedChange = onToggleEnabled)
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

// None = don't recolor. The rest are AnsiPalette's standard 8 + a couple of bright variants,
// so trigger highlight colors read the same as the ANSI colors already used in the scrollback.
private val RECOLOR_OPTIONS: List<Int?> = listOf(
    null, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15,
)

@Composable
private fun TriggerEditorDialog(
    initial: Trigger?,
    worlds: List<World>,
    onDismiss: () -> Unit,
    onSave: (
        worldId: Long?,
        name: String,
        pattern: String,
        matchType: PatternMatchType,
        caseSensitive: Boolean,
        enabled: Boolean,
        continueMatching: Boolean,
        omitFromDisplay: Boolean,
        recolorForeground: Int?,
        sendText: String?,
        inputText: String?,
    ) -> Unit,
) {
    var worldId by remember { mutableStateOf(initial?.worldId) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var pattern by remember { mutableStateOf(initial?.pattern ?: "") }
    var matchType by remember { mutableStateOf(initial?.matchType ?: PatternMatchType.WILDCARD) }
    var caseSensitive by remember { mutableStateOf(initial?.caseSensitive ?: false) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var continueMatching by remember { mutableStateOf(initial?.continueMatching ?: false) }
    var omitFromDisplay by remember { mutableStateOf(initial?.omitFromDisplay ?: false) }
    var recolor by remember { mutableStateOf(initial?.recolorForeground) }
    var sendText by remember { mutableStateOf(initial?.sendText ?: "") }
    var inputText by remember { mutableStateOf(initial?.inputText ?: "") }

    val isValid = name.isNotBlank() && pattern.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Trigger" else "Edit Trigger") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TriggerWorldSelector(worlds = worlds, selectedWorldId = worldId, onSelect = { worldId = it })
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern") },
                    singleLine = true,
                )

                Text("Match type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                LabeledCheckbox("Continue matching further triggers", continueMatching) { continueMatching = it }
                LabeledCheckbox("Omit from display", omitFromDisplay) { omitFromDisplay = it }

                Text(
                    "Recolor foreground",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RECOLOR_OPTIONS.forEach { index ->
                        val argb = index?.let { AnsiPalette.color(it).toArgb() }
                        ColorSwatch(
                            argb = argb,
                            selected = recolor == argb,
                            onClick = { recolor = argb },
                        )
                    }
                }

                OutlinedTextField(
                    value = sendText,
                    onValueChange = { sendText = it },
                    label = { Text("Send") },
                    placeholder = { Text("One command per line. %0-%9 insert captures. Sent silently.") },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Put in input box") },
                    placeholder = { Text("%0-%9 insert captures") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        worldId,
                        name,
                        pattern,
                        matchType,
                        caseSensitive,
                        enabled,
                        continueMatching,
                        omitFromDisplay,
                        recolor,
                        sendText.ifBlank { null },
                        inputText.ifBlank { null },
                    )
                },
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

@Composable
private fun ColorSwatch(argb: Int?, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(argb?.let { Color(it) } ?: Color.Transparent)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (argb == null && selected) {
            Icon(Icons.Default.Check, contentDescription = "None", modifier = Modifier.size(16.dp))
        }
    }
}

private fun PatternMatchType.label(): String = when (this) {
    PatternMatchType.WILDCARD -> "Wildcard"
    PatternMatchType.REGEXP -> "Regexp"
    PatternMatchType.CONTAINS -> "Contains"
}

@Composable
private fun TriggerWorldSelector(
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

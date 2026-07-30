package com.rickd.mushclient.ui.worlds

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rickd.mushclient.data.World
import com.rickd.mushclient.data.WorldGroup
import com.rickd.mushclient.data.WorldWithLastCharacter
import com.rickd.mushclient.ui.common.ConfirmDialog
import com.rickd.mushclient.ui.common.SettingsIconButton

@Composable
fun WorldAddressBookScreen(
    onSelectWorld: (World) -> Unit,
    onConnectManually: () -> Unit,
    onOpenSettings: () -> Unit,
    // Null on the app's true start destination (nothing to go back to). Non-null when this
    // screen was reached via a push — e.g. the session screen's "Worlds" button, which no
    // longer pops its own live session off the back stack, so this is the only on-screen way
    // back to it besides the system Back button.
    onBack: (() -> Unit)? = null,
    viewModel: WorldAddressBookViewModel = viewModel(),
) {
    val groups by viewModel.groups.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val worlds by viewModel.worlds.collectAsState()
    val activeTabCount by viewModel.activeTabCount.collectAsState()

    // Selection doesn't carry meaning across a group switch — the previously selected row may
    // not even be visible in the new list.
    var selectedWorld by remember { mutableStateOf<World?>(null) }
    var editing by remember { mutableStateOf<World?>(null) }
    var showWorldEditor by remember { mutableStateOf(false) }
    var showNewGroup by remember { mutableStateOf(false) }
    var deletingGroup by remember { mutableStateOf<WorldGroup?>(null) }
    var deletingWorld by remember { mutableStateOf<World?>(null) }
    var permanentlyDeletingWorld by remember { mutableStateOf<World?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onBack?.let { TextButton(onClick = it) { Text("< Back") } }
                Text(text = "Address Book", style = MaterialTheme.typography.titleLarge)
            }
            SettingsIconButton(onClick = onOpenSettings)
        }

        // Only shown when a session is actually live — reassures that picking a world here
        // opens an additional tab rather than replacing the one already running in the
        // background, which isn't obvious from this screen alone (see SessionScreen's tab row).
        if (activeTabCount > 0) {
            Text(
                text = if (activeTabCount == 1) {
                    "1 connection active — picking a world opens another, it won't disconnect it."
                } else {
                    "$activeTabCount connections active — picking a world opens another, it won't disconnect them."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            GroupSidebar(
                groups = groups,
                selection = selection,
                onSelect = {
                    viewModel.selectGroup(it)
                    selectedWorld = null
                },
                onNewGroup = { showNewGroup = true },
                onDeleteGroup = { (selection as? GroupSelection.Named)?.let { deletingGroup = it.group } },
                modifier = Modifier.width(220.dp).fillMaxHeight(),
            )

            Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp)) {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(worlds, key = { it.world.id }) { row ->
                        WorldRow(
                            row = row,
                            selected = row.world.id == selectedWorld?.id,
                            onTap = {
                                selectedWorld = if (selectedWorld?.id == row.world.id) null else row.world
                            },
                            onDoubleTap = { onSelectWorld(row.world) },
                        )
                    }
                }

                if (selection == GroupSelection.Deleted) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { selectedWorld?.let { viewModel.restore(it); selectedWorld = null } },
                            enabled = selectedWorld != null,
                        ) { Text("Restore") }
                        Button(
                            onClick = { selectedWorld?.let { permanentlyDeletingWorld = it } },
                            enabled = selectedWorld != null,
                        ) { Text("Delete Permanently") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                editing = null
                                showWorldEditor = true
                            },
                        ) { Text("New World") }
                        Button(
                            onClick = { selectedWorld?.let(viewModel::copyWorld) },
                            enabled = selectedWorld != null,
                        ) { Text("Copy World") }
                        Button(
                            onClick = {
                                editing = selectedWorld
                                showWorldEditor = true
                            },
                            enabled = selectedWorld != null,
                        ) { Text("Edit World") }
                        Button(
                            onClick = { selectedWorld?.let(onSelectWorld) },
                            enabled = selectedWorld != null,
                        ) { Text("Connect To World") }
                        Button(
                            onClick = { selectedWorld?.let { deletingWorld = it } },
                            enabled = selectedWorld != null,
                        ) { Text("Delete") }
                    }
                }

                TextButton(onClick = onConnectManually) { Text("Connect without saving") }
            }
        }
    }

    if (showWorldEditor) {
        WorldEditorDialog(
            initial = editing,
            groups = groups,
            defaultGroupId = editing?.groupId ?: viewModel.currentGroupIdForNewWorld,
            onDismiss = { showWorldEditor = false },
            onSave = { name, host, port, groupId ->
                viewModel.save(editing?.id, name, host, port, groupId)
                showWorldEditor = false
            },
        )
    }

    if (showNewGroup) {
        NewGroupDialog(
            onDismiss = { showNewGroup = false },
            onSave = { name ->
                viewModel.newGroup(name)
                showNewGroup = false
            },
        )
    }

    deletingGroup?.let { group ->
        ConfirmDialog(
            title = "Delete group?",
            message = "\"${group.name}\" will be removed. Its worlds stay — they'll become Ungrouped.",
            onConfirm = {
                viewModel.deleteGroup(group)
                deletingGroup = null
            },
            onDismiss = { deletingGroup = null },
        )
    }

    deletingWorld?.let { world ->
        ConfirmDialog(
            title = "Delete world?",
            message = "\"${world.name}\" will be moved to Temp/Deleted. You can restore it later.",
            onConfirm = {
                viewModel.softDelete(world)
                selectedWorld = null
                deletingWorld = null
            },
            onDismiss = { deletingWorld = null },
        )
    }

    permanentlyDeletingWorld?.let { world ->
        ConfirmDialog(
            title = "Delete permanently?",
            message = "\"${world.name}\" and its characters will be permanently deleted. This cannot be undone.",
            confirmLabel = "Delete Permanently",
            onConfirm = {
                viewModel.permanentlyDelete(world)
                selectedWorld = null
                permanentlyDeletingWorld = null
            },
            onDismiss = { permanentlyDeletingWorld = null },
        )
    }
}

@Composable
private fun GroupSidebar(
    groups: List<WorldGroup>,
    selection: GroupSelection,
    onSelect: (GroupSelection) -> Unit,
    onNewGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            GroupSidebarRow("All Worlds", selection == GroupSelection.AllWorlds) { onSelect(GroupSelection.AllWorlds) }
            GroupSidebarRow("Ungrouped", selection == GroupSelection.Ungrouped) { onSelect(GroupSelection.Ungrouped) }
            groups.forEach { group ->
                GroupSidebarRow(group.name, selection == GroupSelection.Named(group)) {
                    onSelect(GroupSelection.Named(group))
                }
            }
            GroupSidebarRow("Temp/Deleted", selection == GroupSelection.Deleted) { onSelect(GroupSelection.Deleted) }
        }
        TextButton(onClick = onNewGroup, modifier = Modifier.fillMaxWidth()) { Text("New Group") }
        TextButton(
            onClick = onDeleteGroup,
            enabled = selection is GroupSelection.Named,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Delete Group") }
    }
}

@Composable
private fun GroupSidebarRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
    ) {
        Text(label)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorldRow(
    row: WorldWithLastCharacter,
    selected: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val world = row.world
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onTap, onDoubleClick = onDoubleTap)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = world.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${world.host}:${world.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = row.lastCharacterName ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorldEditorDialog(
    initial: World?,
    groups: List<WorldGroup>,
    defaultGroupId: Long?,
    onDismiss: () -> Unit,
    onSave: (name: String, host: String, port: Int, groupId: Long?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var host by remember { mutableStateOf(initial?.host ?: "") }
    var port by remember { mutableStateOf(initial?.port?.toString() ?: "") }
    var groupId by remember { mutableStateOf(initial?.groupId ?: defaultGroupId) }

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
                GroupSelector(groups = groups, selectedGroupId = groupId, onSelect = { groupId = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, host, portValue!!, groupId) }, enabled = isValid) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun GroupSelector(
    groups: List<WorldGroup>,
    selectedGroupId: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "None"

    Column {
        Text(
            text = "Group",
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
                    text = { Text("None") },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
                groups.forEach { group ->
                    DropdownMenuItem(
                        text = { Text(group.name) },
                        onClick = {
                            onSelect(group.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NewGroupDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Group") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

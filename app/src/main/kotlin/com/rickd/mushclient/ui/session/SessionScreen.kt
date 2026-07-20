package com.rickd.mushclient.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rickd.mushclient.telnet.ConnectionState
import com.rickd.mushclient.ui.common.SettingsIconButton
import com.rickd.mushclient.ui.theme.TerminalGreen
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun SessionScreen(
    initialHost: String? = null,
    initialPort: String? = null,
    worldName: String? = null,
    autoLoginCharacterName: String? = null,
    autoLoginCharacterPassword: String? = null,
    worldId: Long? = null,
    onNavigateToWorlds: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SessionViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // No specific target was passed in (a bare re-entry, or "Connect without saving"): if tabs
    // are already open (e.g. the user navigated away to Worlds/Settings and came back), land on
    // the active one rather than hiding it behind a blank form — that blank form is only the
    // right default when there's truly nothing else to show yet. `draftOverride` is null until
    // the user explicitly taps "+" or a tab, at which point their choice wins regardless of
    // tab count; until then this recomputes live as uiState.tabs loads in.
    var draftOverride by remember { mutableStateOf<Boolean?>(null) }
    val showDraft = draftOverride ?: (initialHost == null && uiState.tabs.isEmpty())
    var draftHost by remember { mutableStateOf(initialHost ?: "") }
    var draftPort by remember { mutableStateOf(initialPort ?: "") }

    // Keyed by connection id so switching tabs doesn't leak one tab's unsent draft, history
    // position, or scroll offset into another's.
    val inputDrafts = remember { mutableStateMapOf<Long, String>() }
    val historyIndices = remember { mutableStateMapOf<Long, Int>() }
    val draftsBeforeHistory = remember { mutableStateMapOf<Long, String>() }
    val listStates = remember { mutableStateMapOf<Long, LazyListState>() }

    // A character was picked (not just a world): dial immediately, server-side auto-login
    // handles sending the login line once this specific tab reaches CONNECTED. Picking a bare
    // world never auto-connects — only an explicit character selection implies "log me in now".
    LaunchedEffect(Unit) {
        if (initialHost != null) {
            viewModel.openInitialTabIfNeeded(
                host = initialHost,
                port = initialPort?.toIntOrNull() ?: return@LaunchedEffect,
                worldId = worldId,
                worldName = worldName,
                characterName = autoLoginCharacterName,
                characterPassword = autoLoginCharacterPassword,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Always visible regardless of tab state — without this, closing every tab (or a
            // bare "Connect without saving" entry) leaves no on-screen way back to Worlds,
            // only the system Back button, which isn't reliable once repeated tab-opens have
            // rewritten the back stack (see navigateToSession's popUpTo).
            TextButton(onClick = onNavigateToWorlds) { Text("Worlds") }
            Box(modifier = Modifier.weight(1f)) {
                TabRow(
                    tabs = uiState.tabs,
                    activeConnectionId = uiState.activeConnectionId,
                    showDraft = showDraft,
                    onSelectTab = { draftOverride = false; viewModel.setActiveTab(it) },
                    onCloseTab = { id ->
                        viewModel.closeTab(id)
                        inputDrafts.remove(id)
                        historyIndices.remove(id)
                        draftsBeforeHistory.remove(id)
                        listStates.remove(id)
                    },
                    onNewConnection = { draftOverride = true; draftHost = ""; draftPort = "" },
                )
            }
            // Reachable from the terminal itself — e.g. checking a character's password or a
            // different world's host:port to hand to someone, then Back returns straight here.
            SettingsIconButton(onClick = onOpenSettings)
        }

        if (showDraft) {
            ConnectionBar(
                host = draftHost,
                onHostChange = { draftHost = it },
                port = draftPort,
                onPortChange = { draftPort = it },
                connectionState = ConnectionState.DISCONNECTED,
                onConnect = {
                    val port = draftPort.toIntOrNull() ?: return@ConnectionBar
                    viewModel.openTab(draftHost, port)
                    draftOverride = false
                },
                onDisconnect = {},
            )
            Text(
                text = "Enter a host and port, then tap Connect to start a new connection.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val activeId = uiState.activeConnectionId
            if (activeId == null) {
                Text(
                    text = "No connections. Tap + above, or go to Worlds to connect.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                ActiveSession(
                    connectionId = activeId,
                    uiState = uiState.active,
                    viewModel = viewModel,
                    inputText = inputDrafts[activeId] ?: "",
                    onInputTextChange = { inputDrafts[activeId] = it },
                    historyIndex = historyIndices[activeId] ?: -1,
                    onHistoryIndexChange = { historyIndices[activeId] = it },
                    draftBeforeHistory = draftsBeforeHistory[activeId] ?: "",
                    onDraftBeforeHistoryChange = { draftsBeforeHistory[activeId] = it },
                    listState = listStates.getOrPut(activeId) { LazyListState() },
                )
            }
        }
    }
}

@Composable
private fun TabRow(
    tabs: List<TabMeta>,
    activeConnectionId: Long?,
    showDraft: Boolean,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onNewConnection: () -> Unit,
) {
    val selectedIndex = if (showDraft) {
        tabs.size
    } else {
        tabs.indexOfFirst { it.id == activeConnectionId }.let { if (it == -1) tabs.size else it }
    }
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 0.dp,
        // The default indicator indexes tabPositions[selectedTabIndex] unconditionally, which
        // crashes for one transient frame whenever the tab count and selectedIndex change
        // together (e.g. right after opening or closing a tab) — bounds-check it ourselves.
        indicator = { tabPositions ->
            tabPositions.getOrNull(selectedIndex)?.let { position ->
                with(TabRowDefaults) {
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(position))
                }
            }
        },
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = !showDraft && tab.id == activeConnectionId,
                onClick = { onSelectTab(tab.id) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(text = tab.label, maxLines = 1)
                        if (tab.hasError) {
                            Text(text = "!", color = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { onCloseTab(tab.id) }, modifier = Modifier.size(20.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close ${tab.label}",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                },
            )
        }
        Tab(
            selected = showDraft,
            onClick = onNewConnection,
            text = { Icon(Icons.Default.Add, contentDescription = "New connection") },
        )
    }
}

@Composable
private fun ColumnScope.ActiveSession(
    connectionId: Long,
    uiState: SessionUiState,
    viewModel: SessionViewModel,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    historyIndex: Int,
    onHistoryIndexChange: (Int) -> Unit,
    draftBeforeHistory: String,
    onDraftBeforeHistoryChange: (String) -> Unit,
    listState: LazyListState,
) {
    // Local editable copy of this tab's host/port — reseeded whenever the tab itself changes,
    // but not on every service update, so editing while e.g. RECONNECTING isn't clobbered.
    var editHost by remember(connectionId) { mutableStateOf(uiState.host) }
    var editPort by remember(connectionId) { mutableStateOf(uiState.port) }

    LaunchedEffect(connectionId, uiState.lines.size) {
        if (uiState.lines.isNotEmpty()) {
            listState.animateScrollToItem(uiState.lines.size - 1)
        }
    }

    // A Trigger's "Input" action landing in the box — keyed on the nonce (not the text) so
    // firing the same text twice in a row still re-applies it.
    LaunchedEffect(connectionId, uiState.pendingInputNonce) {
        if (uiState.pendingInputNonce > 0) {
            onInputTextChange(uiState.pendingInputText ?: "")
            onHistoryIndexChange(-1)
        }
    }

    ConnectionBar(
        host = editHost,
        onHostChange = { editHost = it },
        port = editPort,
        onPortChange = { editPort = it },
        connectionState = uiState.connectionState,
        onConnect = { viewModel.connect(connectionId, editHost, editPort.toIntOrNull() ?: return@ConnectionBar) },
        onDisconnect = { viewModel.disconnect(connectionId) },
    )

    uiState.errorMessage?.let { message ->
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        items(uiState.lines) { line ->
            // TerminalGreen is the default; ANSI SpanStyles in `line` override it per-run.
            Text(text = line, fontFamily = FontFamily.Monospace, color = TerminalGreen)
        }
    }

    InputBar(
        value = inputText,
        onValueChange = onInputTextChange,
        enabled = uiState.connectionState == ConnectionState.CONNECTED,
        canNavigateOlder = uiState.history.isNotEmpty() && historyIndex < uiState.history.lastIndex,
        canNavigateNewer = historyIndex >= 0,
        onNavigateOlder = {
            if (historyIndex == -1) onDraftBeforeHistoryChange(inputText)
            val next = (historyIndex + 1).coerceAtMost(uiState.history.lastIndex)
            onHistoryIndexChange(next)
            onInputTextChange(uiState.history[next])
        },
        onNavigateNewer = {
            if (historyIndex > 0) {
                onHistoryIndexChange(historyIndex - 1)
                onInputTextChange(uiState.history[historyIndex - 1])
            } else if (historyIndex == 0) {
                onHistoryIndexChange(-1)
                onInputTextChange(draftBeforeHistory)
            }
        },
        onSend = {
            viewModel.send(connectionId, inputText)
            onInputTextChange("")
            onHistoryIndexChange(-1)
        },
    )
}

@Composable
private fun ConnectionBar(
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = onHostChange,
                label = { Text("Host") },
                singleLine = true,
                enabled = connectionState == ConnectionState.DISCONNECTED,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                singleLine = true,
                enabled = connectionState == ConnectionState.DISCONNECTED,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            when (connectionState) {
                ConnectionState.CONNECTED -> Button(onClick = onDisconnect) { Text("Disconnect") }
                ConnectionState.CONNECTING -> Button(onClick = {}, enabled = false) { Text("Connecting…") }
                ConnectionState.RECONNECTING -> Button(onClick = onDisconnect) { Text("Reconnecting… (cancel)") }
                ConnectionState.DISCONNECTED -> Button(onClick = onConnect) { Text("Connect") }
            }
        }
        Text(text = "Status: ${connectionState.name}")
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    canNavigateOlder: Boolean,
    canNavigateNewer: Boolean,
    onNavigateOlder: () -> Unit,
    onNavigateNewer: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Recalls previously sent commands into the input, like a shell's up/down history —
        // independent of `enabled` so you can line up your next command while reconnecting.
        IconButton(onClick = onNavigateOlder, enabled = canNavigateOlder) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous command")
        }
        IconButton(onClick = onNavigateNewer, enabled = canNavigateNewer) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next command")
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (value.isNotEmpty()) onSend() }),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onSend, enabled = enabled && value.isNotEmpty()) {
            Text("Send")
        }
    }
}

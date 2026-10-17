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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.rickd.mushclient.service.SessionLog
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.rickd.mushclient.telnet.ConnectionState
import com.rickd.mushclient.ui.common.SettingsIconButton
import com.rickd.mushclient.ui.theme.TerminalGreen
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

// Same two colors MushTato uses: cyan = the tab you're in, orange blink = unseen activity.
private val ACTIVE_TAB_COLOR = Color(0xFF4EC9F5)
private val ACTIVITY_TAB_COLOR = Color(0xFFFFA500)
private const val FLASH_INTERVAL_MS = 500L

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
                    tab = uiState.tabs.firstOrNull { it.id == activeId },
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
    // One shared blink clock for every flashing tab, so several blink together instead of
    // drifting apart. Indefinite until the tab is viewed (same as MushTato); the clock only
    // runs while at least one tab actually has unseen activity.
    var flashOn by remember { mutableStateOf(true) }
    val anyActivity = tabs.any { it.hasUnseenActivity }
    LaunchedEffect(anyActivity) {
        flashOn = true
        while (anyActivity) {
            delay(FLASH_INTERVAL_MS)
            flashOn = !flashOn
        }
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
                    val isActive = !showDraft && tab.id == activeConnectionId
                    // Steady cyan for the tab in view; blinking orange (in sync across tabs) for
                    // a background tab with unseen server text; otherwise the default color.
                    val labelColor = when {
                        isActive -> ACTIVE_TAB_COLOR
                        tab.hasUnseenActivity && flashOn -> ACTIVITY_TAB_COLOR
                        else -> Color.Unspecified
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Column {
                            Text(text = tab.label, maxLines = 1, color = labelColor)
                            tab.characterName?.let { name ->
                                Text(
                                    text = name,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = labelColor,
                                )
                            }
                        }
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
            text = {
                // Labeled, not just an icon — with only one tab open (the common case) a bare
                // "+" reads as decoration rather than "open another connection alongside this
                // one", which is exactly the affordance that needs to be found.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("New Connection")
                }
            },
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
    tab: TabMeta?,
) {
    // Local editable copy of this tab's host/port — reseeded whenever the tab itself changes,
    // but not on every service update, so editing while e.g. RECONNECTING isn't clobbered.
    var editHost by remember(connectionId) { mutableStateOf(uiState.host) }
    var editPort by remember(connectionId) { mutableStateOf(uiState.port) }

    // Save Log uses the system "Save as" picker (Storage Access Framework): no storage
    // permission needed, and the player chooses where the file goes.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingLog by remember(connectionId) { mutableStateOf<String?>(null) }
    val saveLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        val text = pendingLog
        pendingLog = null
        if (uri != null && text != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    }.isSuccess
                }
                Toast.makeText(
                    context,
                    if (saved) "Log saved" else "Couldn't save the log",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // Find state is per tab and lives here (not in FindBar) so it survives tab switches.
    var findOpen by remember(connectionId) { mutableStateOf(false) }
    var findQuery by remember(connectionId) { mutableStateOf("") }
    var findCaseSensitive by remember(connectionId) { mutableStateOf(false) }
    var findCurrent by remember(connectionId) { mutableStateOf(-1) }
    val matches = remember(uiState.lines, findOpen, findQuery, findCaseSensitive) {
        if (findOpen) findMatches(uiState.lines.map { it.text }, findQuery, findCaseSensitive) else emptyList()
    }
    val matchesByLine = remember(matches) { matches.groupBy { it.line } }
    // Lines only ever append, so an index into `matches` stays valid as text arrives; clamp
    // for the one case it can shrink (the scrollback being cleared).
    val current = findCurrent.coerceIn(-1, matches.lastIndex)
    val searching = findOpen && findQuery.isNotEmpty()

    // A new query starts at the most recent match — in a MUD the text you want is usually the
    // newest — and Previous walks back through older ones.
    LaunchedEffect(findOpen, findQuery, findCaseSensitive) { findCurrent = matches.lastIndex }
    LaunchedEffect(current, findQuery) {
        matches.getOrNull(current)?.let { listState.animateScrollToItem(it.line) }
    }
    // Back closes the find bar first, before it would navigate away.
    BackHandler(enabled = findOpen) { findOpen = false }

    LaunchedEffect(connectionId, uiState.lines.size) {
        // Don't yank the view to the bottom while the player is reading search results.
        if (uiState.lines.isNotEmpty() && !searching) {
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
        showTimestamps = uiState.showTimestamps,
        onToggleTimestamps = { viewModel.setTimestamps(connectionId, null) },
        findOpen = findOpen,
        onToggleFind = { findOpen = !findOpen },
        canSaveLog = uiState.lines.isNotEmpty(),
        onSaveLog = {
            // Snapshot now, so the file holds what was on screen when Save was tapped even if
            // more text arrives while the system file picker is open.
            val now = LocalDateTime.now()
            val label = tab?.label ?: "${uiState.host}:${uiState.port}"
            pendingLog = SessionLog.build(
                label, uiState.host, uiState.port, tab?.characterName,
                uiState.lines.map { it.text }, now,
            )
            saveLogLauncher.launch(SessionLog.fileName(label, now))
        },
    )

    uiState.errorMessage?.let { message ->
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }

    if (findOpen) {
        FindBar(
            query = findQuery,
            onQueryChange = { findQuery = it },
            caseSensitive = findCaseSensitive,
            onToggleCase = { findCaseSensitive = !findCaseSensitive },
            matchCount = matches.size,
            currentIndex = current,
            onPrevious = { findCurrent = stepIndex(current, matches.size, -1) },
            onNext = { findCurrent = stepIndex(current, matches.size, +1) },
            onClose = { findOpen = false },
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        itemsIndexed(uiState.lines) { index, line ->
            val lineMatches = matchesByLine[index]
            val currentOnLine = matches.getOrNull(current)?.takeIf { it.line == index }
            // TerminalGreen is the default; ANSI SpanStyles in `line` override it per-run.
            val rendered = remember(line, lineMatches, currentOnLine) {
                line.withUrlLinks().withFindHighlights(lineMatches, currentOnLine)
            }
            Text(text = rendered, fontFamily = FontFamily.Monospace, color = TerminalGreen)
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
    // Null on the draft "new connection" form, which has no session to apply timestamps to.
    showTimestamps: Boolean = false,
    onToggleTimestamps: (() -> Unit)? = null,
    findOpen: Boolean = false,
    onToggleFind: (() -> Unit)? = null,
    canSaveLog: Boolean = false,
    onSaveLog: (() -> Unit)? = null,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Status: ${connectionState.name}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onToggleFind?.let { toggle ->
                    FilterChip(selected = findOpen, onClick = toggle, label = { Text("Find") })
                }
                onSaveLog?.let { save ->
                    AssistChip(onClick = save, enabled = canSaveLog, label = { Text("Save Log") })
                }
                onToggleTimestamps?.let { toggle ->
                    FilterChip(
                        selected = showTimestamps,
                        onClick = toggle,
                        label = { Text("Timestamps") },
                    )
                }
            }
        }
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

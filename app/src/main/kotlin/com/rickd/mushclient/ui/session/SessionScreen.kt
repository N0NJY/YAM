package com.rickd.mushclient.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rickd.mushclient.telnet.ConnectionState
import com.rickd.mushclient.ui.theme.TerminalGreen
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun SessionScreen(
    initialHost: String? = null,
    initialPort: String? = null,
    autoLoginCharacterName: String? = null,
    autoLoginCharacterPassword: String? = null,
    viewModel: SessionViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var host by remember { mutableStateOf(initialHost ?: "") }
    var port by remember { mutableStateOf(initialPort ?: "") }
    var inputText by remember { mutableStateOf("") }
    var autoLoginPending by remember { mutableStateOf(autoLoginCharacterName != null) }
    val listState = rememberLazyListState()

    // A character was picked (not just a world): dial immediately and, once connected, send
    // its login line once. Picking a world alone never auto-connects — only an explicit
    // character selection implies "log me in as this specific character now". The password
    // is only ever sent over the wire — echoAs keeps it out of the visible scrollback and
    // the tappable command history.
    LaunchedEffect(Unit) {
        if (autoLoginCharacterName != null) {
            viewModel.connect(host, port.toIntOrNull() ?: return@LaunchedEffect)
        }
    }
    LaunchedEffect(uiState.connectionState) {
        if (autoLoginPending && uiState.connectionState == ConnectionState.CONNECTED) {
            autoLoginPending = false
            viewModel.send(
                text = "connect $autoLoginCharacterName $autoLoginCharacterPassword",
                echoAs = "connect $autoLoginCharacterName ********",
            )
        }
    }

    LaunchedEffect(uiState.lines.size) {
        if (uiState.lines.isNotEmpty()) {
            listState.animateScrollToItem(uiState.lines.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConnectionBar(
            host = host,
            onHostChange = { host = it },
            port = port,
            onPortChange = { port = it },
            connectionState = uiState.connectionState,
            onConnect = { viewModel.connect(host, port.toIntOrNull() ?: return@ConnectionBar) },
            onDisconnect = viewModel::disconnect,
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

        if (uiState.history.isNotEmpty()) {
            HistoryRow(history = uiState.history, onPick = { inputText = it })
        }

        InputBar(
            value = inputText,
            onValueChange = { inputText = it },
            enabled = uiState.connectionState == ConnectionState.CONNECTED,
            onSend = {
                viewModel.send(inputText)
                inputText = ""
            },
        )
    }
}

@Composable
private fun HistoryRow(history: List<String>, onPick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(history) { command ->
            // Tapping fills the input rather than sending immediately — a MUSH command
            // (e.g. a kill/drop) shouldn't fire again from a single accidental tap.
            SuggestionChip(onClick = { onPick(command) }, label = { Text(command) })
        }
    }
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
                enabled = connectionState != ConnectionState.CONNECTED,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                singleLine = true,
                enabled = connectionState != ConnectionState.CONNECTED,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            when (connectionState) {
                ConnectionState.CONNECTED -> Button(onClick = onDisconnect) { Text("Disconnect") }
                ConnectionState.CONNECTING -> Button(onClick = {}, enabled = false) { Text("Connecting…") }
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
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

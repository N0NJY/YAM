package com.rickd.mushclient.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rickd.mushclient.telnet.ConnectionState
import com.rickd.mushclient.telnet.TelnetClient
import com.rickd.mushclient.telnet.TelnetEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val lines: List<String> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Turns the byte/event-level [TelnetClient] into scrollback the UI can render: buffers
 * partial lines across chunks (servers don't align text on line boundaries) and echoes
 * sent commands into the same scrollback, MUD-client style.
 */
class SessionViewModel : ViewModel() {

    private val telnetClient = TelnetClient(viewModelScope)
    private val pendingLine = StringBuilder()

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            telnetClient.events.collect(::handleEvent)
        }
    }

    fun connect(host: String, port: Int) {
        if (host.isBlank()) return
        _uiState.update { it.copy(errorMessage = null) }
        viewModelScope.launch {
            telnetClient.connect(host, port)
        }
    }

    fun disconnect() {
        telnetClient.disconnect()
    }

    fun send(text: String) {
        if (text.isEmpty()) return
        _uiState.update { it.copy(lines = it.lines + "> $text") }
        viewModelScope.launch {
            telnetClient.send(text)
        }
    }

    private fun handleEvent(event: TelnetEvent) {
        when (event) {
            is TelnetEvent.Text -> appendText(event.chunk)
            is TelnetEvent.StateChanged -> _uiState.update { it.copy(connectionState = event.state) }
            is TelnetEvent.Error -> _uiState.update { it.copy(errorMessage = event.message) }
            is TelnetEvent.Subnegotiation -> Unit // not interpreted yet (GMCP/MSDP land later)
        }
    }

    private fun appendText(chunk: String) {
        pendingLine.append(chunk)
        val parts = pendingLine.toString().replace("\r\n", "\n").replace("\r", "\n").split("\n")
        pendingLine.clear()
        pendingLine.append(parts.last())
        val completeLines = parts.dropLast(1)
        if (completeLines.isNotEmpty()) {
            _uiState.update { it.copy(lines = it.lines + completeLines) }
        }
    }

    override fun onCleared() {
        telnetClient.disconnect()
    }
}

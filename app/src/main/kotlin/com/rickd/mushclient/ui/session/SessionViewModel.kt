package com.rickd.mushclient.ui.session

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rickd.mushclient.service.MudConnectionService
import com.rickd.mushclient.telnet.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val lines: List<AnnotatedString> = emptyList(),
    val history: List<String> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Thin adapter between the UI and [MudConnectionService], which owns the actual telnet
 * connection so it survives this ViewModel (and the Activity) being torn down. Binds to
 * the service on creation and mirrors its state; [disconnect]/[send] forward to the bound
 * service, while [connect] starts the service as a foreground service (not just a bind) —
 * that's what keeps the session alive after the UI unbinds.
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var service: MudConnectionService? = null
    private var mirrorJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val bound = (binder as MudConnectionService.LocalBinder).getService()
            service = bound
            mirrorJob = viewModelScope.launch {
                bound.uiState.collect { _uiState.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            mirrorJob?.cancel()
        }
    }

    init {
        val context = getApplication<Application>()
        context.bindService(
            Intent(context, MudConnectionService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
    }

    fun connect(host: String, port: Int) {
        if (host.isBlank()) return
        val context = getApplication<Application>()
        val intent = Intent(context, MudConnectionService::class.java)
            .putExtra(MudConnectionService.EXTRA_HOST, host)
            .putExtra(MudConnectionService.EXTRA_PORT, port)
        ContextCompat.startForegroundService(context, intent)
    }

    fun disconnect() {
        service?.disconnect()
    }

    fun send(text: String, echoAs: String? = redactIfLoginCommand(text)) {
        service?.send(text, echoAs)
    }

    override fun onCleared() {
        // Deliberately don't disconnect here: unbinding must not kill a live session,
        // that's the whole point of the service being separately "started".
        getApplication<Application>().unbindService(connection)
    }
}

// A player typing "connect <name> <password>" (or "co"/"create"/"cr") straight into the
// input bar isn't going through the auto-login path's echoAs=null, so without this the raw
// password would otherwise land in the scrollback and in the tappable history row.
private val LOGIN_COMMAND_REGEX = Regex("""(?is)^\s*(connect|co|create|cr)\s+(\S+)\s+\S.*$""")

private fun redactIfLoginCommand(text: String): String {
    val match = LOGIN_COMMAND_REGEX.matchEntire(text) ?: return text
    val (verb, name) = match.destructured
    return "$verb $name ********"
}

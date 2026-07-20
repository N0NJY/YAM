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
    // The tab's own last-dialed (or pre-filled) address — lets the UI show/edit it per-tab
    // without a separate round trip, since typing into the fields can't go through the Service.
    val host: String = "",
    val port: String = "",
    val lines: List<AnnotatedString> = emptyList(),
    val history: List<String> = emptyList(),
    val errorMessage: String? = null,
    // A Trigger's "Input" action landing text in the input box. `pendingInputNonce` increments
    // on every fire (even a repeat of the same text) so the UI's LaunchedEffect always re-runs.
    val pendingInputText: String? = null,
    val pendingInputNonce: Long = 0,
)

/** Cheap per-tab summary for the tab row — rarely changes, unlike the active tab's full state. */
data class TabMeta(
    val id: Long,
    val label: String,
    val connectionState: ConnectionState,
    val hasError: Boolean,
)

data class SessionScreenState(
    val tabs: List<TabMeta> = emptyList(),
    val activeConnectionId: Long? = null,
    // Full scrollback/history of the active tab only — see MudConnectionService.activeUiState
    // for why every tab's state isn't aggregated here.
    val active: SessionUiState = SessionUiState(),
)

/**
 * Thin adapter between the UI and [MudConnectionService], which owns every live connection
 * ("tab") so they survive this ViewModel (and the Activity) being torn down. Binds to the
 * service on creation and mirrors its aggregated state; [disconnect]/[send]/[setActiveTab]/
 * [closeTab] forward to the bound service by connection id, while [openTab] starts the
 * service as a foreground service (not just a bind) to create a new one — that's what keeps
 * a session alive after the UI unbinds, not the binding itself.
 */
class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SessionScreenState())
    val uiState: StateFlow<SessionScreenState> = _uiState.asStateFlow()

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

    // Guards openInitialTabIfNeeded below — survives Compose disposing/recomposing the screen
    // (e.g. navigating away to Worlds and back via system Back), unlike a plain `remember` in
    // the Composable, since this ViewModel instance lives as long as the nav back-stack entry.
    private var hasOpenedInitialTab = false

    /**
     * Opens the tab this screen was launched with, but only the first time this ViewModel
     * instance sees it — a second call (from the Composable's `LaunchedEffect(Unit)` re-running
     * after the screen is disposed and recomposed, which happens on any trip away and back) is
     * a no-op, so returning to a live session via Back doesn't silently open a duplicate tab to
     * the same world.
     */
    fun openInitialTabIfNeeded(
        host: String,
        port: Int,
        worldId: Long? = null,
        worldName: String? = null,
        characterName: String? = null,
        characterPassword: String? = null,
    ) {
        if (hasOpenedInitialTab) return
        hasOpenedInitialTab = true
        openTab(host, port, worldId, worldName, characterName, characterPassword)
    }

    /** Always opens a brand-new tab — see the tabs plan's "no reuse dedup" decision. */
    fun openTab(
        host: String,
        port: Int,
        worldId: Long? = null,
        worldName: String? = null,
        characterName: String? = null,
        characterPassword: String? = null,
    ) {
        if (host.isBlank()) return
        val context = getApplication<Application>()
        val intent = Intent(context, MudConnectionService::class.java)
            .putExtra(MudConnectionService.EXTRA_HOST, host)
            .putExtra(MudConnectionService.EXTRA_PORT, port)
        if (worldId != null) intent.putExtra(MudConnectionService.EXTRA_WORLD_ID, worldId)
        if (worldName != null) intent.putExtra(MudConnectionService.EXTRA_WORLD_NAME, worldName)
        if (characterName != null) intent.putExtra(MudConnectionService.EXTRA_CHAR_NAME, characterName)
        if (characterPassword != null) intent.putExtra(MudConnectionService.EXTRA_CHAR_PASSWORD, characterPassword)
        ContextCompat.startForegroundService(context, intent)
    }

    fun connect(connectionId: Long, host: String, port: Int) {
        service?.connect(connectionId, host, port)
    }

    fun disconnect(connectionId: Long) {
        service?.disconnect(connectionId)
    }

    fun send(connectionId: Long, text: String, echoAs: String? = redactIfLoginCommand(text)) {
        service?.send(connectionId, text, echoAs)
    }

    fun setActiveTab(connectionId: Long) {
        service?.setActiveConnection(connectionId)
    }

    fun closeTab(connectionId: Long) {
        service?.closeConnection(connectionId)
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

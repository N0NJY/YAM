package com.rickd.mushclient.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.rickd.mushclient.MainActivity
import com.rickd.mushclient.R
import com.rickd.mushclient.ansi.AnsiParser
import com.rickd.mushclient.data.AppPreferences
import com.rickd.mushclient.data.MudDatabase
import com.rickd.mushclient.data.RoomPinStore
import com.rickd.mushclient.data.SslPinDao
import com.rickd.mushclient.telnet.CertificatePinning
import com.rickd.mushclient.data.SlashCommand
import com.rickd.mushclient.data.SlashCommandDao
import com.rickd.mushclient.data.Trigger
import com.rickd.mushclient.data.TriggerDao
import com.rickd.mushclient.data.WorldDao
import com.rickd.mushclient.telnet.ConnectionState
import com.rickd.mushclient.telnet.TelnetClient
import com.rickd.mushclient.telnet.TelnetEvent
import com.rickd.mushclient.trigger.BuiltInAction
import com.rickd.mushclient.trigger.SlashCommandEngine
import java.time.LocalDateTime
import java.time.LocalTime
import com.rickd.mushclient.trigger.SlashResolution
import com.rickd.mushclient.trigger.TriggerEngine
import com.rickd.mushclient.ui.session.SessionScreenState
import com.rickd.mushclient.ui.session.SessionUiState
import com.rickd.mushclient.ui.session.TabMeta
import com.rickd.mushclient.ui.theme.TerminalSystemYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service owning every live telnet connection ("tab") so sessions survive the
 * Activity being backgrounded or destroyed on a config change. Each tab is a
 * [ConnectionSession] with its own [TelnetClient], reconnect/trigger/slash-command state,
 * and [SessionUiState]; this class only aggregates them ([sessions], [_tabs],
 * [_activeConnectionId]) and owns cross-tab concerns (the one foreground notification,
 * "should we still be foreground" — see [maybeStopIfIdle]). The UI binds to it for direct
 * calls (disconnect/send, all keyed by connection id) and state observation, but a *new* tab
 * is only ever created via [onStartCommand] (started with `startForegroundService`) — that's
 * what keeps this service alive after the UI unbinds, not the binding itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MudConnectionService : LifecycleService() {

    private val binder = LocalBinder()
    private lateinit var appPreferences: AppPreferences
    private lateinit var triggerDao: TriggerDao
    private lateinit var slashCommandDao: SlashCommandDao
    private lateinit var worldDao: WorldDao
    private lateinit var sslPinDao: SslPinDao

    private val sessions = mutableMapOf<Long, ConnectionSession>()
    private var nextConnectionId = 1L

    private val _tabs = MutableStateFlow<List<TabMeta>>(emptyList())
    private val _activeConnectionId = MutableStateFlow<Long?>(null)

    // Only the *active* tab's full scrollback/history is exposed — aggregating every tab's
    // full SessionUiState into one object would mean a chatty background tab triggers
    // recomposition on every incoming line even though the visible tab didn't change.
    private val activeUiState: Flow<SessionUiState> = _activeConnectionId.flatMapLatest { id ->
        id?.let { sessions[it]?.uiState } ?: flowOf(SessionUiState())
    }

    val uiState: StateFlow<SessionScreenState> =
        combine(_tabs, _activeConnectionId, activeUiState) { tabs, activeId, active ->
            SessionScreenState(tabs, activeId, active)
        }.stateIn(lifecycleScope, SharingStarted.Eagerly, SessionScreenState())

    inner class LocalBinder : Binder() {
        fun getService(): MudConnectionService = this@MudConnectionService
    }

    override fun onCreate() {
        super.onCreate()
        appPreferences = AppPreferences.get(applicationContext)
        triggerDao = MudDatabase.get(applicationContext).triggerDao()
        slashCommandDao = MudDatabase.get(applicationContext).slashCommandDao()
        worldDao = MudDatabase.get(applicationContext).worldDao()
        sslPinDao = MudDatabase.get(applicationContext).sslPinDao()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val host = intent?.getStringExtra(EXTRA_HOST)
        val port = intent?.getIntExtra(EXTRA_PORT, -1) ?: -1
        val worldId = intent?.getLongExtra(EXTRA_WORLD_ID, -1L)?.takeIf { it > 0 }
        val worldName = intent?.getStringExtra(EXTRA_WORLD_NAME)
        val charName = intent?.getStringExtra(EXTRA_CHAR_NAME)
        val charPassword = intent?.getStringExtra(EXTRA_CHAR_PASSWORD)
        if (host != null && port > 0) {
            openConnection(host, port, worldId, worldName, charName, charPassword)
        }
        // Opening a tab (if any) already updated _tabs above, so the very first notification
        // reflects it immediately instead of a generic "Connecting…" that a moment later gets
        // stomped by the real aggregate — see startForegroundService/onStartCommand contract.
        startForeground(NOTIFICATION_ID, buildAggregateNotification())
        return START_NOT_STICKY
    }

    /** Always creates a brand-new tab — there's no "reuse a matching existing tab" dedup. */
    private fun openConnection(
        host: String,
        port: Int,
        worldId: Long?,
        worldName: String?,
        charName: String?,
        charPassword: String?,
    ) {
        val id = nextConnectionId++
        val label = worldName ?: "$host:$port"
        val session = ConnectionSession(id, worldId)
        sessions[id] = session
        addTab(id, label)
        session.start()
        if (charName != null && charPassword != null) {
            // Picking a specific character is explicit intent to connect now (the one
            // exception to "never auto-connect") — everything else just pre-fills.
            session.pendingAutoLogin = LoginCredentials(charName, charPassword)
            session.connect(host, port)
        } else {
            session.prefill(host, port)
        }
        // Set active last: the UI's active-tab flow re-subscribes the instant this changes, so
        // the very first SessionUiState it ever sees for this tab should already carry the
        // prefilled/connecting host and port — not the session's brand-new empty default.
        setActiveConnection(id)
    }

    fun connect(connectionId: Long, host: String, port: Int) {
        sessions[connectionId]?.connect(host, port)
    }

    fun disconnect(connectionId: Long) {
        sessions[connectionId]?.disconnect()
    }

    fun send(connectionId: Long, text: String, echoAs: String? = text) {
        sessions[connectionId]?.send(text, echoAs)
    }

    fun setTimestamps(connectionId: Long, enabled: Boolean?) {
        sessions[connectionId]?.setTimestamps(enabled)
    }

    fun moveTab(connectionId: Long, toIndex: Int) {
        _tabs.update { it.withMoved(connectionId, toIndex) }
    }

    fun setActiveConnection(connectionId: Long) {
        _activeConnectionId.value = connectionId
        _tabs.update { it.withActivityCleared(connectionId) }
    }

    /** The only thing that actually removes a tab — a naturally-disconnected one just sits there. */
    fun closeConnection(connectionId: Long) {
        sessions[connectionId]?.dispose()
        sessions.remove(connectionId)
        _tabs.update { tabs -> tabs.filterNot { it.id == connectionId } }
        if (_activeConnectionId.value == connectionId) {
            _tabs.value.lastOrNull()?.id?.let(::setActiveConnection) ?: run { _activeConnectionId.value = null }
        }
        refreshNotification()
        maybeStopIfIdle()
    }

    private fun addTab(id: Long, label: String) {
        _tabs.update { it + TabMeta(id, label, ConnectionState.DISCONNECTED, hasError = false) }
    }

    /** Reactive hook: every [ConnectionSession] pushes its own state changes through here. */
    private fun updateTab(id: Long, connectionState: ConnectionState, hasError: Boolean) {
        val current = _tabs.value.firstOrNull { it.id == id }
        if (current != null && current.connectionState == connectionState && current.hasError == hasError) return
        _tabs.update { tabs -> tabs.map { if (it.id == id) it.copy(connectionState = connectionState, hasError = hasError) else it } }
        refreshNotification()
        maybeStopIfIdle()
    }

    private fun maybeStopIfIdle() {
        val anyLive = sessions.values.any { session ->
            when (session.uiState.value.connectionState) {
                ConnectionState.CONNECTED, ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> true
                ConnectionState.DISCONNECTED -> false
            }
        }
        if (!anyLive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildAggregateNotification())
    }

    private fun buildAggregateNotification(): Notification {
        val tabs = _tabs.value
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YAM MUSH Client")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        return when (tabs.size) {
            0 -> builder.setContentText("Connecting…").build()
            1 -> builder.setContentText(statusText(tabs[0].connectionState)).build()
            else -> {
                val style = NotificationCompat.InboxStyle()
                tabs.forEach { style.addLine("${it.label}: ${statusText(it.connectionState)}") }
                builder.setContentText("${tabs.size} connections").setStyle(style).build()
            }
        }
    }

    private fun statusText(state: ConnectionState): String = when (state) {
        ConnectionState.CONNECTED -> "Connected"
        ConnectionState.RECONNECTING -> "Reconnecting…"
        ConnectionState.CONNECTING -> "Connecting…"
        ConnectionState.DISCONNECTED -> "Disconnected"
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MUSH session",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows while a MUSH connection is active"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private data class LoginCredentials(val characterName: String, val characterPassword: String)
    private data class DialTarget(val host: String, val port: Int, val worldId: Long?)

    /**
     * One tab's worth of connection state: its own [TelnetClient], ANSI/trigger/slash-command
     * scope, and [SessionUiState]. An `inner class` so it can reach the Service's
     * [lifecycleScope]/DAOs/`applicationContext` directly rather than needing its own.
     */
    private inner class ConnectionSession(val id: Long, initialWorldId: Long?) {
        val telnetClient = TelnetClient(lifecycleScope, pinStore = RoomPinStore(sslPinDao))
        val ansiParser = AnsiParser()
        val pendingLine = StringBuilder()

        var lastHost: String? = null
        var lastPort: Int = -1
        var worldId: Long? = initialWorldId
        var userInitiatedDisconnect = false
        var debugEnabled = false
        // Set by a fatal TelnetEvent.Error (a changed SSL certificate): the next disconnect must
        // not be followed by an auto-reconnect, which could never succeed.
        private var lastErrorFatal = false
        var pendingAutoLogin: LoginCredentials? = null
        private var reconnectJob: Job? = null
        private var triggerObserverJob: Job? = null
        private var slashCommandObserverJob: Job? = null
        private var eventCollectorJob: Job? = null
        // Set by /reconnect or /connect while a live connection is still up: there's no live
        // socket to redial onto until the current one finishes tearing down (async, via the
        // normal DISCONNECTED event), so the next dial waits here instead of racing it.
        private var pendingDial: DialTarget? = null
        // Updated live by observeTriggers()/observeSlashCommands(); world entries first, then
        // Global — matches Potato's World-then-Global evaluation order.
        @Volatile var currentTriggers: List<Trigger> = emptyList()
        @Volatile var currentSlashCommands: List<SlashCommand> = emptyList()

        private val _uiState = MutableStateFlow(SessionUiState())
        val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

        fun start() {
            eventCollectorJob = lifecycleScope.launch { telnetClient.events.collect(::handleEvent) }
            lifecycleScope.launch {
                uiState.collect { state -> updateTab(id, state.connectionState, state.errorMessage != null) }
            }
            observeTriggers()
            observeSlashCommands()
        }

        /** Explicit teardown for [closeConnection] — without this a "closed" tab's jobs/socket leak. */
        fun dispose() {
            reconnectJob?.cancel()
            triggerObserverJob?.cancel()
            slashCommandObserverJob?.cancel()
            eventCollectorJob?.cancel()
            telnetClient.disconnect()
        }

        /** Pre-fills host/port for editing without dialing (picking a bare world/manual entry). */
        fun prefill(host: String, port: Int) {
            lastHost = host
            lastPort = port
            _uiState.update { it.copy(host = host, port = port.toString()) }
        }

        private fun observeTriggers() {
            triggerObserverJob?.cancel()
            triggerObserverJob = lifecycleScope.launch {
                val worldTriggers = worldId?.let { triggerDao.observeForWorld(it) } ?: flowOf(emptyList())
                combine(worldTriggers, triggerDao.observeGlobal()) { world, global -> world + global }
                    .collect { currentTriggers = it }
            }
        }

        private fun observeSlashCommands() {
            slashCommandObserverJob?.cancel()
            slashCommandObserverJob = lifecycleScope.launch {
                val worldCommands = worldId?.let { slashCommandDao.observeForWorld(it) } ?: flowOf(emptyList())
                combine(worldCommands, slashCommandDao.observeGlobal()) { world, global -> world + global }
                    .collect { currentSlashCommands = it }
            }
        }

        fun connect(host: String, port: Int) {
            if (host.isBlank()) return
            lastHost = host
            lastPort = port
            userInitiatedDisconnect = false
            lastErrorFatal = false
            _uiState.update { it.copy(host = host, port = port.toString(), errorMessage = null) }
            lifecycleScope.launch {
                // Per-world setting, read at dial time so an edit in World Properties applies to
                // the next connect/reconnect without restarting anything.
                val world = worldId?.let { worldDao.getById(it) }
                telnetClient.connect(
                    host,
                    port,
                    nopKeepalive = world?.nopKeepalive ?: false,
                    useSsl = world?.useSsl ?: false,
                )
            }
        }

        fun disconnect() {
            userInitiatedDisconnect = true
            reconnectJob?.cancel()
            reconnectJob = null
            pendingDial = null
            telnetClient.disconnect()
            // telnetClient.disconnect() only emits a state change if a socket was actually open —
            // while waiting out a scheduled reconnect there isn't one, so force the update here.
            if (_uiState.value.connectionState == ConnectionState.RECONNECTING) {
                _uiState.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }
            }
        }

        /**
         * [echoAs] overrides what's shown in the scrollback/history in place of [text] itself;
         * pass null to suppress the echo (and history entry) entirely. Used for character
         * auto-login, where [text] carries a real password that must never render on screen or
         * land in the recallable history — not even redacted, the whole "connect ..." line is
         * skipped and only the server's own response (e.g. a "has connected" line) shows up.
         *
         * [text] is also resolved against [currentSlashCommands] first: a recognized `/name
         * args` sends the command's expansion instead, while [echoAs] (still [text] by
         * default) keeps showing what was actually typed.
         */
        fun send(text: String, echoAs: String? = text) {
            if (text.isEmpty()) return
            if (echoAs != null) recordEcho(echoAs)
            when (val resolution = SlashCommandEngine.resolve(text, currentSlashCommands)) {
                is SlashResolution.Literal -> transmitIfConnected(listOf(resolution.text))
                is SlashResolution.Expanded -> transmitIfConnected(resolution.lines)
                is SlashResolution.SystemMessage -> printSystemMessage(resolution.text)
                is SlashResolution.Action -> performAction(resolution.action)
            }
        }

        private fun recordEcho(echoAs: String) {
            _uiState.update {
                it.copy(
                    lines = it.lines + stamped(AnnotatedString("> $echoAs"), it.showTimestamps),
                    // Most-recent-first, re-sending a repeated command moves it to the front
                    // instead of listing it twice.
                    history = (listOf(echoAs) + it.history.filterNot { cmd -> cmd == echoAs }).take(MAX_HISTORY),
                )
            }
        }

        /** Anything bound for the server needs a live connection; say so instead of dropping it silently. */
        private fun transmitIfConnected(lines: List<String>) {
            if (_uiState.value.connectionState == ConnectionState.CONNECTED) {
                lines.forEach(::transmit)
            } else {
                printSystemMessage(NOT_CONNECTED_NOTE)
            }
        }

        private fun transmit(text: String) {
            lifecycleScope.launch { telnetClient.send(text) }
        }

        private fun handleEvent(event: TelnetEvent) {
            when (event) {
                is TelnetEvent.Text -> appendText(event.chunk)
                is TelnetEvent.StateChanged -> {
                    // Routed through here regardless of *why* the connection ended (user
                    // tapped Disconnect, the remote end closed the socket, a connect attempt
                    // failed) — TelnetClient always funnels all of those into this same event.
                    if (event.state == ConnectionState.DISCONNECTED) {
                        val dial = pendingDial
                        when {
                            dial != null -> {
                                pendingDial = null
                                dialTarget(dial)
                            }
                            !userInitiatedDisconnect && !lastErrorFatal && appPreferences.autoReconnect.value && lastHost != null ->
                                scheduleReconnect()
                            else -> _uiState.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }
                        }
                    } else {
                        _uiState.update { it.copy(connectionState = event.state) }
                        if (event.state == ConnectionState.CONNECTED) {
                            // Consumed once: an auto-reconnect later on this same session must
                            // not resend the login line a second time.
                            val login = pendingAutoLogin
                            if (login != null) {
                                pendingAutoLogin = null
                                _tabs.update { it.withCharacter(id, login.characterName) }
                                send("connect ${login.characterName} ${login.characterPassword}", echoAs = null)
                            }
                        }
                    }
                }
                is TelnetEvent.Error -> {
                    if (event.fatal) lastErrorFatal = true
                    _uiState.update { it.copy(errorMessage = event.message) }
                }
                is TelnetEvent.Notice -> printSystemMessage(event.message)
                is TelnetEvent.Subnegotiation -> Unit // not interpreted yet (GMCP/MSDP land later)
                is TelnetEvent.Debug -> if (debugEnabled) printSystemMessage(event.message)
            }
        }

        private fun scheduleReconnect() {
            _uiState.update { it.copy(connectionState = ConnectionState.RECONNECTING) }
            reconnectJob = lifecycleScope.launch {
                delay(RECONNECT_DELAY_MS)
                val host = lastHost
                val port = lastPort
                if (host != null && port > 0) connect(host, port)
            }
        }

        /** Connects assuming no live socket exists — callers ensure that first (see [pendingDial]). */
        private fun dialTarget(target: DialTarget) {
            if (target.worldId != worldId) {
                worldId = target.worldId
                observeTriggers()
                observeSlashCommands()
            }
            connect(target.host, target.port)
        }

        /** `/reconnect`: redial this tab now instead of waiting out [scheduleReconnect]'s delay. */
        private fun reconnectNow() {
            val host = lastHost
            val port = lastPort
            if (host == null || port <= 0) return
            val target = DialTarget(host, port, worldId)
            when (_uiState.value.connectionState) {
                ConnectionState.RECONNECTING -> {
                    reconnectJob?.cancel()
                    reconnectJob = null
                    dialTarget(target)
                }
                ConnectionState.DISCONNECTED -> dialTarget(target)
                else -> {
                    pendingDial = target
                    userInitiatedDisconnect = false
                    telnetClient.disconnect()
                }
            }
        }

        /** `/connect <world name, or host:port>` — redials *this* tab, doesn't open a new one. */
        private fun connectToAddress(address: String) {
            lifecycleScope.launch {
                val worlds = worldDao.observeAll().first()
                val matched = worlds.firstOrNull { it.name.equals(address.trim(), ignoreCase = true) }
                val target = when {
                    matched != null -> DialTarget(matched.host, matched.port, matched.id)
                    else -> parseHostPort(address)?.let { (host, port) -> DialTarget(host, port, null) }
                }
                if (target == null) {
                    printSystemMessage("Can't connect: $address")
                    return@launch
                }
                when (_uiState.value.connectionState) {
                    ConnectionState.RECONNECTING -> {
                        reconnectJob?.cancel()
                        reconnectJob = null
                        dialTarget(target)
                    }
                    ConnectionState.DISCONNECTED -> dialTarget(target)
                    else -> {
                        pendingDial = target
                        userInitiatedDisconnect = false
                        telnetClient.disconnect()
                    }
                }
            }
        }

        private fun parseHostPort(address: String): Pair<String, Int>? {
            val trimmed = address.trim()
            val parts = if (trimmed.contains(':')) trimmed.split(':', limit = 2) else trimmed.split(Regex("\\s+"), limit = 2)
            val host = parts.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
            return if (port > 0) host to port else null
        }

        private fun performAction(action: BuiltInAction) {
            when (action) {
                BuiltInAction.ClearScreen -> _uiState.update { it.copy(lines = emptyList()) }
                is BuiltInAction.OpenUrl -> openUrl(action.url)
                BuiltInAction.ReconnectNow -> reconnectNow()
                BuiltInAction.Disconnect -> disconnect()
                is BuiltInAction.Connect -> connectToAddress(action.address)
                is BuiltInAction.SetTimestamps -> setTimestamps(action.enabled)
                is BuiltInAction.SslForget -> lifecycleScope.launch {
                    val key = CertificatePinning.key(action.host, action.port)
                    val removed = withContext(Dispatchers.IO) { sslPinDao.delete(key) } > 0
                    printSystemMessage(
                        if (removed) "Forgot the saved SSL certificate for $key. The next connection will trust whatever it sees."
                        else "No saved SSL certificate for $key.",
                    )
                }
                is BuiltInAction.SetDebug -> {
                    debugEnabled = action.enabled ?: !debugEnabled
                    printSystemMessage(if (debugEnabled) "Debug: ON" else "Debug: OFF")
                }
            }
        }

        private fun openUrl(url: String) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                printSystemMessage("Can't open: $url")
            }
        }

        private fun printSystemMessage(text: String) {
            _uiState.update {
                it.copy(lines = it.lines + stamped(recolor(AnnotatedString(text), TerminalSystemYellow), it.showTimestamps))
            }
        }

        private fun stamped(line: AnnotatedString, on: Boolean, time: LocalTime = LocalTime.now()): AnnotatedString =
            if (on) Timestamps.apply(line, time) else line

        /**
         * Flips (or, with an explicit [enabled], sets) this tab's timestamps. Switching to the
         * state it is already in is a no-op. The marker line carries its own full date/time, so
         * it is added unstamped, and nothing already on screen is rewritten.
         */
        fun setTimestamps(enabled: Boolean?) {
            val target = enabled ?: !_uiState.value.showTimestamps
            if (target == _uiState.value.showTimestamps) return
            val marker = recolor(AnnotatedString(Timestamps.marker(target, LocalDateTime.now())), TerminalSystemYellow)
            _uiState.update { it.copy(showTimestamps = target, lines = it.lines + marker) }
        }

        private fun appendText(chunk: String) {
            pendingLine.append(chunk)
            val parts = pendingLine.toString().replace("\r\n", "\n").replace("\r", "\n").split("\n")
            pendingLine.clear()
            pendingLine.append(parts.last())
            val completeLines = parts.dropLast(1)
            if (completeLines.isEmpty()) return

            lifecycleScope.launch {
                // The parse/trigger-evaluate loop is pure computation with no UI-state touches
                // until the end — running it off Main means one busy/spammy tab's burst of
                // text can't stall every other tab's event processing and UI responsiveness.
                val (visibleLines, triggeredSends, latestInput) = withContext(Dispatchers.Default) {
                    val visible = mutableListOf<AnnotatedString>()
                    val sends = mutableListOf<String>()
                    var input: String? = null
                    for (raw in completeLines) {
                        // Always parsed (even if the outcome omits the line) — AnsiParser is
                        // stateful, so skipping a line here would lose any SGR color/style
                        // change it carried into the lines that follow it.
                        val rendered = ansiParser.parseLine(raw)
                        val outcome = TriggerEngine.evaluate(raw, currentTriggers)
                        if (!outcome.omit) {
                            visible += outcome.recolorForeground?.let { recolor(rendered, Color(it)) } ?: rendered
                        }
                        sends += outcome.sendLines
                        outcome.inputText?.let { input = it }
                    }
                    Triple(visible, sends, input)
                }

                if (visibleLines.isNotEmpty()) {
                    _tabs.update { it.withActivity(id, _activeConnectionId.value) }
                    val time = LocalTime.now()
                    _uiState.update { state ->
                        state.copy(lines = state.lines + visibleLines.map { stamped(it, state.showTimestamps, time) })
                    }
                }
                latestInput?.let(::setPendingInput)
                // Not echoed (echoAs = null): the player didn't type these, an auto-reply
                // shouldn't masquerade as one in the scrollback/history.
                triggeredSends.forEach { send(it, echoAs = null) }
            }
        }

        private fun recolor(text: AnnotatedString, color: Color): AnnotatedString = buildAnnotatedString {
            append(text)
            addStyle(SpanStyle(color = color), 0, text.length)
        }

        private fun setPendingInput(text: String) {
            _uiState.update { it.copy(pendingInputText = text, pendingInputNonce = it.pendingInputNonce + 1) }
        }
    }

    companion object {
        private const val CHANNEL_ID = "mud_session"
        private const val NOTIFICATION_ID = 1
        private const val MAX_HISTORY = 20
        private const val NOT_CONNECTED_NOTE =
            "Not connected, so that wasn't sent. Tap Connect, or use /reconnect or /connect."
        private const val RECONNECT_DELAY_MS = 30_000L
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_WORLD_ID = "worldId"
        const val EXTRA_WORLD_NAME = "worldName"
        const val EXTRA_CHAR_NAME = "charName"
        const val EXTRA_CHAR_PASSWORD = "charPassword"
    }
}

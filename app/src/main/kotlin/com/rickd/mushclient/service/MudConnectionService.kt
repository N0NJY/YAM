package com.rickd.mushclient.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.compose.ui.text.AnnotatedString
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.rickd.mushclient.MainActivity
import com.rickd.mushclient.R
import com.rickd.mushclient.ansi.AnsiParser
import com.rickd.mushclient.telnet.ConnectionState
import com.rickd.mushclient.telnet.TelnetClient
import com.rickd.mushclient.telnet.TelnetEvent
import com.rickd.mushclient.ui.session.SessionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the [TelnetClient] so the MUSH session survives the
 * Activity being backgrounded or destroyed on a config change. The UI binds to it for
 * direct calls (disconnect/send) and state observation, but [connect] is only ever
 * triggered via [onStartCommand] (started with `startForegroundService`) — that's what
 * keeps this service alive after the UI unbinds, not the binding itself.
 */
class MudConnectionService : LifecycleService() {

    private val binder = LocalBinder()
    private val telnetClient = TelnetClient(lifecycleScope)
    private val ansiParser = AnsiParser()
    private val pendingLine = StringBuilder()

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): MudConnectionService = this@MudConnectionService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        lifecycleScope.launch {
            telnetClient.events.collect(::handleEvent)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification("Connecting…"))
        val host = intent?.getStringExtra(EXTRA_HOST)
        val port = intent?.getIntExtra(EXTRA_PORT, -1) ?: -1
        if (host != null && port > 0) {
            connect(host, port)
        }
        return START_NOT_STICKY
    }

    private fun connect(host: String, port: Int) {
        if (host.isBlank()) return
        _uiState.update { it.copy(errorMessage = null) }
        lifecycleScope.launch {
            telnetClient.connect(host, port)
        }
    }

    fun disconnect() {
        telnetClient.disconnect()
    }

    /**
     * [echoAs] overrides what's shown in the scrollback/history in place of [text] itself;
     * pass null to suppress the echo (and history entry) entirely. Used for character
     * auto-login, where [text] carries a real password that must never render on screen or
     * land in the recallable history — not even redacted, the whole "connect ..." line is
     * skipped and only the server's own response (e.g. a "has connected" line) shows up.
     */
    fun send(text: String, echoAs: String? = text) {
        if (text.isEmpty()) return
        if (echoAs != null) {
            _uiState.update {
                it.copy(
                    lines = it.lines + AnnotatedString("> $echoAs"),
                    // Most-recent-first, re-sending a repeated command moves it to the front
                    // instead of listing it twice.
                    history = (listOf(echoAs) + it.history.filterNot { cmd -> cmd == echoAs }).take(MAX_HISTORY),
                )
            }
        }
        lifecycleScope.launch {
            telnetClient.send(text)
        }
    }

    private fun handleEvent(event: TelnetEvent) {
        when (event) {
            is TelnetEvent.Text -> appendText(event.chunk)
            is TelnetEvent.StateChanged -> {
                _uiState.update { it.copy(connectionState = event.state) }
                // Routed through here regardless of *why* the connection ended (user tapped
                // Disconnect, the remote end closed the socket, a connect attempt failed) —
                // TelnetClient always funnels all of those into this same event, so tearing
                // the foreground/started state down here (instead of also in `disconnect()`)
                // means a server-initiated drop can't leave a zombie foreground notification.
                if (event.state == ConnectionState.DISCONNECTED) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateNotification(event.state)
                }
            }
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
            val rendered = completeLines.map(ansiParser::parseLine)
            _uiState.update { it.copy(lines = it.lines + rendered) }
        }
    }

    private fun updateNotification(state: ConnectionState) {
        // Only ever called with CONNECTING/CONNECTED — DISCONNECTED is handled by the
        // caller tearing the whole foreground notification down instead of updating it.
        val text = if (state == ConnectionState.CONNECTED) "Connected" else "Connecting…"
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(statusText: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MUSH Client")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
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

    companion object {
        private const val CHANNEL_ID = "mud_session"
        private const val NOTIFICATION_ID = 1
        private const val MAX_HISTORY = 20
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
    }
}

package com.rickd.mushclient.telnet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.Charset
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager

/**
 * Owns a raw telnet TCP connection: connects, runs the read loop on [Dispatchers.IO],
 * and replies to IAC option negotiation via [TelnetNegotiator]: every option is refused
 * (WONT/DONT) — a valid, RFC 854-compliant response, enough for plain line-mode MUD/MUSH
 * output — except NAWS (window size) and TERM-TYPE, which are answered. Other subnegotiation
 * blocks (GMCP/MSDP) are parsed and surfaced via [TelnetEvent.Subnegotiation] but not
 * interpreted here — a future decoder subscribes to
 * that event without any changes to this class.
 */
class TelnetClient(
    private val scope: CoroutineScope,
    private val nopIntervalMs: Long = NOP_INTERVAL_MS,
    private val keepAliveTuner: TcpKeepAlive.Tuner? = null,
    private val pinStore: PinStore? = null,
    // How a socket is actually closed; a seam so tests can observe which thread does it.
    private val closeSocket: (Socket) -> Unit = { it.close() },
) {

    // Byte-transparent: never throws mid-decode on a TCP packet boundary, and matches how
    // most classic MUD/MUSH servers emit text.
    private val charset: Charset = Charset.forName("ISO-8859-1")
    private val iacProcessor = IacProcessor()
    // Per-connection: each connect starts with nothing agreed to (see TelnetNegotiator).
    private var negotiator = TelnetNegotiator()

    private var socket: Socket? = null
    private var nopJob: Job? = null
    private var output: OutputStream? = null

    private val _events = MutableSharedFlow<TelnetEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<TelnetEvent> = _events.asSharedFlow()

    /** Single-address connect; see the list form below. */
    suspend fun connect(host: String, port: Int, nopKeepalive: Boolean = false, useSsl: Boolean = false) =
        connect(listOf(ConnectionTarget(host, port, useSsl)), nopKeepalive)

    /**
     * Tries each of [targets] in order and uses the first that connects (TCP, then TLS if that
     * target asks for it). Each call starts again from the first target, so a primary that has
     * come back is always preferred over a fallback.
     *
     * [nopKeepalive] (a per-world opt-in) also sends an `IAC NOP` heartbeat every [nopIntervalMs],
     * for servers or routers that drop idle connections. TCP keepalive is always on.
     *
     * With a [proxy], every target is reached through it (the proxy resolves hostnames itself).
     */
    suspend fun connect(targets: List<ConnectionTarget>, nopKeepalive: Boolean = false, proxy: Socks4Proxy? = null) {
        withContext(Dispatchers.IO) {
            _events.emit(TelnetEvent.StateChanged(ConnectionState.CONNECTING))
            negotiator = TelnetNegotiator()
            val failures = mutableListOf<Pair<ConnectionTarget, IOException>>()
            for (target in targets) {
                val active = try {
                    openSocket(target, proxy)
                } catch (e: IOException) {
                    failures += target to e
                    continue
                }
                socket = active
                output = active.getOutputStream()
                announceFallbackUse(target, failures)
                _events.emit(TelnetEvent.StateChanged(ConnectionState.CONNECTED))
                if (nopKeepalive) startNopHeartbeat()
                readLoop(active)
                return@withContext
            }
            reportConnectFailure(failures)
        }
    }

    /** Opens one target; on any failure nothing is left open. */
    private fun openSocket(target: ConnectionTarget, proxy: Socks4Proxy?): Socket {
        val raw = Socket()
        try {
            if (proxy == null) {
                raw.connect(InetSocketAddress(target.host, target.port), CONNECT_TIMEOUT_MS)
            } else {
                // The hop to the proxy is plain TCP, never TLS; TLS (if wanted) goes on top of the
                // relayed connection afterwards, so the certificate checked is the *server's*.
                try {
                    raw.connect(InetSocketAddress(proxy.host, proxy.port), CONNECT_TIMEOUT_MS)
                } catch (e: IOException) {
                    throw ProxyException("Couldn't reach the SOCKS4 proxy ${proxy.label} (${friendlyReason(e)}).")
                }
            }
            if (keepAliveTuner != null) TcpKeepAlive.enable(raw, keepAliveTuner) else TcpKeepAlive.enable(raw)
            if (proxy != null) Socks4.handshake(raw, target.host, target.port)
            return if (target.useSsl) wrapInTls(raw, target.host, target.port) else raw
        } catch (e: Throwable) {
            runCatching { raw.close() }
            throw e
        }
    }

    private fun changedCertificate(e: Throwable): CertificateChangedException? =
        generateSequence(e) { it.cause }.filterIsInstance<CertificateChangedException>().firstOrNull()

    private fun reasonFor(e: IOException): String =
        if (changedCertificate(e) != null) "its SSL certificate changed" else friendlyReason(e)

    /** Tells the player a fallback was used (and why), and never lets a refused certificate go unmentioned. */
    private suspend fun announceFallbackUse(target: ConnectionTarget, failures: List<Pair<ConnectionTarget, IOException>>) {
        if (failures.isEmpty()) return
        val why = failures.joinToString("; ") { (t, e) -> "${t.label} (${reasonFor(e)})" }
        _events.emit(TelnetEvent.Notice("[Fallback] Couldn't connect to $why. Connected to the fallback address ${target.label} instead."))
        failures.mapNotNull { changedCertificate(it.second) }.forEach {
            _events.emit(TelnetEvent.Notice(CertificatePinning.fallbackWarning(it)))
        }
    }

    private suspend fun reportConnectFailure(failures: List<Pair<ConnectionTarget, IOException>>) {
        if (failures.isEmpty()) {
            _events.emit(TelnetEvent.Error("Connection failed: there is no address to connect to."))
        } else {
            val changed = failures.firstNotNullOfOrNull { changedCertificate(it.second) }
            val last = failures.last().second
            if (changed != null) {
                // A refused certificate is a security event: it stays fatal and visible even if
                // another address also failed for an ordinary reason.
                val others = failures.filter { changedCertificate(it.second) == null }
                    .joinToString("") { (t, e) -> "\nAlso couldn't connect to ${t.label}: ${reasonFor(e)}." }
                _events.emit(TelnetEvent.Error(CertificatePinning.mismatchMessage(changed) + others, last, fatal = true))
            } else if (failures.size == 1) {
                _events.emit(TelnetEvent.Error("Connection failed: ${friendlyReason(last)}.", last))
            } else {
                val all = failures.joinToString(" ") { (t, e) -> "${t.label}: ${reasonFor(e)}." }
                _events.emit(TelnetEvent.Error("Connection failed. $all", last))
            }
        }
        _events.emit(TelnetEvent.StateChanged(ConnectionState.DISCONNECTED))
        closeQuietly()
    }

    /**
     * Implicit TLS over the already-connected [raw] socket (the server expects TLS from the first
     * byte, on a dedicated port). The certificate is checked by [PinningTrustManager] inside the
     * handshake; a changed one surfaces here as an IOException caused by
     * [CertificateChangedException].
     */
    private fun wrapInTls(raw: Socket, host: String, port: Int): Socket {
        val store = pinStore ?: throw IOException("SSL is not available: no certificate store.")
        val trust = PinningTrustManager(host, port, store) { fingerprint ->
            _events.tryEmit(
                TelnetEvent.Notice(
                    "[SSL] Trusted a new certificate for ${CertificatePinning.key(host, port)} " +
                        "(SHA-256 $fingerprint). It will be checked on every future connection.",
                ),
            )
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), null) }
        val tls = context.socketFactory.createSocket(raw, host, port, true) as SSLSocket
        // A server that accepts the connection but never answers the handshake must not hang us.
        tls.soTimeout = HANDSHAKE_TIMEOUT_MS
        tls.startHandshake()
        tls.soTimeout = 0
        return tls
    }

    private suspend fun readLoop(sock: Socket) {
        val input = sock.getInputStream()
        val buffer = ByteArray(4096)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                for (raw in iacProcessor.process(buffer, read)) {
                    when (raw) {
                        is IacProcessor.RawEvent.Data -> {
                            _events.emit(TelnetEvent.Text(String(raw.bytes, charset)))
                        }
                        is IacProcessor.RawEvent.Negotiation -> {
                            _events.emit(TelnetEvent.Debug(describeNegotiation(raw.command, raw.option)))
                            sendReplies(negotiator.onNegotiation(raw.command, raw.option))
                        }
                        is IacProcessor.RawEvent.Subnegotiation -> {
                            _events.emit(
                                TelnetEvent.Debug("SB ${optionName(raw.option)} (${raw.data.size} bytes)"),
                            )
                            _events.emit(TelnetEvent.Subnegotiation(raw.option, raw.data))
                            sendReplies(negotiator.onSubnegotiation(raw.option, raw.data))
                        }
                    }
                }
            }
        } catch (e: IOException) {
            _events.emit(TelnetEvent.Error("Connection lost: ${e.message}", e))
        } finally {
            _events.emit(TelnetEvent.StateChanged(ConnectionState.DISCONNECTED))
            closeQuietly()
        }
    }

    private fun describeNegotiation(command: Int, option: Int): String {
        val commandName = when (command) {
            IacProcessor.WILL -> "WILL"
            IacProcessor.WONT -> "WONT"
            IacProcessor.DO -> "DO"
            IacProcessor.DONT -> "DONT"
            else -> "CMD($command)"
        }
        return "$commandName ${optionName(option)}"
    }

    private fun optionName(option: Int): String {
        val name = TELNET_OPTION_NAMES[option] ?: return option.toString()
        return "$option ($name)"
    }

    private suspend fun sendReplies(replies: List<TelnetNegotiator.Reply>) {
        for (reply in replies) {
            writeRawBlocking(reply.bytes)
            _events.emit(TelnetEvent.Debug("-> ${reply.description}"))
        }
    }

    suspend fun send(line: String) {
        withContext(Dispatchers.IO) {
            writeRawBlocking((line + "\r\n").toByteArray(charset))
        }
    }

    private fun writeRawBlocking(bytes: ByteArray) {
        try {
            output?.write(bytes)
            output?.flush()
        } catch (_: IOException) {
            // The read loop will observe the same failure and emit Error/Disconnected.
        }
    }

    /**
     * Safe to call from the main thread. The socket is detached at once, so nothing else can use
     * it, but *closed* on an IO thread: closing an SSLSocket sends a TLS close message over the
     * network, which Android forbids on the main thread (NetworkOnMainThreadException — this
     * crashed the app on Disconnect before the close was moved off it).
     */
    fun disconnect() {
        val sock = detachSocket() ?: return
        scope.launch(Dispatchers.IO) { runCatching { closeSocket(sock) } }
    }

    private fun startNopHeartbeat() {
        nopJob?.cancel()
        nopJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(nopIntervalMs)
                writeRawBlocking(NOP_BYTES)
            }
        }
    }

    /** For callers already on an IO thread (the read loop's cleanup, a failed connect). */
    private fun closeQuietly() {
        val sock = detachSocket() ?: return
        runCatching { closeSocket(sock) }
    }

    private fun detachSocket(): Socket? {
        nopJob?.cancel()
        nopJob = null
        val sock = socket
        socket = null
        output = null
        return sock
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val HANDSHAKE_TIMEOUT_MS = 10_000
        const val NOP_INTERVAL_MS = 60_000L
        private const val NOP = 241
        val NOP_BYTES = byteArrayOf(IacProcessor.IAC.toByte(), NOP.toByte())

        // Just the ones a MUD/MUSH server is likely to actually offer — purely cosmetic,
        // for /debug output; unrecognized options still show fine as a bare number.
        private val TELNET_OPTION_NAMES = mapOf(
            1 to "ECHO",
            3 to "SGA",
            5 to "STATUS",
            24 to "TTYPE",
            25 to "EOR",
            31 to "NAWS",
            34 to "LINEMODE",
            39 to "NEW-ENVIRON",
            42 to "CHARSET",
            70 to "MSSP",
            85 to "COMPRESS",
            86 to "MCCP2",
            90 to "MSP",
            91 to "MXP",
            93 to "ZMP",
            201 to "GMCP",
        )
    }
}

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

    /**
     * [nopKeepalive] (a per-world opt-in) also sends an `IAC NOP` heartbeat every [nopIntervalMs],
     * for servers or routers that drop idle connections. TCP keepalive is always on.
     */
    suspend fun connect(host: String, port: Int, nopKeepalive: Boolean = false) {
        withContext(Dispatchers.IO) {
            _events.emit(TelnetEvent.StateChanged(ConnectionState.CONNECTING))
            negotiator = TelnetNegotiator()
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                if (keepAliveTuner != null) TcpKeepAlive.enable(sock, keepAliveTuner) else TcpKeepAlive.enable(sock)
                socket = sock
                output = sock.getOutputStream()
                _events.emit(TelnetEvent.StateChanged(ConnectionState.CONNECTED))
                if (nopKeepalive) startNopHeartbeat()
                readLoop(sock)
            } catch (e: IOException) {
                _events.emit(TelnetEvent.Error("Connection failed: ${e.message}", e))
                _events.emit(TelnetEvent.StateChanged(ConnectionState.DISCONNECTED))
            }
        }
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

    fun disconnect() {
        closeQuietly()
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

    private fun closeQuietly() {
        nopJob?.cancel()
        nopJob = null
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        output = null
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
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

package com.rickd.mushclient.telnet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * and replies to IAC option negotiation. v1 stance is deliberately simple: refuse every
 * option (WONT/DONT), which is a valid, RFC 854-compliant response and enough for plain
 * line-mode MUD/MUSH output. Subnegotiation blocks (GMCP/MSDP) are parsed and surfaced via
 * [TelnetEvent.Subnegotiation] but not interpreted here — a future decoder subscribes to
 * that event without any changes to this class.
 */
class TelnetClient(private val scope: CoroutineScope) {

    // Byte-transparent: never throws mid-decode on a TCP packet boundary, and matches how
    // most classic MUD/MUSH servers emit text.
    private val charset: Charset = Charset.forName("ISO-8859-1")
    private val iacProcessor = IacProcessor()

    private var socket: Socket? = null
    private var output: OutputStream? = null

    private val _events = MutableSharedFlow<TelnetEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<TelnetEvent> = _events.asSharedFlow()

    suspend fun connect(host: String, port: Int) {
        withContext(Dispatchers.IO) {
            _events.emit(TelnetEvent.StateChanged(ConnectionState.CONNECTING))
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket = sock
                output = sock.getOutputStream()
                _events.emit(TelnetEvent.StateChanged(ConnectionState.CONNECTED))
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
                            respondToNegotiation(raw.command, raw.option)
                        }
                        is IacProcessor.RawEvent.Subnegotiation -> {
                            _events.emit(TelnetEvent.Subnegotiation(raw.option, raw.data))
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

    private fun respondToNegotiation(command: Int, option: Int) {
        val reply = when (command) {
            IacProcessor.WILL, IacProcessor.WONT ->
                byteArrayOf(IacProcessor.IAC.toByte(), IacProcessor.DONT.toByte(), option.toByte())
            IacProcessor.DO, IacProcessor.DONT ->
                byteArrayOf(IacProcessor.IAC.toByte(), IacProcessor.WONT.toByte(), option.toByte())
            else -> return
        }
        writeRawBlocking(reply)
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

    private fun closeQuietly() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        output = null
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
    }
}

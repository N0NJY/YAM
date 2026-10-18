package com.rickd.mushclient.telnet

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.net.IDN
import java.net.Socket

/** A SOCKS4/4a proxy to relay a connection through. Its own hop is always plain TCP. */
data class Socks4Proxy(val host: String, val port: Int) {
    val label: String get() = "$host:$port"
}

/** A proxy problem (unreachable, refused our request, hung up) as opposed to a problem with the server. */
class ProxyException(message: String) : IOException(message)

/**
 * A hand-written SOCKS4/SOCKS4a client — about forty lines of well-understood protocol, so no
 * library. Same behavior as MushTato's, which was checked byte-for-byte against Potato's own:
 *  - a literal dotted IPv4 address is sent as-is (classic SOCKS4);
 *  - a hostname uses SOCKS4a — the placeholder address 0.0.0.1 plus the hostname after the user
 *    id — so the *proxy* resolves DNS, not this device;
 *  - no user id is offered (just the terminating NUL).
 */
object Socks4 {
    private const val VERSION = 4
    private const val CMD_CONNECT = 1
    private const val REPLY_GRANTED = 0x5A
    private const val REPLY_IDENTD_UNREACHABLE = 0x5C
    private const val REPLY_IDENTD_MISMATCH = 0x5D
    private const val HANDSHAKE_TIMEOUT_MS = 10_000

    private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    fun encodeRequest(destHost: String, destPort: Int): ByteArray {
        if (destPort !in 1..65535) throw ProxyException("Invalid port $destPort for a proxied connection.")
        val header = byteArrayOf(
            VERSION.toByte(), CMD_CONNECT.toByte(),
            (destPort shr 8).toByte(), (destPort and 0xFF).toByte(),
        )
        val octets = IPV4.matchEntire(destHost)?.groupValues?.drop(1)?.map { it.toInt() }
        val userId = byteArrayOf(0)
        return if (octets != null && octets.all { it in 0..255 }) {
            header + octets.map { it.toByte() }.toByteArray() + userId
        } else {
            // SOCKS4 cannot carry an IPv6 address; fail clearly instead of sending nonsense.
            if (destHost.contains(':')) throw ProxyException("A SOCKS4 proxy can't connect to an IPv6 address.")
            val ascii = try {
                IDN.toASCII(destHost)
            } catch (_: IllegalArgumentException) {
                throw ProxyException("\"$destHost\" is not a valid host name for a proxied connection.")
            }
            header + byteArrayOf(0, 0, 0, 1) + userId + ascii.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        }
    }

    /**
     * Runs the handshake on [socket], which must already be connected to the proxy. On return the
     * socket is a transparent pipe to [destHost]:[destPort]. Reads exactly the 8-byte reply and
     * no further, so any server data that arrives right behind it is left for the caller.
     */
    fun handshake(socket: Socket, destHost: String, destPort: Int) {
        val request = encodeRequest(destHost, destPort)
        val previousTimeout = socket.soTimeout
        socket.soTimeout = HANDSHAKE_TIMEOUT_MS
        try {
            socket.getOutputStream().apply { write(request); flush() }
            val reply = ByteArray(8)
            try {
                DataInputStream(socket.getInputStream()).readFully(reply)
            } catch (_: EOFException) {
                throw ProxyException("The proxy closed the connection before answering.")
            }
            when (val status = reply[1].toInt() and 0xFF) {
                REPLY_GRANTED -> Unit
                REPLY_IDENTD_UNREACHABLE, REPLY_IDENTD_MISMATCH ->
                    throw ProxyException("The proxy needs an identd/user id it could not verify.")
                else -> throw ProxyException(
                    "The proxy refused the request for $destHost:$destPort (status 0x%02X).".format(status),
                )
            }
        } finally {
            socket.soTimeout = previousTimeout
        }
    }
}

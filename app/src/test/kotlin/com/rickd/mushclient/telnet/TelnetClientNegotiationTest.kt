package com.rickd.mushclient.telnet

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Real sockets, no fakes at the client level: a local server speaks actual telnet negotiation
 * to a real [TelnetClient] and records exactly the bytes the client writes back.
 */
class TelnetClientNegotiationTest {

    private val IAC = 255
    private val SB = 250
    private val SE = 240
    private val WILL = 251
    private val WONT = 252
    private val DO = 253

    /** Runs [serverScript] against a connected client; returns every byte the client wrote. */
    private fun exchange(serverScript: (java.io.OutputStream) -> Unit): List<Int> {
        val received = ByteArrayOutputStream()
        ServerSocket(0).use { server ->
            val serverThread = Thread {
                server.accept().use { sock ->
                    val out = sock.getOutputStream()
                    serverScript(out)
                    out.flush()
                    sock.soTimeout = 700
                    try {
                        val buf = ByteArray(1024)
                        while (true) {
                            val n = sock.getInputStream().read(buf)
                            if (n == -1) break
                            received.write(buf, 0, n)
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // quiet for 700ms: the client has said everything it is going to say
                    }
                }
            }.also { it.start() }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = TelnetClient(scope)
            scope.launch { client.connect("127.0.0.1", server.localPort) }
            serverThread.join(8000)
            client.disconnect()
            scope.cancel()
        }
        return received.toByteArray().map { it.toInt() and 0xFF }
    }

    private fun java.io.OutputStream.send(vararg v: Int) = write(ByteArray(v.size) { v[it].toByte() })

    @Test fun clientAnswersNawsTtypeAndRefusesTheRestOnTheRealWire() {
        val wire = exchange { out ->
            out.send(IAC, DO, 31)                       // DO NAWS
            out.send(IAC, DO, 24)                       // DO TTYPE
            out.send(IAC, DO, 1)                        // DO ECHO (refused)
            out.send(IAC, SB, 24, 1, IAC, SE)           // SB TTYPE SEND
        }
        val expected = listOf(
            IAC, WILL, 31, IAC, SB, 31, 0, 80, 0, 24, IAC, SE,   // NAWS agreed + 80x24
            IAC, WILL, 24,                                       // TTYPE agreed
            IAC, WONT, 1,                                        // ECHO refused
            IAC, SB, 24, 0, 'Y'.code, 'A'.code, 'M'.code, IAC, SE, // TTYPE IS YAM
        )
        assertEquals(expected, wire)
    }

    @Test fun negotiationSplitAcrossTcpReadsIsStillAnswered() {
        val wire = exchange { out ->
            out.send(IAC, DO)            // option byte arrives in a later packet
            out.flush(); Thread.sleep(150)
            out.send(31)
        }
        assertEquals(listOf(IAC, WILL, 31, IAC, SB, 31, 0, 80, 0, 24, IAC, SE), wire)
    }

    @Test fun aServerThatNeverNegotiatesGetsNothingSpontaneous() {
        val wire = exchange { out -> out.write("Welcome\r\n".toByteArray()) }
        assertEquals(emptyList<Int>(), wire)
    }
}

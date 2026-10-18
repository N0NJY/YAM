package com.rickd.mushclient.telnet

import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAliveTest {

    @Test fun enableSetsSoKeepaliveAndAppliesTheTimings() {
        ServerSocket(0).use { server ->
            Socket("127.0.0.1", server.localPort).use { sock ->
                var tuned: List<Int>? = null
                assertFalse(sock.keepAlive)
                TcpKeepAlive.enable(sock) { _, idle, interval, count -> tuned = listOf(idle, interval, count) }
                assertTrue(sock.keepAlive)
                assertEquals(listOf(30, 10, 3), tuned)
            }
        }
    }

    @Test fun aTunerThatFailsStillLeavesKeepaliveOnAndDoesNotThrow() {
        ServerSocket(0).use { server ->
            Socket("127.0.0.1", server.localPort).use { sock ->
                TcpKeepAlive.enable(sock) { _, _, _, _ -> error("platform refuses this knob") }
                assertTrue(sock.keepAlive)
            }
        }
    }

    /** Runs a client against a local server for [listenMs]; returns the bytes the client sent. */
    private fun clientBytes(nop: Boolean, nopIntervalMs: Long, listenMs: Long): List<Int> {
        val received = java.io.ByteArrayOutputStream()
        ServerSocket(0).use { server ->
            val t = Thread {
                server.accept().use { s ->
                    s.soTimeout = listenMs.toInt()
                    try {
                        val buf = ByteArray(256)
                        while (true) {
                            val n = s.getInputStream().read(buf)
                            if (n == -1) break
                            received.write(buf, 0, n)
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                    }
                }
            }.also { it.start() }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val client = TelnetClient(scope, nopIntervalMs = nopIntervalMs, keepAliveTuner = TcpKeepAlive.Tuner { _, _, _, _ -> })
            scope.launch { client.connect("127.0.0.1", server.localPort, nopKeepalive = nop) }
            t.join(listenMs + 3000)
            client.disconnect()
            scope.cancel()
        }
        return received.toByteArray().map { it.toInt() and 0xFF }
    }

    @Test fun nopKeepaliveSendsIacNopRepeatedlyOnTheRealWire() {
        val wire = clientBytes(nop = true, nopIntervalMs = 150, listenMs = 900)
        assertTrue("expected several NOPs, got ${wire.size / 2}", wire.size >= 6)
        assertEquals(List(wire.size / 2) { listOf(255, 241) }.flatten(), wire)
    }

    @Test fun nopKeepaliveIsSilentWhenNotOptedIn() {
        assertEquals(emptyList<Int>(), clientBytes(nop = false, nopIntervalMs = 150, listenMs = 900))
    }
}

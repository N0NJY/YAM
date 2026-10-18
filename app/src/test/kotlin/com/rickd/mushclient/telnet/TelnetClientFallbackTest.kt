package com.rickd.mushclient.telnet

import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fallback addressing on real sockets: a real [TelnetClient] dialing real local servers. */
class TelnetClientFallbackTest {

    private val noTuning = TcpKeepAlive.Tuner { _, _, _, _ -> }

    /** A server that serves up to [connections] clients, counting how many actually arrived. */
    private class Server(val port: Int, val accepts: AtomicInteger, private val socket: ServerSocket) {
        fun close() = runCatching { socket.close() }
    }

    private fun plainServer(banner: String, port: Int = 0, connections: Int = 1): Server {
        val server = ServerSocket(port).apply { reuseAddress = true }
        val accepts = AtomicInteger()
        Thread {
            try {
                repeat(connections) {
                    server.accept().use { s ->
                        accepts.incrementAndGet()
                        s.getOutputStream().write(banner.toByteArray()); s.getOutputStream().flush()
                        Thread.sleep(150)
                    }
                }
            } catch (_: Exception) {
            } finally { runCatching { server.close() } }
        }.start()
        return Server(server.localPort, accepts, server)
    }

    private fun tlsServer(cert: String, banner: String): Server {
        val server = TlsTestSupport.serverContext(cert).serverSocketFactory.createServerSocket(0) as SSLServerSocket
        val accepts = AtomicInteger()
        Thread {
            try {
                server.accept().use { s ->
                    accepts.incrementAndGet()
                    s.getOutputStream().write(banner.toByteArray()); s.getOutputStream().flush()
                    Thread.sleep(150)
                }
            } catch (_: Exception) {
            } finally { runCatching { server.close() } }
        }.start()
        return Server(server.localPort, accepts, server)
    }

    /** A port nothing listens on, so a connect is refused at once. */
    private fun deadPort(): Int = ServerSocket(0).use { it.localPort }

    private class Harness(store: PinStore? = null) {
        val events = CopyOnWriteArrayList<TelnetEvent>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = TelnetClient(scope, keepAliveTuner = TcpKeepAlive.Tuner { _, _, _, _ -> }, pinStore = store)

        init {
            scope.launch { client.events.collect { events += it } }
            runBlocking { delay(80) }
        }

        /** Connects to [targets] and returns the events from this attempt, once it has disconnected. */
        fun connect(targets: List<ConnectionTarget>): Run {
            val from = events.size
            scope.launch { client.connect(targets) }
            val deadline = System.currentTimeMillis() + 12_000
            while (System.currentTimeMillis() < deadline &&
                events.drop(from).none { it is TelnetEvent.StateChanged && it.state == ConnectionState.DISCONNECTED }
            ) Thread.sleep(25)
            return Run(events.drop(from))
        }

        fun close() { client.disconnect(); scope.cancel() }
    }

    private class Run(val events: List<TelnetEvent>) {
        val text get() = events.filterIsInstance<TelnetEvent.Text>().joinToString("") { it.chunk }
        val notices get() = events.filterIsInstance<TelnetEvent.Notice>().map { it.message }
        val errors get() = events.filterIsInstance<TelnetEvent.Error>()
        val connected get() = events.any { it is TelnetEvent.StateChanged && it.state == ConnectionState.CONNECTED }
    }

    private fun target(port: Int, ssl: Boolean = false) = ConnectionTarget("127.0.0.1", port, ssl)

    @Test fun theFallbackIsUsedWhenThePrimaryIsRefusedAndSaysSo() {
        val second = plainServer("SECOND\r\n")
        val dead = deadPort()
        val h = Harness()
        val run = h.connect(listOf(target(dead), target(second.port)))
        h.close()
        assertTrue(run.text.contains("SECOND"))
        assertTrue(run.errors.isEmpty())
        val note = run.notices.single()
        assertTrue(note, note.contains("[Fallback]") && note.contains("127.0.0.1:$dead") && note.contains("127.0.0.1:${second.port}"))
        assertTrue("reason should be plain words: $note", note.contains("(connection refused)"))
        assertFalse("raw Java text should be gone: $note", note.contains("ECONNREFUSED"))
    }

    @Test fun thePrimaryIsPreferredAndTheFallbackIsNeverContacted() {
        val first = plainServer("FIRST\r\n")
        val second = plainServer("SECOND\r\n")
        val h = Harness()
        val run = h.connect(listOf(target(first.port), target(second.port)))
        h.close(); second.close()
        assertTrue(run.text.contains("FIRST"))
        assertFalse(run.text.contains("SECOND"))
        assertEquals(0, second.accepts.get())
        assertTrue(run.notices.isEmpty())
    }

    @Test fun theFallbackIsNotStickyAPrimaryThatCameBackIsUsedAgain() {
        val dead = deadPort()
        val second = plainServer("SECOND\r\n", connections = 2)
        val h = Harness()
        val firstRun = h.connect(listOf(target(dead), target(second.port)))
        assertTrue(firstRun.text.contains("SECOND"))
        assertEquals(1, second.accepts.get())

        // The primary comes back on the very port that was dead; the next connect must use it.
        val primaryAgain = plainServer("FIRST AGAIN\r\n", port = dead)
        val secondRun = h.connect(listOf(target(dead), target(second.port)))
        h.close(); second.close()
        assertTrue(secondRun.text.contains("FIRST AGAIN"))
        assertEquals("the fallback must not be touched again", 1, second.accepts.get())
        assertEquals(1, primaryAgain.accepts.get())
    }

    @Test fun whenEveryAddressFailsTheErrorNamesThemAllAndIsNotFatal() {
        val a = deadPort(); val b = deadPort()
        val h = Harness()
        val run = h.connect(listOf(target(a), target(b)))
        h.close()
        assertFalse(run.connected)
        val err = run.errors.single()
        assertFalse(err.fatal)
        assertTrue(err.message, err.message.contains("127.0.0.1:$a") && err.message.contains("127.0.0.1:$b"))
    }

    @Test fun aSingleAddressFailureIsInPlainWordsNotJavasRawText() {
        val dead = deadPort()
        val h = Harness()
        val run = h.connect(listOf(target(dead)))
        h.close()
        assertEquals("Connection failed: connection refused.", run.errors.single().message)
    }

    @Test fun anEmptyTargetListFailsClearly() {
        val h = Harness()
        val run = h.connect(emptyList())
        h.close()
        assertTrue(run.errors.single().message.contains("no address"))
    }

    @Test fun aChangedPrimaryCertificateStillFallsBackButTheWarningIsShown() {
        val primary = tlsServer("tls-b.p12", "PRIMARY\r\n")
        val second = plainServer("SECOND\r\n")
        val store = InMemoryPinStore().apply { pins["127.0.0.1:${primary.port}"] = TlsTestSupport.FINGERPRINT_A }
        val h = Harness(store)
        val run = h.connect(listOf(target(primary.port, ssl = true), target(second.port)))
        h.close()
        assertTrue(run.text.contains("SECOND"))
        assertFalse(run.text.contains("PRIMARY"))
        assertTrue(run.errors.isEmpty())
        val warning = run.notices.firstOrNull { it.contains("CHANGED") }
        assertTrue(run.notices.toString(), warning != null && warning.contains("/ssl-forget 127.0.0.1:${primary.port}"))
        assertEquals(TlsTestSupport.FINGERPRINT_A, store.pins["127.0.0.1:${primary.port}"])
    }

    @Test fun aChangedCertificateStaysFatalAndVisibleEvenIfTheFallbackAlsoFails() {
        val primary = tlsServer("tls-b.p12", "PRIMARY\r\n")
        val dead = deadPort()
        val store = InMemoryPinStore().apply { pins["127.0.0.1:${primary.port}"] = TlsTestSupport.FINGERPRINT_A }
        val h = Harness(store)
        val run = h.connect(listOf(target(primary.port, ssl = true), target(dead)))
        h.close()
        val err = run.errors.single()
        assertTrue("the security event must not be downgraded", err.fatal)
        assertTrue(err.message.contains("/ssl-forget 127.0.0.1:${primary.port}"))
        assertTrue(err.message.contains("Also couldn't connect to 127.0.0.1:$dead"))
    }

    @Test fun theFallbackHasItsOwnSslSettingAndItsOwnPin() {
        val dead = deadPort()
        val second = tlsServer("tls-a.p12", "SECURE SECOND\r\n")
        val store = InMemoryPinStore()
        val h = Harness(store)
        val run = h.connect(listOf(target(dead, ssl = false), target(second.port, ssl = true)))
        h.close()
        assertTrue(run.text.contains("SECURE SECOND"))
        assertEquals(TlsTestSupport.FINGERPRINT_A, store.pins["127.0.0.1:${second.port}"])
        assertFalse("the dead plain primary never gets a pin", store.pins.containsKey("127.0.0.1:$dead"))
    }
}

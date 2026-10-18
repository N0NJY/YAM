package com.rickd.mushclient.telnet

import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real TLS sockets end to end: a local TLS server, a real [TelnetClient], a real trust manager. */
class TelnetClientTlsTest {

    private val noTuning = TcpKeepAlive.Tuner { _, _, _, _ -> }

    /** A one-shot TLS server using [certName] on [port] (0 = pick one); returns its port. */
    private fun tlsServer(certName: String, port: Int = 0, banner: String = "Hello over TLS\r\n"): Pair<Int, Thread> {
        val server = TlsTestSupport.serverContext(certName).serverSocketFactory.createServerSocket(port) as SSLServerSocket
        val thread = Thread {
            try {
                server.use { srv ->
                    srv.accept().use { s ->
                        s.getOutputStream().write(banner.toByteArray()); s.getOutputStream().flush()
                        s.soTimeout = 500
                        try { while (s.getInputStream().read() != -1) Unit } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
                // a client that refuses the certificate aborts the handshake; that's expected
            }
        }.also { it.start() }
        return server.localPort to thread
    }

    private class Run(val events: List<TelnetEvent>) {
        val texts get() = events.filterIsInstance<TelnetEvent.Text>().joinToString("") { it.chunk }
        val notices get() = events.filterIsInstance<TelnetEvent.Notice>()
        val errors get() = events.filterIsInstance<TelnetEvent.Error>()
    }

    /** Connects once and returns every event, once the client reports DISCONNECTED. */
    private fun connect(port: Int, store: PinStore?, useSsl: Boolean = true): Run {
        val events = CopyOnWriteArrayList<TelnetEvent>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = TelnetClient(scope, keepAliveTuner = noTuning, pinStore = store)
        scope.launch { client.events.collect { events += it } }
        runBlocking { delay(80) }
        scope.launch { client.connect("127.0.0.1", port, useSsl = useSsl) }
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline &&
            events.none { it is TelnetEvent.StateChanged && it.state == ConnectionState.DISCONNECTED }
        ) Thread.sleep(25)
        client.disconnect(); scope.cancel()
        return Run(events.toList())
    }

    @Test fun firstConnectTrustsSavesThePinAndDeliversText() {
        val store = InMemoryPinStore()
        val (port, t) = tlsServer("tls-a.p12")
        val run = connect(port, store)
        t.join(3000)
        assertTrue(run.texts.contains("Hello over TLS"))
        assertEquals(TlsTestSupport.FINGERPRINT_A, store.pins["127.0.0.1:$port"])
        assertEquals(1, run.notices.size)
        assertTrue(run.notices[0].message.contains(TlsTestSupport.FINGERPRINT_A))
    }

    @Test fun reconnectingWithTheSameCertificateIsSilent() {
        val (port, t) = tlsServer("tls-a.p12")
        val store = InMemoryPinStore().apply { pins["127.0.0.1:$port"] = TlsTestSupport.FINGERPRINT_A }
        val run = connect(port, store)
        t.join(3000)
        assertTrue(run.texts.contains("Hello over TLS"))
        assertTrue("no notice expected on a known certificate", run.notices.isEmpty())
        assertTrue(run.errors.isEmpty())
    }

    @Test fun aChangedCertificateIsRefusedBeforeAnyTextAndIsFatal() {
        val (port, t) = tlsServer("tls-b.p12")
        val store = InMemoryPinStore().apply { pins["127.0.0.1:$port"] = TlsTestSupport.FINGERPRINT_A }
        val run = connect(port, store)
        t.join(3000)
        assertEquals("", run.texts)
        val err = run.errors.single()
        assertTrue(err.fatal)
        assertTrue(err.message.contains("/ssl-forget 127.0.0.1:$port"))
        assertTrue(err.message.contains(TlsTestSupport.FINGERPRINT_A) && err.message.contains(TlsTestSupport.FINGERPRINT_B))
        assertEquals("the saved pin must not be overwritten", TlsTestSupport.FINGERPRINT_A, store.pins["127.0.0.1:$port"])
    }

    @Test fun forgettingThePinLetsTheNewCertificateBeTrusted() {
        val (port, t1) = tlsServer("tls-a.p12")
        val store = InMemoryPinStore()
        assertTrue(connect(port, store).texts.contains("Hello"))
        t1.join(3000)
        // The server's certificate is replaced (same host:port, new key).
        val (_, t2) = tlsServer("tls-b.p12", port)
        assertTrue("changed cert refused", connect(port, store).errors.single().fatal)
        t2.join(3000)
        assertTrue(store.remove("127.0.0.1:$port"))
        val (_, t3) = tlsServer("tls-b.p12", port)
        val run = connect(port, store)
        t3.join(3000)
        assertTrue(run.texts.contains("Hello over TLS"))
        assertEquals(TlsTestSupport.FINGERPRINT_B, store.pins["127.0.0.1:$port"])
    }

    @Test fun aPlainConnectionNeverTouchesTheCertificateStore() {
        val store = InMemoryPinStore()
        val server = ServerSocket(0)
        val t = Thread {
            server.use { it.accept().use { s -> s.getOutputStream().write("plain hello\r\n".toByteArray()) } }
        }.also { it.start() }
        val run = connect(server.localPort, store, useSsl = false)
        t.join(3000)
        assertTrue(run.texts.contains("plain hello"))
        assertEquals(0, store.gets)
        assertTrue(store.pins.isEmpty())
    }

    @Test fun sslAgainstAServerThatIsNotSpeakingTlsFailsWithoutBeingFatal() {
        val server = ServerSocket(0)
        val t = Thread {
            try { server.use { it.accept().use { s -> s.getOutputStream().write("not tls at all\r\n".toByteArray()); Thread.sleep(300) } } } catch (_: Exception) {}
        }.also { it.start() }
        val run = connect(server.localPort, InMemoryPinStore())
        t.join(3000)
        val err = run.errors.single()
        assertFalse("a plain failure is retryable", err.fatal)
        assertTrue(err.message.startsWith("Connection failed"))
        assertEquals("", run.texts)
    }

    @Test fun sslWithoutACertificateStoreFailsClearly() {
        val server = ServerSocket(0)
        val t = Thread { try { server.use { it.accept().close() } } catch (_: Exception) {} }.also { it.start() }
        val run = connect(server.localPort, store = null)
        t.join(3000)
        assertNotNull(run.errors.singleOrNull())
        assertTrue(run.errors.single().message.contains("SSL is not available"))
    }
}

class TelnetClientDisconnectThreadTest {

    /**
     * Android forbids network I/O on the main thread, and closing an SSLSocket does network I/O.
     * So disconnect() must hand the close to another thread; if it closed synchronously, this
     * test would see the calling thread here.
     */
    @Test fun disconnectClosesTheSocketOffTheCallingThread() {
        val closedOn = java.util.concurrent.atomic.AtomicReference<Thread?>()
        val server = ServerSocket(0)
        val serverThread = Thread {
            try { server.use { it.accept().use { s -> s.getOutputStream().write("hi\r\n".toByteArray()); Thread.sleep(1500) } } } catch (_: Exception) {}
        }.also { it.start() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = TelnetClient(
            scope,
            keepAliveTuner = TcpKeepAlive.Tuner { _, _, _, _ -> },
            closeSocket = { closedOn.set(Thread.currentThread()); it.close() },
        )
        scope.launch { client.connect("127.0.0.1", server.localPort) }
        Thread.sleep(500) // connected and reading

        val caller = Thread.currentThread()
        client.disconnect()

        val deadline = System.currentTimeMillis() + 3000
        while (closedOn.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        scope.cancel(); serverThread.join(3000)
        assertNotNull("the socket was never closed", closedOn.get())
        assertTrue("close ran on the calling thread", closedOn.get() !== caller)
    }

    @Test fun disconnectWithNothingConnectedIsHarmless() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        TelnetClient(scope).disconnect()
        scope.cancel()
    }
}

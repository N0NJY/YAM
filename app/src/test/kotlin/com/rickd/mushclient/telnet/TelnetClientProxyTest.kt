package com.rickd.mushclient.telnet

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
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
import org.junit.Assert.assertTrue
import org.junit.Test

/** SOCKS4/4a on real sockets: a real client, a fake relaying proxy, real target servers. */
class TelnetClientProxyTest {

    /**
     * A tiny SOCKS4/4a proxy. It records the request it receives, answers with [status], and (when
     * granted) relays to the requested destination — resolving a SOCKS4a hostname itself, which is
     * the point. [replyInTwoPieces] splits the 8-byte reply across two writes.
     */
    private class FakeProxy(
        private val status: Int = 0x5A,
        private val replyInTwoPieces: Boolean = false,
        private val closeWithoutReply: Boolean = false,
    ) {
        val server = ServerSocket(0)
        val port get() = server.localPort
        @Volatile var request: ByteArray = ByteArray(0)

        init {
            Thread {
                try {
                    server.accept().use { client ->
                        val inp = client.getInputStream()
                        val head = ByteArray(8); var n = 0
                        while (n < 8) n += inp.read(head, n, 8 - n)
                        val rest = ByteArrayOutputStream()
                        while (true) { val b = inp.read(); if (b <= 0) break; rest.write(b) } // user id (NUL-terminated)
                        val isSocks4a = head[4].toInt() == 0 && head[5].toInt() == 0 && head[6].toInt() == 0 && head[7].toInt() != 0
                        val host = if (isSocks4a) {
                            val h = ByteArrayOutputStream(); while (true) { val b = inp.read(); if (b <= 0) break; h.write(b) }; String(h.toByteArray())
                        } else head.copyOfRange(4, 8).joinToString(".") { (it.toInt() and 0xFF).toString() }
                        val port = ((head[2].toInt() and 0xFF) shl 8) or (head[3].toInt() and 0xFF)
                        request = head + rest.toByteArray() + (if (isSocks4a) host.toByteArray() + byteArrayOf(0) else ByteArray(0))
                        if (closeWithoutReply) return@use
                        val reply = byteArrayOf(0, status.toByte(), 0, 0, 0, 0, 0, 0)
                        if (status != 0x5A) { client.getOutputStream().write(reply); return@use }
                        val target = Socket(InetAddress.getByName(host), port)
                        if (replyInTwoPieces) {
                            client.getOutputStream().write(reply, 0, 3); client.getOutputStream().flush(); Thread.sleep(120)
                            client.getOutputStream().write(reply, 3, 5)
                        } else client.getOutputStream().write(reply)
                        client.getOutputStream().flush()
                        val up = Thread { runCatching { inp.copyTo(target.getOutputStream()) }; runCatching { target.shutdownOutput() } }.also { it.start() }
                        runCatching { target.getInputStream().copyTo(client.getOutputStream()) }
                        up.join(500); target.close()
                    }
                } catch (_: Exception) {
                } finally { runCatching { server.close() } }
            }.start()
        }
    }

    private fun plainTarget(banner: String): Int {
        val s = ServerSocket(0)
        Thread { try { s.use { it.accept().use { c -> c.getOutputStream().write(banner.toByteArray()); Thread.sleep(300) } } } catch (_: Exception) {} }.start()
        return s.localPort
    }

    private fun tlsTarget(cert: String, banner: String): Int {
        val s = TlsTestSupport.serverContext(cert).serverSocketFactory.createServerSocket(0) as SSLServerSocket
        Thread { try { s.use { it.accept().use { c -> c.getOutputStream().write(banner.toByteArray()); Thread.sleep(300) } } } catch (_: Exception) {} }.start()
        return s.localPort
    }

    private fun deadPort(): Int = ServerSocket(0).use { it.localPort }

    private class Run(val events: List<TelnetEvent>) {
        val text get() = events.filterIsInstance<TelnetEvent.Text>().joinToString("") { it.chunk }
        val errors get() = events.filterIsInstance<TelnetEvent.Error>()
        val notices get() = events.filterIsInstance<TelnetEvent.Notice>().map { it.message }
    }

    private fun connect(targets: List<ConnectionTarget>, proxy: Socks4Proxy?, store: PinStore? = null): Run {
        val events = CopyOnWriteArrayList<TelnetEvent>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = TelnetClient(scope, keepAliveTuner = TcpKeepAlive.Tuner { _, _, _, _ -> }, pinStore = store)
        scope.launch { client.events.collect { events += it } }
        runBlocking { delay(80) }
        scope.launch { client.connect(targets, proxy = proxy) }
        val deadline = System.currentTimeMillis() + 12_000
        while (System.currentTimeMillis() < deadline &&
            events.none { it is TelnetEvent.StateChanged && it.state == ConnectionState.DISCONNECTED }
        ) Thread.sleep(25)
        client.disconnect(); scope.cancel()
        return Run(events.toList())
    }

    @Test fun anIpLiteralTargetGoesThroughTheProxyAsClassicSocks4() {
        val target = plainTarget("VIA PROXY\r\n")
        val proxy = FakeProxy()
        val run = connect(listOf(ConnectionTarget("127.0.0.1", target)), Socks4Proxy("127.0.0.1", proxy.port))
        assertTrue(run.text.contains("VIA PROXY"))
        val r = proxy.request
        assertEquals(listOf(4, 1), listOf(r[0].toInt(), r[1].toInt()))
        assertEquals(target, ((r[2].toInt() and 0xFF) shl 8) or (r[3].toInt() and 0xFF))
        assertEquals(listOf(127, 0, 0, 1), r.slice(4..7).map { it.toInt() and 0xFF })
    }

    @Test fun aHostnameTargetIsResolvedByTheProxyNotByTheClient() {
        // "localhost" is sent by name (SOCKS4a); the proxy resolves it and relays to the real port.
        val target = plainTarget("RESOLVED BY PROXY\r\n")
        val proxy = FakeProxy()
        val run = connect(listOf(ConnectionTarget("localhost", target)), Socks4Proxy("127.0.0.1", proxy.port))
        assertTrue(run.text.contains("RESOLVED BY PROXY"))
        assertEquals(listOf(0, 0, 0, 1), proxy.request.slice(4..7).map { it.toInt() })
        assertTrue(String(proxy.request).contains("localhost"))
    }

    @Test fun aHostNameNoOneCouldResolveStillReachesTheProxyAsAName() {
        // The client must not try DNS itself: an unresolvable name is simply handed to the proxy,
        // which here refuses it (status 0x5B). The failure is the proxy's, and says so.
        val proxy = FakeProxy(status = 0x5B)
        val run = connect(listOf(ConnectionTarget("no-such-host.invalid", 4444)), Socks4Proxy("127.0.0.1", proxy.port))
        assertTrue(String(proxy.request).contains("no-such-host.invalid"))
        val err = run.errors.single()
        assertTrue(err.message, err.message.contains("The proxy refused the request for no-such-host.invalid:4444"))
        assertFalse(err.fatal)
    }

    @Test fun aProxyReplyArrivingInTwoPiecesIsStillRead() {
        val target = plainTarget("SLOW REPLY OK\r\n")
        val proxy = FakeProxy(replyInTwoPieces = true)
        val run = connect(listOf(ConnectionTarget("127.0.0.1", target)), Socks4Proxy("127.0.0.1", proxy.port))
        assertTrue(run.text.contains("SLOW REPLY OK"))
    }

    @Test fun anUnreachableProxyIsReportedAsTheProxysFault() {
        val run = connect(listOf(ConnectionTarget("127.0.0.1", 4444)), Socks4Proxy("127.0.0.1", deadPort()))
        val msg = run.errors.single().message
        assertTrue(msg, msg.contains("Couldn't reach the SOCKS4 proxy") && msg.contains("connection refused"))
    }

    @Test fun aProxyThatHangsUpWithoutAnsweringIsReported() {
        val proxy = FakeProxy(closeWithoutReply = true)
        val run = connect(listOf(ConnectionTarget("127.0.0.1", 4444)), Socks4Proxy("127.0.0.1", proxy.port))
        assertTrue(run.errors.single().message.contains("closed the connection before answering"))
    }

    @Test fun anIdentdRejectionHasItsOwnMessage() {
        val proxy = FakeProxy(status = 0x5C)
        val run = connect(listOf(ConnectionTarget("127.0.0.1", 4444)), Socks4Proxy("127.0.0.1", proxy.port))
        assertTrue(run.errors.single().message.contains("identd"))
    }

    @Test fun sslRunsEndToEndThroughTheProxyAndThePinIsForTheServerNotTheProxy() {
        val target = tlsTarget("tls-a.p12", "SECURE VIA PROXY\r\n")
        val proxy = FakeProxy()
        val store = InMemoryPinStore()
        val run = connect(listOf(ConnectionTarget("127.0.0.1", target, useSsl = true)), Socks4Proxy("127.0.0.1", proxy.port), store)
        assertTrue(run.notices.toString() + run.errors, run.text.contains("SECURE VIA PROXY"))
        assertEquals(TlsTestSupport.FINGERPRINT_A, store.pins["127.0.0.1:$target"])
        assertFalse("the proxy's own address must never get a pin", store.pins.containsKey("127.0.0.1:${proxy.port}"))
    }

    @Test fun theProxyAppliesToTheFallbackToo() {
        val dead = deadPort()
        val good = plainTarget("FALLBACK VIA PROXY\r\n")
        // First proxy connection (primary) is refused by the proxy; a second proxy serves the fallback.
        val refusing = FakeProxy(status = 0x5B)
        val run = connect(
            listOf(ConnectionTarget("127.0.0.1", dead), ConnectionTarget("127.0.0.1", good)),
            Socks4Proxy("127.0.0.1", refusing.port),
        )
        // The single-shot fake proxy served the primary attempt; the fallback attempt then finds the
        // proxy gone, which proves both attempts went through the proxy (never straight out).
        assertTrue(run.errors.single().message.contains("127.0.0.1:$dead") && run.errors.single().message.contains("127.0.0.1:$good"))
        assertFalse(run.text.contains("FALLBACK VIA PROXY"))
    }

    @Test fun withoutAProxyNothingChanges() {
        val target = plainTarget("DIRECT\r\n")
        assertTrue(connect(listOf(ConnectionTarget("127.0.0.1", target)), proxy = null).text.contains("DIRECT"))
    }
}

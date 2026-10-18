package com.rickd.mushclient.telnet

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Test

class FriendlyReasonTest {
    @Test fun androidsRawRefusedMessageBecomesPlainWords() {
        val raw = "failed to connect to /192.168.1.101 (port 4471) from /192.168.1.49 (port 47066) " +
            "after 10000ms: isConnected failed: ECONNREFUSED (Connection refused)"
        assertEquals("connection refused", friendlyReason(ConnectException(raw)))
    }

    @Test fun refusedIsRecognizedFromTheMessageEvenWithoutTheExceptionType() =
        assertEquals("connection refused", friendlyReason(IOException("Connection refused")))

    @Test fun timeouts() {
        assertEquals("timed out", friendlyReason(SocketTimeoutException("connect timed out")))
        assertEquals("timed out", friendlyReason(IOException("Read timed out")))
    }

    @Test fun unknownHost() = assertEquals("host not found", friendlyReason(UnknownHostException("no.such.host")))

    @Test fun noRoute() {
        assertEquals("no route to host", friendlyReason(NoRouteToHostException("No route to host")))
        assertEquals("no route to host", friendlyReason(IOException("connect failed: EHOSTUNREACH (No route to host)")))
    }

    @Test fun networkUnreachable() =
        assertEquals("network unreachable", friendlyReason(ConnectException("connect failed: ENETUNREACH (Network is unreachable)")))

    @Test fun anUnrecognizedFailureKeepsItsOwnText() =
        assertEquals("Remote host terminated the handshake", friendlyReason(SSLHandshakeException("Remote host terminated the handshake")))

    @Test fun aBlankMessageFallsBackToTheTypeName() = assertEquals("IOException", friendlyReason(IOException()))
}

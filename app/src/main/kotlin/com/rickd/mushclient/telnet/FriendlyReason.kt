package com.rickd.mushclient.telnet

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Plain words for the common ways a connection attempt fails, in place of Java's raw message
 * (which reads like "failed to connect to /10.0.0.1 (port 4471) from /10.0.0.2 (port 47066)
 * after 10000ms: isConnected failed: ECONNREFUSED"). Anything unrecognized keeps its own text.
 */
fun friendlyReason(e: IOException): String {
    val message = e.message.orEmpty()
    return when {
        // Already phrased for the player (and mentions the proxy), so it must not be re-labelled.
        e is ProxyException -> message
        e is UnknownHostException -> "host not found"
        e is SocketTimeoutException || message.contains("timed out", ignoreCase = true) -> "timed out"
        e is NoRouteToHostException || message.contains("EHOSTUNREACH") || message.contains("No route to host", ignoreCase = true) -> "no route to host"
        e is ConnectException && message.contains("ENETUNREACH") -> "network unreachable"
        e is ConnectException || message.contains("ECONNREFUSED") || message.contains("refused", ignoreCase = true) -> "connection refused"
        message.isNotBlank() -> message
        else -> e.javaClass.simpleName
    }
}

package com.rickd.mushclient.telnet

/** One address a connection attempt can dial. */
data class ConnectionTarget(val host: String, val port: Int, val useSsl: Boolean = false) {
    val label: String get() = "$host:$port"
}

/** The proxy to use, or null if none is configured; both host and a valid port are required. */
fun socks4ProxyOrNull(host: String, port: Int): Socks4Proxy? {
    val trimmed = host.trim()
    return if (trimmed.isNotEmpty() && port in 1..65535) Socks4Proxy(trimmed, port) else null
}

/**
 * The addresses to try, in order: the primary, then the fallback if one is configured. The
 * fallback counts only when both its host and a valid port are set — a half-filled one is
 * ignored rather than guessed at. The list is rebuilt for every connect (so the primary is
 * always tried first; the fallback is never "remembered" as the better one).
 */
fun connectionTargets(
    primary: ConnectionTarget,
    fallbackHost: String,
    fallbackPort: Int,
    fallbackSsl: Boolean,
): List<ConnectionTarget> {
    val host = fallbackHost.trim()
    return if (host.isNotEmpty() && fallbackPort in 1..65535) {
        listOf(primary, ConnectionTarget(host, fallbackPort, fallbackSsl))
    } else {
        listOf(primary)
    }
}

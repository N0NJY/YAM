package com.rickd.mushclient.telnet

/** One address a connection attempt can dial. */
data class ConnectionTarget(val host: String, val port: Int, val useSsl: Boolean = false) {
    val label: String get() = "$host:$port"
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

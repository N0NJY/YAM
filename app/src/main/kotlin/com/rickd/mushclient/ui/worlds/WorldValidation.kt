package com.rickd.mushclient.ui.worlds

/** What the World Properties "Basic" page checks before it lets you save. */
fun validateWorldBasics(name: String, host: String, portText: String): String? {
    if (name.isBlank()) return "Give the world a name."
    if (host.isBlank()) return "Enter a host."
    val port = portText.trim().toIntOrNull() ?: return "The port must be a number."
    if (port !in 1..65535) return "The port must be between 1 and 65535."
    return null
}

/**
 * The fallback address is optional, but half of one is a mistake worth catching: it needs a host
 * and a port together (both empty means "no fallback").
 */
fun validateFallback(host2: String, port2Text: String): String? {
    val host = host2.trim()
    val portText = port2Text.trim()
    if (host.isEmpty() && portText.isEmpty()) return null
    if (host.isEmpty()) return "Enter a host for the fallback address, or clear its port."
    if (portText.isEmpty()) return "Enter a port for the fallback address, or clear its host."
    val port = portText.toIntOrNull() ?: return "The fallback port must be a number."
    if (port !in 1..65535) return "The fallback port must be between 1 and 65535."
    return null
}

/** Same rule as the fallback address: a proxy needs a host and a port together, or neither. */
fun validateProxy(host: String, portText: String): String? {
    val h = host.trim()
    val p = portText.trim()
    if (h.isEmpty() && p.isEmpty()) return null
    if (h.isEmpty()) return "Enter a host for the proxy, or clear its port."
    if (p.isEmpty()) return "Enter a port for the proxy, or clear its host."
    val port = p.toIntOrNull() ?: return "The proxy port must be a number."
    if (port !in 1..65535) return "The proxy port must be between 1 and 65535."
    return null
}

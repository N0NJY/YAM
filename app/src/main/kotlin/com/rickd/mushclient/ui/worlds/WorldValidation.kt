package com.rickd.mushclient.ui.worlds

/** What the World Properties "Basic" page checks before it lets you save. */
fun validateWorldBasics(name: String, host: String, portText: String): String? {
    if (name.isBlank()) return "Give the world a name."
    if (host.isBlank()) return "Enter a host."
    val port = portText.trim().toIntOrNull() ?: return "The port must be a number."
    if (port !in 1..65535) return "The port must be between 1 and 65535."
    return null
}

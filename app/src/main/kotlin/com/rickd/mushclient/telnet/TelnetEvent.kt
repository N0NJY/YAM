package com.rickd.mushclient.telnet

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
}

sealed class TelnetEvent {
    data class Text(val chunk: String) : TelnetEvent()
    data class Subnegotiation(val optionCode: Int, val data: ByteArray) : TelnetEvent()
    data class StateChanged(val state: ConnectionState) : TelnetEvent()
    data class Error(val message: String, val cause: Throwable? = null) : TelnetEvent()
    // Human-readable description of a raw IAC exchange (negotiation or subnegotiation).
    // Always emitted — cheap, since negotiation only happens a handful of times per
    // connection — the /debug slash command decides whether to actually display them.
    data class Debug(val message: String) : TelnetEvent()
}

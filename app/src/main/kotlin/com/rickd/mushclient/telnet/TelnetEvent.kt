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
    // fatal = retrying can't help and would be unsafe (e.g. a changed SSL certificate), so the
    // session must not auto-reconnect after this.
    data class Error(val message: String, val cause: Throwable? = null, val fatal: Boolean = false) : TelnetEvent()
    // A plain-language note the player should always see (unlike [Debug]), e.g. a newly trusted
    // SSL certificate.
    data class Notice(val message: String) : TelnetEvent()
    // Human-readable description of a raw IAC exchange (negotiation or subnegotiation).
    // Always emitted — cheap, since negotiation only happens a handful of times per
    // connection — the /debug slash command decides whether to actually display them.
    data class Debug(val message: String) : TelnetEvent()
}

package com.rickd.mushclient.telnet

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
}

sealed class TelnetEvent {
    data class Text(val chunk: String) : TelnetEvent()
    data class Subnegotiation(val optionCode: Int, val data: ByteArray) : TelnetEvent()
    data class StateChanged(val state: ConnectionState) : TelnetEvent()
    data class Error(val message: String, val cause: Throwable? = null) : TelnetEvent()
}

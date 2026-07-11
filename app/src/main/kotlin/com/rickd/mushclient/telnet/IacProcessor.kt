package com.rickd.mushclient.telnet

import java.io.ByteArrayOutputStream

/**
 * Pure byte-stream telnet (RFC 854) IAC state machine. No I/O, no coroutines —
 * feed it raw bytes as they arrive from the socket (across as many calls as needed;
 * state persists between calls so an IAC sequence split across two socket reads is
 * handled correctly) and it returns decoded [RawEvent]s.
 */
class IacProcessor {

    sealed class RawEvent {
        data class Data(val bytes: ByteArray) : RawEvent()
        data class Negotiation(val command: Int, val option: Int) : RawEvent()
        data class Subnegotiation(val option: Int, val data: ByteArray) : RawEvent()
    }

    private enum class State {
        NORMAL,
        SEEN_IAC,
        SEEN_NEGOTIATION_COMMAND,
        IN_SUBNEG,
        IN_SUBNEG_SEEN_IAC,
    }

    private var state = State.NORMAL
    private var pendingCommand = 0
    private val dataBuffer = ByteArrayOutputStream()
    private val subnegBuffer = ByteArrayOutputStream()

    fun process(input: ByteArray, length: Int = input.size): List<RawEvent> {
        val events = mutableListOf<RawEvent>()
        for (i in 0 until length) {
            val b = input[i].toInt() and 0xFF
            when (state) {
                State.NORMAL -> {
                    if (b == IAC) {
                        flushData(events)
                        state = State.SEEN_IAC
                    } else {
                        dataBuffer.write(b)
                    }
                }

                State.SEEN_IAC -> when (b) {
                    IAC -> {
                        // Escaped literal 0xFF byte in the data stream.
                        dataBuffer.write(0xFF)
                        state = State.NORMAL
                    }
                    SB -> {
                        subnegBuffer.reset()
                        state = State.IN_SUBNEG
                    }
                    WILL, WONT, DO, DONT -> {
                        pendingCommand = b
                        state = State.SEEN_NEGOTIATION_COMMAND
                    }
                    else -> {
                        // Single-byte commands (NOP, GA, AYT, etc.) carry no option byte.
                        state = State.NORMAL
                    }
                }

                State.SEEN_NEGOTIATION_COMMAND -> {
                    events.add(RawEvent.Negotiation(pendingCommand, b))
                    state = State.NORMAL
                }

                State.IN_SUBNEG -> {
                    if (b == IAC) {
                        state = State.IN_SUBNEG_SEEN_IAC
                    } else {
                        subnegBuffer.write(b)
                    }
                }

                State.IN_SUBNEG_SEEN_IAC -> when (b) {
                    SE -> {
                        val bytes = subnegBuffer.toByteArray()
                        val option = if (bytes.isNotEmpty()) bytes[0].toInt() and 0xFF else -1
                        val data = if (bytes.size > 1) bytes.copyOfRange(1, bytes.size) else ByteArray(0)
                        events.add(RawEvent.Subnegotiation(option, data))
                        state = State.NORMAL
                    }
                    IAC -> {
                        // Escaped literal 0xFF inside a subnegotiation block (RFC 855).
                        subnegBuffer.write(0xFF)
                        state = State.IN_SUBNEG
                    }
                    else -> {
                        // Malformed (IAC followed by neither SE nor IAC while inside a
                        // subnegotiation); keep the byte and resync rather than dropping data.
                        subnegBuffer.write(b)
                        state = State.IN_SUBNEG
                    }
                }
            }
        }
        flushData(events)
        return events
    }

    private fun flushData(events: MutableList<RawEvent>) {
        if (dataBuffer.size() > 0) {
            events.add(RawEvent.Data(dataBuffer.toByteArray()))
            dataBuffer.reset()
        }
    }

    companion object {
        const val IAC = 255
        const val SE = 240
        const val SB = 250
        const val WILL = 251
        const val WONT = 252
        const val DO = 253
        const val DONT = 254
    }
}

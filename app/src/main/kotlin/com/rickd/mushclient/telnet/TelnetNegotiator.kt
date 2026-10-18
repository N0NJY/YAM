package com.rickd.mushclient.telnet

import java.io.ByteArrayOutputStream

/**
 * Decides how to answer telnet option negotiation, as pure bytes-in/bytes-out logic.
 *
 * YAM still refuses almost every option, but supports two the way MushTato does:
 *  - NAWS (RFC 1073): tells the server the window size, a fixed [cols] x [rows] rather than
 *    the real screen — servers wrap their output at that width, and 80 keeps banners and tables
 *    lined up everywhere (same choice as MushTato and Potato).
 *  - TERM-TYPE (RFC 1091): when asked, names this client as [terminalName].
 *
 * Each option is agreed to once per connection: a repeated DO for something already enabled is
 * not answered again (RFC 1143), which is what stops two chatty ends from looping.
 */
class TelnetNegotiator(
    private val terminalName: String = DEFAULT_TERMINAL_NAME,
    private val cols: Int = DEFAULT_COLS,
    private val rows: Int = DEFAULT_ROWS,
) {
    /** One thing to write to the server, plus a human-readable note for `/debug`. */
    class Reply(val bytes: ByteArray, val description: String)

    private var nawsEnabled = false
    private var ttypeEnabled = false

    fun onNegotiation(command: Int, option: Int): List<Reply> = when (option) {
        OPT_NAWS -> when (command) {
            IacProcessor.DO -> if (nawsEnabled) emptyList() else {
                nawsEnabled = true
                listOf(
                    negotiation(IacProcessor.WILL, option, "WILL NAWS"),
                    Reply(nawsSubnegotiation(), "SB NAWS $cols x $rows"),
                )
            }
            IacProcessor.DONT -> if (nawsEnabled) {
                nawsEnabled = false
                listOf(negotiation(IacProcessor.WONT, option, "WONT NAWS"))
            } else emptyList()
            else -> refuse(command, option)
        }
        OPT_TTYPE -> when (command) {
            IacProcessor.DO -> if (ttypeEnabled) emptyList() else {
                ttypeEnabled = true
                listOf(negotiation(IacProcessor.WILL, option, "WILL TTYPE"))
            }
            IacProcessor.DONT -> if (ttypeEnabled) {
                ttypeEnabled = false
                listOf(negotiation(IacProcessor.WONT, option, "WONT TTYPE"))
            } else emptyList()
            else -> refuse(command, option)
        }
        else -> refuse(command, option)
    }

    /** Answers a subnegotiation block; only `TTYPE SEND` (while agreed to) gets a reply. */
    fun onSubnegotiation(option: Int, data: ByteArray): List<Reply> {
        if (option == OPT_TTYPE && ttypeEnabled && data.isNotEmpty() && data[0].toInt() == TTYPE_SEND) {
            return listOf(Reply(ttypeIs(), "SB TTYPE IS $terminalName"))
        }
        return emptyList()
    }

    /** Everything else is refused, exactly as YAM always has: WILL/WONT -> DONT, DO/DONT -> WONT. */
    private fun refuse(command: Int, option: Int): List<Reply> = when (command) {
        IacProcessor.WILL, IacProcessor.WONT ->
            listOf(negotiation(IacProcessor.DONT, option, "DONT $option"))
        IacProcessor.DO, IacProcessor.DONT ->
            listOf(negotiation(IacProcessor.WONT, option, "WONT $option"))
        else -> emptyList()
    }

    private fun negotiation(command: Int, option: Int, description: String) =
        Reply(byteArrayOf(IacProcessor.IAC.toByte(), command.toByte(), option.toByte()), description)

    private fun nawsSubnegotiation(): ByteArray = ByteArrayOutputStream().apply {
        write(IacProcessor.IAC); write(IacProcessor.SB); write(OPT_NAWS)
        // 16-bit big-endian width then height; any 0xFF data byte is doubled (RFC 855).
        for (b in intArrayOf(cols shr 8 and 0xFF, cols and 0xFF, rows shr 8 and 0xFF, rows and 0xFF)) {
            write(b)
            if (b == IacProcessor.IAC) write(IacProcessor.IAC)
        }
        write(IacProcessor.IAC); write(IacProcessor.SE)
    }.toByteArray()

    private fun ttypeIs(): ByteArray = ByteArrayOutputStream().apply {
        write(IacProcessor.IAC); write(IacProcessor.SB); write(OPT_TTYPE); write(TTYPE_IS)
        for (b in terminalName.toByteArray(Charsets.US_ASCII)) {
            val v = b.toInt() and 0xFF
            write(v)
            if (v == IacProcessor.IAC) write(IacProcessor.IAC)
        }
        write(IacProcessor.IAC); write(IacProcessor.SE)
    }.toByteArray()

    companion object {
        const val OPT_TTYPE = 24
        const val OPT_NAWS = 31
        private const val TTYPE_IS = 0
        private const val TTYPE_SEND = 1
        const val DEFAULT_TERMINAL_NAME = "YAM"
        const val DEFAULT_COLS = 80
        const val DEFAULT_ROWS = 24
    }
}

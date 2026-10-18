package com.rickd.mushclient.telnet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelnetNegotiatorTest {

    private val IAC = 255
    private val SB = 250
    private val SE = 240
    private val WILL = 251
    private val WONT = 252
    private val DO = 253
    private val DONT = 254

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun List<TelnetNegotiator.Reply>.wire(): List<List<Int>> =
        map { r -> r.bytes.map { it.toInt() and 0xFF } }

    @Test fun doNawsAnswersWillThenTheWindowSize() {
        val replies = TelnetNegotiator().onNegotiation(DO, 31)
        assertEquals(
            listOf(
                listOf(IAC, WILL, 31),
                listOf(IAC, SB, 31, 0, 80, 0, 24, IAC, SE),
            ),
            replies.wire(),
        )
    }

    @Test fun sizeIsConfigurableAndSixteenBitBigEndian() {
        val replies = TelnetNegotiator(cols = 300, rows = 1000).onNegotiation(DO, 31)
        // 300 = 0x012C, 1000 = 0x03E8
        assertEquals(listOf(IAC, SB, 31, 1, 0x2C, 3, 0xE8, IAC, SE), replies.wire()[1])
    }

    @Test fun aByteOf255InTheSizeIsDoubledSoItCannotBeReadAsAnIac() {
        val replies = TelnetNegotiator(cols = 255, rows = 24).onNegotiation(DO, 31)
        assertEquals(listOf(IAC, SB, 31, 0, IAC, IAC, 0, 24, IAC, SE), replies.wire()[1])
    }

    @Test fun aRepeatedDoNawsIsNotAnsweredAgain() {
        val n = TelnetNegotiator()
        assertEquals(2, n.onNegotiation(DO, 31).size)
        assertTrue(n.onNegotiation(DO, 31).isEmpty())
    }

    @Test fun dontNawsTurnsItOffOnceThenCanBeEnabledAgain() {
        val n = TelnetNegotiator()
        n.onNegotiation(DO, 31)
        assertEquals(listOf(listOf(IAC, WONT, 31)), n.onNegotiation(DONT, 31).wire())
        assertTrue(n.onNegotiation(DONT, 31).isEmpty())
        assertEquals(2, n.onNegotiation(DO, 31).size)
    }

    @Test fun doTtypeAnswersWillAndWaitsToBeAsked() {
        assertEquals(listOf(listOf(IAC, WILL, 24)), TelnetNegotiator().onNegotiation(DO, 24).wire())
    }

    @Test fun ttypeSendGetsTheClientName() {
        val n = TelnetNegotiator()
        n.onNegotiation(DO, 24)
        val replies = n.onSubnegotiation(24, bytes(1))
        assertEquals(
            listOf(listOf(IAC, SB, 24, 0, 'Y'.code, 'A'.code, 'M'.code, IAC, SE)),
            replies.wire(),
        )
    }

    @Test fun ttypeSendIsAnsweredEveryTimeItIsAsked() {
        val n = TelnetNegotiator()
        n.onNegotiation(DO, 24)
        assertEquals(1, n.onSubnegotiation(24, bytes(1)).size)
        assertEquals(1, n.onSubnegotiation(24, bytes(1)).size)
    }

    @Test fun ttypeSendBeforeAgreeingGetsNoReply() {
        assertTrue(TelnetNegotiator().onSubnegotiation(24, bytes(1)).isEmpty())
    }

    @Test fun otherSubnegotiationsAreIgnored() {
        val n = TelnetNegotiator()
        n.onNegotiation(DO, 24)
        assertTrue(n.onSubnegotiation(24, bytes(0, 'x'.code)).isEmpty())   // TTYPE IS from the server
        assertTrue(n.onSubnegotiation(201, bytes(1, 2, 3)).isEmpty())       // GMCP
    }

    @Test fun customTerminalNameIsUsed() {
        val n = TelnetNegotiator(terminalName = "xterm")
        n.onNegotiation(DO, 24)
        val wire = n.onSubnegotiation(24, bytes(1)).wire()[0]
        assertEquals("xterm", wire.subList(4, wire.size - 2).map { it.toChar() }.joinToString(""))
    }

    @Test fun everyOtherOptionIsRefusedAsBefore() {
        val n = TelnetNegotiator()
        assertEquals(listOf(listOf(IAC, WONT, 1)), n.onNegotiation(DO, 1).wire())      // DO ECHO
        assertEquals(listOf(listOf(IAC, WONT, 91)), n.onNegotiation(DONT, 91).wire())  // DONT MXP
        assertEquals(listOf(listOf(IAC, DONT, 201)), n.onNegotiation(WILL, 201).wire()) // WILL GMCP
        assertEquals(listOf(listOf(IAC, DONT, 70)), n.onNegotiation(WONT, 70).wire())   // WONT MSSP
    }

    @Test fun willNawsFromTheServerIsRefusedNotAccepted() {
        // The server announcing it will send NAWS makes no sense; we must not enable anything.
        val n = TelnetNegotiator()
        assertEquals(listOf(listOf(IAC, DONT, 31)), n.onNegotiation(WILL, 31).wire())
        assertEquals(2, n.onNegotiation(DO, 31).size)
    }

    @Test fun replyBytesAreIndependentCopies() {
        val r = TelnetNegotiator().onNegotiation(DO, 31)[0]
        assertArrayEquals(bytes(IAC, WILL, 31), r.bytes)
    }
}

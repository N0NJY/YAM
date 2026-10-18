package com.rickd.mushclient.telnet

import com.rickd.mushclient.ui.worlds.validateProxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Socks4Test {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }

    @Test fun anIpv4LiteralIsSentAsClassicSocks4() {
        // VN=4 CD=1 port 4444 (0x115C) ip 192.168.1.101, user id = just the NUL
        assertEquals(
            hex(bytes(4, 1, 0x11, 0x5C, 192, 168, 1, 101, 0)),
            hex(Socks4.encodeRequest("192.168.1.101", 4444)),
        )
    }

    @Test fun aHostnameUsesSocks4aWithThePlaceholderAddressAndTheNameAfterTheUserId() {
        val expected = bytes(4, 1, 0x00, 0x50, 0, 0, 0, 1, 0) + "mush.example".toByteArray() + bytes(0)
        assertEquals(hex(expected), hex(Socks4.encodeRequest("mush.example", 80)))
    }

    @Test fun thePortIsTwoBytesBigEndian() {
        val r = Socks4.encodeRequest("1.2.3.4", 65535)
        assertEquals(listOf(0xFF, 0xFF), listOf(r[2].toInt() and 0xFF, r[3].toInt() and 0xFF))
        val r2 = Socks4.encodeRequest("1.2.3.4", 256)
        assertEquals(listOf(1, 0), listOf(r2[2].toInt() and 0xFF, r2[3].toInt() and 0xFF))
    }

    @Test fun anOutOfRangeDottedQuadIsTreatedAsAHostnameNotAnAddress() {
        // 999.1.1.1 is not an IP; sending it as four bytes would be wrong, so it goes as a name.
        val r = Socks4.encodeRequest("999.1.1.1", 80)
        assertEquals(listOf(0, 0, 0, 1), r.slice(4..7).map { it.toInt() })
        assertTrue(String(r.copyOfRange(9, r.size - 1)) == "999.1.1.1")
    }

    @Test fun aNonAsciiHostnameIsSentAsPunycode() {
        val r = Socks4.encodeRequest("bücher.example", 80)
        assertEquals("xn--bcher-kva.example", String(r.copyOfRange(9, r.size - 1)))
    }

    @Test fun anIpv6AddressIsRejectedClearlyBecauseSocks4CannotCarryIt() {
        try { Socks4.encodeRequest("::1", 80); fail() } catch (e: ProxyException) { assertTrue(e.message!!.contains("IPv6")) }
    }

    @Test fun badPortsAreRejected() {
        for (p in listOf(0, -1, 65536)) {
            try { Socks4.encodeRequest("h", p); fail("port $p") } catch (_: ProxyException) {}
        }
    }

    @Test fun proxyHelperRequiresBothHostAndAValidPort() {
        assertNull(socks4ProxyOrNull("", 1080))
        assertNull(socks4ProxyOrNull("proxy.example", 0))
        assertNull(socks4ProxyOrNull("   ", 1080))
        assertNull(socks4ProxyOrNull("proxy.example", 70000))
        assertEquals(Socks4Proxy("proxy.example", 1080), socks4ProxyOrNull(" proxy.example ", 1080))
    }

    @Test fun proxyValidationMirrorsTheFallbackRule() {
        assertNull(validateProxy("", ""))
        assertNull(validateProxy("p.example", "1080"))
        assertNotNull(validateProxy("p.example", ""))
        assertNotNull(validateProxy("", "1080"))
        assertNotNull(validateProxy("p", "abc"))
        assertNotNull(validateProxy("p", "0"))
        assertNotNull(validateProxy("p", "65536"))
    }

    @Test fun aProxyFailureMessageIsNotRelabelledByTheFriendlyReasonRules() {
        // Contains the word "refused" yet must stay as written, not collapse to "connection refused".
        val msg = "The proxy refused the request for h:1 (status 0x5B)."
        assertEquals(msg, friendlyReason(ProxyException(msg)))
    }
}

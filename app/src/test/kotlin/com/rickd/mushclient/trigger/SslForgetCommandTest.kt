package com.rickd.mushclient.trigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SslForgetCommandTest {
    private fun resolve(s: String) = SlashCommandEngine.resolve(s, emptyList())

    @Test fun hostColonPortIsParsed() {
        assertEquals(
            SlashResolution.Action(BuiltInAction.SslForget("mush.example", 4443)),
            resolve("/ssl-forget mush.example:4443"),
        )
    }

    @Test fun theCommandNameIsCaseInsensitiveAndSpacesAreTolerated() {
        assertEquals(
            SlashResolution.Action(BuiltInAction.SslForget("h", 1)),
            resolve("/SSL-Forget   h : 1 "),
        )
    }

    @Test fun anIpv6LiteralInBracketsWorks() {
        assertEquals(
            SlashResolution.Action(BuiltInAction.SslForget("::1", 4443)),
            resolve("/ssl-forget [::1]:4443"),
        )
    }

    @Test fun aMissingOrBadPortGivesUsageNeverSendsToTheServer() {
        for (bad in listOf("/ssl-forget", "/ssl-forget host", "/ssl-forget host:", "/ssl-forget host:abc",
            "/ssl-forget host:0", "/ssl-forget host:70000", "/ssl-forget :4443")) {
            val r = resolve(bad)
            assertTrue("$bad -> $r", r is SlashResolution.SystemMessage)
            assertEquals("Usage: /ssl-forget host:port", (r as SlashResolution.SystemMessage).text)
        }
    }

    @Test fun parserRejectsWhatItShould() {
        assertNull(SlashCommandEngine.parseHostColonPort("no-port"))
        assertNull(SlashCommandEngine.parseHostColonPort(""))
        assertEquals("h" to 22, SlashCommandEngine.parseHostColonPort("h:22"))
    }

    @Test fun doubleSlashStillEscapesToALiteralSend() {
        assertEquals(SlashResolution.Literal("/ssl-forget h:1"), resolve("//ssl-forget h:1"))
    }
}

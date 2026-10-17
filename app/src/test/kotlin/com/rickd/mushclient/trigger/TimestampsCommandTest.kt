package com.rickd.mushclient.trigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampsCommandTest {

    private fun resolve(input: String) = SlashCommandEngine.resolve(input, emptyList())

    private fun action(input: String): BuiltInAction =
        (resolve(input) as SlashResolution.Action).action

    @Test fun bareCommandToggles() {
        assertEquals(BuiltInAction.SetTimestamps(null), action("/timestamps"))
    }

    @Test fun onAndOffSetExplicitly() {
        assertEquals(BuiltInAction.SetTimestamps(true), action("/timestamps on"))
        assertEquals(BuiltInAction.SetTimestamps(false), action("/timestamps OFF"))
    }

    @Test fun unknownArgumentGivesUsageAndNeverSendsToTheServer() {
        val r = resolve("/timestamps banana")
        assertTrue(r is SlashResolution.SystemMessage)
        assertEquals("Usage: /timestamps [on|off]", (r as SlashResolution.SystemMessage).text)
    }

    @Test fun doubleSlashStillEscapesToALiteralSend() {
        assertEquals(SlashResolution.Literal("/timestamps"), resolve("//timestamps"))
    }
}

package com.rickd.mushclient.telnet

import com.rickd.mushclient.ui.worlds.validateFallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionTargetsTest {
    private val primary = ConnectionTarget("main.example", 4444, useSsl = false)

    @Test fun noFallbackConfiguredMeansJustThePrimary() {
        assertEquals(listOf(primary), connectionTargets(primary, "", 0, false))
    }

    @Test fun aCompleteFallbackFollowsThePrimaryWithItsOwnSslFlag() {
        assertEquals(
            listOf(primary, ConnectionTarget("backup.example", 5555, useSsl = true)),
            connectionTargets(primary, "backup.example", 5555, true),
        )
    }

    @Test fun theSslFlagsAreIndependent() {
        val ssl = ConnectionTarget("main.example", 4443, useSsl = true)
        assertEquals(false, connectionTargets(ssl, "b", 1, false)[1].useSsl)
        assertEquals(true, connectionTargets(primary, "b", 1, true)[1].useSsl)
    }

    @Test fun aHalfFilledFallbackIsIgnored() {
        assertEquals(listOf(primary), connectionTargets(primary, "backup.example", 0, false))
        assertEquals(listOf(primary), connectionTargets(primary, "", 5555, false))
        assertEquals(listOf(primary), connectionTargets(primary, "   ", 5555, false))
        assertEquals(listOf(primary), connectionTargets(primary, "backup.example", 70000, false))
        assertEquals(listOf(primary), connectionTargets(primary, "backup.example", -1, false))
    }

    @Test fun theFallbackHostIsTrimmed() {
        assertEquals("backup.example", connectionTargets(primary, "  backup.example ", 1, false)[1].host)
    }

    @Test fun theFallbackIsAlwaysSecondSoThePrimaryIsAlwaysTriedFirst() {
        val targets = connectionTargets(primary, "backup.example", 5555, false)
        assertEquals(primary, targets.first())
    }

    @Test fun labelIsHostColonPort() = assertEquals("main.example:4444", primary.label)

    // The Connection page's own check, kept next to the rule it protects.
    @Test fun noFallbackAtAllIsValid() = assertNull(validateFallback("", ""))

    @Test fun aCompleteFallbackIsValid() = assertNull(validateFallback("backup.example", "5555"))

    @Test fun halfAFallbackIsRejectedWithAMessage() {
        assertNotNull(validateFallback("backup.example", ""))
        assertNotNull(validateFallback("", "5555"))
        assertNotNull(validateFallback("   ", "5555"))
    }

    @Test fun aBadFallbackPortIsRejected() {
        assertNotNull(validateFallback("h", "abc"))
        assertNotNull(validateFallback("h", "0"))
        assertNotNull(validateFallback("h", "65536"))
        assertNull(validateFallback("h", " 65535 "))
    }
}

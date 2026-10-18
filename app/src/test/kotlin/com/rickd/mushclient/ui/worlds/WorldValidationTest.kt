package com.rickd.mushclient.ui.worlds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorldValidationTest {
    @Test fun aCompleteWorldIsValid() = assertNull(validateWorldBasics("Estrellita", "silvren.com", "4444"))

    @Test fun blankNameOrHostIsRejected() {
        assertNotNull(validateWorldBasics("", "h", "1"))
        assertNotNull(validateWorldBasics("   ", "h", "1"))
        assertNotNull(validateWorldBasics("n", "", "1"))
        assertNotNull(validateWorldBasics("n", "  ", "1"))
    }

    @Test fun portMustBeANumberInRange() {
        assertNotNull(validateWorldBasics("n", "h", ""))
        assertNotNull(validateWorldBasics("n", "h", "abc"))
        assertNotNull(validateWorldBasics("n", "h", "0"))
        assertNotNull(validateWorldBasics("n", "h", "65536"))
        assertNotNull(validateWorldBasics("n", "h", "-1"))
        assertNull(validateWorldBasics("n", "h", "1"))
        assertNull(validateWorldBasics("n", "h", "65535"))
    }

    @Test fun surroundingSpacesInThePortAreTolerated() = assertNull(validateWorldBasics("n", "h", " 4444 "))

    @Test fun messagesNameTheProblem() {
        assertEquals("Give the world a name.", validateWorldBasics("", "h", "1"))
        assertEquals("The port must be a number.", validateWorldBasics("n", "h", "x"))
    }
}

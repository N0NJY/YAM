package com.rickd.mushclient.ui.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasteConfirmTest {

    @Test fun detectsAnyLineBreakStyle() {
        assertTrue(hasLineBreak("a\nb"))
        assertTrue(hasLineBreak("a\r\nb"))
        assertTrue(hasLineBreak("a\rb"))
        assertFalse(hasLineBreak("just one line"))
        assertFalse(hasLineBreak(""))
    }

    @Test fun splitsOnLfCrlfAndCrInOrder() {
        assertEquals(listOf("look", "who", "north"), splitPastedLines("look\nwho\nnorth"))
        assertEquals(listOf("look", "who", "north"), splitPastedLines("look\r\nwho\r\nnorth"))
        assertEquals(listOf("look", "who", "north"), splitPastedLines("look\rwho\rnorth"))
    }

    @Test fun aTrailingLineBreakIsNotAnExtraLine() {
        assertEquals(listOf("a", "b"), splitPastedLines("a\nb\n"))
        assertEquals(listOf("a", "b"), splitPastedLines("a\r\nb\r\n"))
    }

    @Test fun blankAndWhitespaceOnlyLinesAreSkipped() {
        assertEquals(listOf("a", "b"), splitPastedLines("a\n\n   \nb"))
    }

    @Test fun leadingSpacesInsideALineArePreserved() {
        assertEquals(listOf("  indented", "x"), splitPastedLines("  indented\nx"))
    }

    @Test fun textThatIsOnlyBreaksYieldsNothing() {
        assertTrue(splitPastedLines("\n\r\n\n").isEmpty())
    }

    @Test fun aPasteOfOneRealLineCollapsesToThatLine() {
        assertEquals("look", collapseToSingleLine("look\n"))
        assertEquals("look", collapseToSingleLine("\nlook\r\n"))
    }
}

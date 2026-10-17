package com.rickd.mushclient.service

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogTest {

    private val now = LocalDateTime.of(2026, 10, 17, 11, 45, 2)

    @Test fun fileNameIsTimestampedAndKeepsSafeCharacters() {
        assertEquals("YAM-LocalRhost-20261017-114502.txt", SessionLog.fileName("LocalRhost", now))
    }

    @Test fun fileNameReplacesUnsafeCharactersAndCollapsesRuns() {
        assertEquals(
            "YAM-192.168.1.101_4444-20261017-114502.txt",
            SessionLog.fileName("192.168.1.101:4444", now),
        )
        assertEquals("YAM-The_Estrellita_Chronicles-20261017-114502.txt", SessionLog.fileName("The Estrellita / Chronicles!", now))
    }

    @Test fun fileNameNeverContainsPathSeparators() {
        val name = SessionLog.fileName("../../etc/passwd", now)
        assertFalse(name.contains('/'))
        assertTrue(name.startsWith("YAM-") && name.endsWith("-20261017-114502.txt"))
    }

    @Test fun emptyOrAllPunctuationLabelFallsBackToSession() {
        assertEquals("YAM-session-20261017-114502.txt", SessionLog.fileName("", now))
        assertEquals("YAM-session-20261017-114502.txt", SessionLog.fileName("///", now))
    }

    @Test fun logHasHeaderThenEveryLineInOrder() {
        val text = SessionLog.build("LocalRhost", "192.168.1.101", "4444", "guest", listOf("one", "two", "three"), now)
        val expected = """
            YAM session log
            World: LocalRhost (192.168.1.101:4444)
            Character: guest
            Saved: Sat, Oct 17 2026 11:45:02
            Lines: 3
            ----------------------------------------
            one
            two
            three

        """.trimIndent()
        assertEquals(expected, text)
    }

    @Test fun characterLineIsOmittedWhenThereIsNone() {
        assertFalse(SessionLog.build("W", "h", "1", null, listOf("x"), now).contains("Character:"))
        assertFalse(SessionLog.build("W", "h", "1", "  ", listOf("x"), now).contains("Character:"))
    }

    @Test fun blankLinesAreKeptSoSpacingSurvives() {
        val text = SessionLog.build("W", "h", "1", null, listOf("a", "", "b"), now)
        assertTrue(text.endsWith("a\n\nb\n"))
        assertTrue(text.contains("Lines: 3"))
    }

    @Test fun unicodeSurvivesRoundTripThroughUtf8() {
        val text = SessionLog.build("W", "h", "1", null, listOf("café — ½"), now)
        assertEquals(text, String(text.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
    }
}

package com.rickd.mushclient.ui.session

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FindBarTest {

    private val lines = listOf(
        "You say, \"Hello\"",
        "hello HELLO world",
        "nothing here",
        "a+b (c) [d] *?",
    )

    @Test fun caseInsensitiveByDefaultFindsEveryOccurrenceInOrder() {
        val m = findMatches(lines, "hello", caseSensitive = false)
        assertEquals(
            listOf(FindMatch(0, 10, 15), FindMatch(1, 0, 5), FindMatch(1, 6, 11)),
            m,
        )
    }

    @Test fun caseSensitiveOnlyFindsExactCase() {
        assertEquals(listOf(FindMatch(1, 6, 11)), findMatches(lines, "HELLO", caseSensitive = true))
        assertTrue(findMatches(lines, "HeLLo", caseSensitive = true).isEmpty())
    }

    @Test fun emptyQueryMatchesNothing() {
        assertTrue(findMatches(lines, "", caseSensitive = false).isEmpty())
    }

    @Test fun regexCharactersAreTreatedAsPlainText() {
        assertEquals(listOf(FindMatch(3, 0, 3)), findMatches(lines, "a+b", false))
        assertEquals(listOf(FindMatch(3, 4, 7)), findMatches(lines, "(c)", false))
        assertEquals(listOf(FindMatch(3, 12, 14)), findMatches(lines, "*?", false))
    }

    @Test fun matchesWithinALineDoNotOverlap() {
        assertEquals(
            listOf(FindMatch(0, 0, 2), FindMatch(0, 2, 4)),
            findMatches(listOf("aaaa"), "aa", false),
        )
    }

    @Test fun stepWrapsForwardAndBackward() {
        assertEquals(1, stepIndex(0, 3, +1))
        assertEquals(0, stepIndex(2, 3, +1))
        assertEquals(2, stepIndex(0, 3, -1))
        assertEquals(0, stepIndex(1, 3, -1))
    }

    @Test fun stepFromNoSelectionPicksAnEnd() {
        assertEquals(0, stepIndex(-1, 4, +1))
        assertEquals(3, stepIndex(-1, 4, -1))
    }

    @Test fun stepWithNoMatchesIsMinusOne() {
        assertEquals(-1, stepIndex(0, 0, +1))
        assertEquals(-1, stepIndex(-1, 0, -1))
    }

    @Test fun highlightLeavesTheTextAndOtherLinesUntouched() {
        val line = AnnotatedString("hello world")
        assertSame(line, line.withFindHighlights(null, null))
        assertSame(line, line.withFindHighlights(emptyList(), null))
        val out = line.withFindHighlights(listOf(FindMatch(0, 0, 5)), null)
        assertEquals("hello world", out.text)
    }

    @Test fun currentMatchGetsADifferentBackgroundThanTheRest() {
        val line = AnnotatedString("hello hello")
        val a = FindMatch(0, 0, 5)
        val b = FindMatch(0, 6, 11)
        val out = line.withFindHighlights(listOf(a, b), current = b)
        val bgAt = { i: Int -> out.spanStyles.filter { it.start <= i && i < it.end }.mapNotNull { it.item.background.takeIf { c -> c != Color.Unspecified } }.last() }
        assertTrue(bgAt(2) != bgAt(8))
    }

    @Test fun highlightKeepsExistingAnsiStyling() {
        val line = buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("find me") } }
        val out = line.withFindHighlights(listOf(FindMatch(0, 0, 4)), null)
        assertTrue(out.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == 7 })
    }
}

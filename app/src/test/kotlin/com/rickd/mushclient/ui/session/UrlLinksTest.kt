package com.rickd.mushclient.ui.session

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlLinksTest {

    private fun urls(s: String) = findUrls(s).map { s.substring(it) }

    @Test fun findsUrlMidSentence() {
        assertEquals(listOf("https://example.com/path"), urls("Visit https://example.com/path for more"))
    }

    @Test fun trimsTrailingSentencePunctuation() {
        assertEquals(listOf("https://example.com/a"), urls("See https://example.com/a."))
        assertEquals(listOf("https://example.com/a"), urls("(https://example.com/a)"))
        assertEquals(listOf("https://example.com/a?x=1"), urls("go to https://example.com/a?x=1, ok"))
    }

    @Test fun findsMultipleUrls() {
        assertEquals(
            listOf("http://a.org", "https://b.org/x"),
            urls("http://a.org and https://b.org/x"),
        )
    }

    @Test fun ignoresBareWwwAndPlainText() {
        assertTrue(findUrls("visit www.example.com today").isEmpty())
        assertTrue(findUrls("no links here").isEmpty())
    }

    @Test fun ignoresSchemeWithNothingAfterIt() {
        assertTrue(findUrls("see https:// later").isEmpty())
        assertTrue(findUrls("see https://. later").isEmpty())
    }

    @Test fun lineWithoutUrlsIsReturnedUnchanged() {
        val line = AnnotatedString("plain line")
        assertSame(line, line.withUrlLinks())
    }

    @Test fun addsLinkAnnotationOverExactUrlSpan() {
        val line = AnnotatedString("go https://example.com/a. now")
        val linked = line.withUrlLinks()
        val links = linked.getLinkAnnotations(0, linked.length)
        assertEquals(1, links.size)
        assertEquals(3, links[0].start)
        assertEquals(3 + "https://example.com/a".length, links[0].end)
    }

    @Test fun preservesExistingAnsiStylingUnderTheLink() {
        val line = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("bold https://example.com text") }
        }
        val linked = line.withUrlLinks()
        assertEquals("bold https://example.com text", linked.text)
        assertTrue(linked.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 0 && it.end == linked.length })
    }
}

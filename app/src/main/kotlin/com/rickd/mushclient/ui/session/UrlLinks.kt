package com.rickd.mushclient.ui.session

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

private val URL_REGEX = Regex("""https?://[^\s<>"]+""")
private const val TRAILING_PUNCTUATION = ".,;:!?'\")]}"

/**
 * Finds `http(s)://` URLs in [text]. Bare `www.` domains are deliberately not matched — there
 * is no reliable way to tell a real domain from an ordinary sentence fragment. Trailing
 * sentence punctuation is trimmed so "see https://x.org/a." links to `https://x.org/a`.
 */
fun findUrls(text: String): List<IntRange> =
    URL_REGEX.findAll(text).mapNotNull { match ->
        var end = match.range.last + 1
        while (end > match.range.first && text[end - 1] in TRAILING_PUNCTUATION) end--
        // Only a bare "https://" with nothing after it is not a link.
        if (end - match.range.first > match.value.indexOf("//") + 2) match.range.first until end else null
    }.toList()

/** Returns a copy of this line with every URL made tappable, keeping all existing styling. */
fun AnnotatedString.withUrlLinks(linkColor: Color = DEFAULT_LINK_COLOR): AnnotatedString {
    val urls = findUrls(text)
    if (urls.isEmpty()) return this
    return buildAnnotatedString {
        append(this@withUrlLinks)
        val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
        for (range in urls) {
            addLink(LinkAnnotation.Url(text.substring(range), style), range.first, range.last + 1)
        }
    }
}

private val DEFAULT_LINK_COLOR = Color(0xFF64B5F6)

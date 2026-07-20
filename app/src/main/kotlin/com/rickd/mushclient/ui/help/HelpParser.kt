package com.rickd.mushclient.ui.help

/** One `## `-delimited section of Help.md. */
data class HelpSection(val title: String, val lines: List<HelpLine>)

/** A single line of a section's body, pre-classified so the screen doesn't re-parse markdown. */
sealed class HelpLine {
    data class SubHeading(val text: String) : HelpLine()
    data class TableRow(val left: String, val right: String) : HelpLine()
    data class Bullet(val text: String) : HelpLine()
    data class Paragraph(val text: String) : HelpLine()
    data object Blank : HelpLine()
}

// Deliberately not a general markdown library — Help.md only ever uses a top-level title,
// "## " sections, "### " sub-headings, "- " bullets, and one "| a | b |" reference table, so a
// small hand-written classifier covers everything it actually contains.
private val TABLE_ROW = Regex("""^\|\s*(.*?)\s*\|\s*(.*?)\s*\|$""")
private val TABLE_SEPARATOR = Regex("""^\|[\s|:-]+\|$""")

fun parseHelp(raw: String): List<HelpSection> {
    val sections = mutableListOf<HelpSection>()
    var currentTitle: String? = null
    var currentLines = mutableListOf<HelpLine>()

    fun flush() {
        val title = currentTitle ?: return
        sections += HelpSection(title, currentLines.trimBlanks())
        currentLines = mutableListOf()
    }

    for (rawLine in raw.lines()) {
        val line = rawLine.trimEnd()
        when {
            line.startsWith("## ") -> {
                flush()
                currentTitle = line.removePrefix("## ").trim()
            }
            currentTitle == null -> Unit // content before the first "## " (the doc's "# Title" and intro) is skipped
            TABLE_SEPARATOR.matches(line) -> Unit // the "|---|---|" row under a table header
            line.isBlank() -> currentLines += HelpLine.Blank
            line.startsWith("### ") -> currentLines += HelpLine.SubHeading(line.removePrefix("### ").trim())
            line.startsWith("- ") -> currentLines += HelpLine.Bullet(line.removePrefix("- ").trim())
            else -> TABLE_ROW.matchEntire(line)?.let { match ->
                currentLines += HelpLine.TableRow(match.groupValues[1], match.groupValues[2])
            } ?: run { currentLines += HelpLine.Paragraph(line) }
        }
    }
    flush()
    return sections
}

private fun List<HelpLine>.trimBlanks(): List<HelpLine> =
    dropWhile { it is HelpLine.Blank }.dropLastWhile { it is HelpLine.Blank }

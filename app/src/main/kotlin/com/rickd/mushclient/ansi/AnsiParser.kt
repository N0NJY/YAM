package com.rickd.mushclient.ansi

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Stateful ANSI SGR (Select Graphic Rendition) parser: turns raw MUD/MUSH output lines
 * into [AnnotatedString]s. State (current fg/bg/bold/...) persists across calls to
 * [parseLine] because servers routinely set a color once and expect it to carry into
 * following lines without repeating the escape code — matches real terminal behavior.
 *
 * Non-SGR CSI sequences (cursor movement, clear-line, etc.) are recognized and dropped
 * silently rather than leaking their raw bytes into the scrollback.
 */
class AnsiParser {

    private sealed class AnsiColor {
        data class Indexed(val index: Int) : AnsiColor()
        data class Rgb(val color: Color) : AnsiColor()
    }

    private var fg: AnsiColor? = null
    private var bg: AnsiColor? = null
    private var bold = false
    private var italic = false
    private var underline = false
    private var reverse = false

    fun parseLine(raw: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        var runStart = 0

        fun flushRun(end: Int) {
            if (end > runStart) {
                withStyle(currentStyle()) { append(raw, runStart, end) }
            }
        }

        while (i < raw.length) {
            val c = raw[i]
            if (c == ESC) {
                flushRun(i)
                i = if (i + 1 < raw.length && raw[i + 1] == '[') {
                    consumeCsi(raw, i + 2)
                } else {
                    i + 1 // lone/unrecognized ESC — drop it
                }
                runStart = i
            } else {
                i++
            }
        }
        flushRun(raw.length)
    }

    /** Consumes a CSI sequence starting right after "ESC [". Returns the index past it. */
    private fun consumeCsi(raw: String, paramsStart: Int): Int {
        var j = paramsStart
        while (j < raw.length && (raw[j] < '@' || raw[j] > '~')) {
            j++
        }
        if (j >= raw.length) return raw.length // unterminated at chunk/line end — drop it
        val finalByte = raw[j]
        if (finalByte == 'm') {
            applySgr(raw.substring(paramsStart, j))
        }
        return j + 1
    }

    private fun applySgr(params: String) {
        val codes = if (params.isEmpty()) listOf(0) else params.split(';').map { it.toIntOrNull() ?: 0 }
        var idx = 0
        while (idx < codes.size) {
            when (val code = codes[idx]) {
                0 -> reset()
                1 -> bold = true
                2, 22 -> bold = false
                3 -> italic = true
                23 -> italic = false
                4 -> underline = true
                24 -> underline = false
                7 -> reverse = true
                27 -> reverse = false
                39 -> fg = null
                49 -> bg = null
                38 -> idx = consumeExtendedColor(codes, idx) { fg = it }
                48 -> idx = consumeExtendedColor(codes, idx) { bg = it }
                in 30..37 -> fg = AnsiColor.Indexed(code - 30)
                in 40..47 -> bg = AnsiColor.Indexed(code - 40)
                in 90..97 -> fg = AnsiColor.Indexed(code - 90 + 8)
                in 100..107 -> bg = AnsiColor.Indexed(code - 100 + 8)
                else -> Unit
            }
            idx++
        }
    }

    /** Handles "38;5;N" (256-color) and "38;2;R;G;B" (truecolor); returns the last consumed index. */
    private fun consumeExtendedColor(codes: List<Int>, start: Int, setColor: (AnsiColor) -> Unit): Int {
        if (start + 1 >= codes.size) return start
        return when (codes[start + 1]) {
            5 -> if (start + 2 < codes.size) {
                setColor(AnsiColor.Indexed(codes[start + 2]))
                start + 2
            } else start + 1
            2 -> if (start + 4 < codes.size) {
                val r = codes[start + 2].coerceIn(0, 255)
                val g = codes[start + 3].coerceIn(0, 255)
                val b = codes[start + 4].coerceIn(0, 255)
                setColor(AnsiColor.Rgb(Color(r, g, b)))
                start + 4
            } else start + 1
            else -> start + 1
        }
    }

    private fun reset() {
        fg = null
        bg = null
        bold = false
        italic = false
        underline = false
        reverse = false
    }

    private fun resolve(color: AnsiColor, brighten: Boolean): Color = when (color) {
        is AnsiColor.Rgb -> color.color
        is AnsiColor.Indexed -> {
            val index = if (brighten && color.index in 0..7) color.index + 8 else color.index
            AnsiPalette.color(index)
        }
    }

    private fun currentStyle(): SpanStyle {
        val resolvedFg = fg?.let { resolve(it, brighten = bold) }
        val resolvedBg = bg?.let { resolve(it, brighten = false) }
        // Reverse video with no explicit color set falls back to black-on-white rather
        // than trying to invert "inherit the Text composable's default" (Color.Unspecified).
        val (finalFg, finalBg) = if (reverse) {
            (resolvedBg ?: Color.Black) to (resolvedFg ?: Color.White)
        } else {
            resolvedFg to resolvedBg
        }
        return SpanStyle(
            color = finalFg ?: Color.Unspecified,
            background = finalBg ?: Color.Unspecified,
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
            textDecoration = if (underline) TextDecoration.Underline else null,
        )
    }

    companion object {
        private const val ESC = '\u001B'
    }
}

package com.rickd.mushclient.service

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Per-tab scrollback timestamps, modeled on MushTato's: a compact `[HH:MM:SS] ` prefix on each
 * new line while enabled, plus one full date-and-time marker line whenever the setting flips,
 * so a saved/copied log still says what day it was. Never rewrites lines already shown.
 */
object Timestamps {
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val FULL_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy HH:mm:ss")
    private val PREFIX_COLOR = Color(0xFF8A8A8A)

    fun prefix(time: LocalTime): String = "[${time.format(TIME_FORMAT)}] "

    /** Prepends a dimmed prefix; the line's own (ANSI) styling is preserved after it. */
    fun apply(line: AnnotatedString, time: LocalTime): AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(color = PREFIX_COLOR)) { append(prefix(time)) }
        append(line)
    }

    /** The full-date line printed when timestamps are switched on or off. */
    fun marker(enabled: Boolean, now: LocalDateTime): String =
        "--- Timestamps ${if (enabled) "ON" else "OFF"}: ${now.format(FULL_FORMAT)} ---"
}

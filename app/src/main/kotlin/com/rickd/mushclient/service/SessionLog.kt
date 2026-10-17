package com.rickd.mushclient.service

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Plain-text export of a tab's scrollback, modeled on MushTato's Save Spawnlog: UTF-8 text with
 * a short header, and a timestamped default filename. ANSI styling is not part of the text
 * (lines arrive here as already-parsed text), so the file reads the same in any editor.
 */
object SessionLog {
    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val HEADER_STAMP = DateTimeFormatter.ofPattern("EEE, MMM d yyyy HH:mm:ss")
    private const val RULE = "----------------------------------------"

    /** `YAM-<label>-yyyyMMdd-HHmmss.txt`, with anything awkward in the label made filename-safe. */
    fun fileName(label: String, now: LocalDateTime): String {
        val safe = label.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_', '.', '-').ifEmpty { "session" }
        return "YAM-$safe-${now.format(FILE_STAMP)}.txt"
    }

    fun build(
        label: String,
        host: String,
        port: String,
        characterName: String?,
        lines: List<String>,
        now: LocalDateTime,
    ): String = buildString {
        append("YAM session log\n")
        append("World: $label ($host:$port)\n")
        if (!characterName.isNullOrBlank()) append("Character: $characterName\n")
        append("Saved: ${now.format(HEADER_STAMP)}\n")
        append("Lines: ${lines.size}\n")
        append(RULE).append('\n')
        lines.forEach { append(it).append('\n') }
    }
}

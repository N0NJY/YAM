package com.rickd.mushclient.trigger

import com.rickd.mushclient.data.Trigger

/** Accumulated effect of every [Trigger] that matched one incoming line. */
data class TriggerOutcome(
    val omit: Boolean = false,
    val recolorForeground: Int? = null,
    val sendLines: List<String> = emptyList(),
    val inputText: String? = null,
) {
    companion object {
        val NONE = TriggerOutcome()
    }
}

/**
 * Evaluates [Trigger]s (via [PatternMatcher]) against one incoming line. Triggers are
 * evaluated in list order (callers pass world triggers before Global ones, matching
 * Potato's World-then-Global precedence) and stop at the first match whose
 * [Trigger.continueMatching] is false — matches that do continue accumulate into a single
 * [TriggerOutcome] (a later match's recolor/input overrides an earlier one's, sends from
 * every matching trigger are all kept).
 */
object TriggerEngine {

    // SGR and other CSI sequences (the ASCII ESC control character, code point 27, then '[',
    // params, a final letter) — stripped before matching so a pattern like "FugueEdit > *"
    // matches regardless of the server's color codes around it. Built from a code point
    // rather than a literal escape char in source to keep this file plain ASCII.
    private val ESCAPE_CHAR: Char = 27.toChar()
    private val ANSI_ESCAPE = Regex(Regex.escape(ESCAPE_CHAR.toString()) + "\\[[0-9;]*[A-Za-z]")

    fun stripAnsi(raw: String): String = raw.replace(ANSI_ESCAPE, "")

    fun evaluate(rawLine: String, triggers: List<Trigger>): TriggerOutcome {
        if (triggers.isEmpty()) return TriggerOutcome.NONE
        val line = stripAnsi(rawLine)

        var omit = false
        var recolor: Int? = null
        val sendLines = mutableListOf<String>()
        var inputText: String? = null

        for (trigger in triggers) {
            if (!trigger.enabled) continue
            val captures = PatternMatcher.captures(line, trigger.pattern, trigger.matchType, trigger.caseSensitive)
                ?: continue

            if (trigger.omitFromDisplay) omit = true
            trigger.recolorForeground?.let { recolor = it }
            trigger.sendText?.takeIf { it.isNotBlank() }?.let { template ->
                template.lines().filter { it.isNotBlank() }.forEach { sendLines += PatternMatcher.substitute(it, captures) }
            }
            trigger.inputText?.takeIf { it.isNotBlank() }?.let { inputText = PatternMatcher.substitute(it, captures) }

            if (!trigger.continueMatching) break
        }

        return TriggerOutcome(omit, recolor, sendLines, inputText)
    }
}

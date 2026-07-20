package com.rickd.mushclient.trigger

import com.rickd.mushclient.data.PatternMatchType

/**
 * Shared pattern-matching/substitution primitives behind both [TriggerEngine] (matching
 * incoming lines) and slash commands (matching command arguments). Kept free of
 * Android/Compose so it's testable in isolation.
 */
object PatternMatcher {

    /** Null if [pattern] doesn't match [text]; otherwise its captures, indices 0-9 (%0-%9). */
    fun captures(text: String, pattern: String, matchType: PatternMatchType, caseSensitive: Boolean): List<String>? {
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return when (matchType) {
            PatternMatchType.CONTAINS -> {
                if (pattern.isEmpty()) return null
                if (text.contains(pattern, ignoreCase = !caseSensitive)) emptyList() else null
            }
            PatternMatchType.REGEXP -> {
                val regex = runCatching { Regex(pattern, options) }.getOrNull() ?: return null
                val match = regex.find(text) ?: return null
                match.groupValues.drop(1).take(10)
            }
            PatternMatchType.WILDCARD -> {
                val regex = runCatching { Regex(globToRegex(pattern), options) }.getOrNull() ?: return null
                val match = regex.matchEntire(text) ?: return null
                match.groupValues.drop(1).take(10)
            }
        }
    }

    /** `*` (0+ chars) and `?` (1 char) each become a capturing group; the rest is escaped and anchored. */
    private fun globToRegex(glob: String): String {
        val sb = StringBuilder("^")
        for (c in glob) {
            when (c) {
                '*' -> sb.append("(.*)")
                '?' -> sb.append("(.)")
                else -> sb.append(Regex.escape(c.toString()))
            }
        }
        sb.append("$")
        return sb.toString()
    }

    fun substitute(template: String, captures: List<String>): String {
        var result = template
        for (i in captures.indices) {
            result = result.replace("%$i", captures[i])
        }
        return result
    }
}

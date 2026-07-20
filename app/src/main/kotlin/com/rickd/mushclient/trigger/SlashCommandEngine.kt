package com.rickd.mushclient.trigger

import com.rickd.mushclient.data.SlashCommand
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What a typed input line should actually put on the wire. */
sealed class SlashResolution {
    /** Send [text] unchanged (not a recognized command, or a "//" escape). */
    data class Literal(val text: String) : SlashResolution()

    /** Send each of [lines] instead of what was typed. */
    data class Expanded(val lines: List<String>) : SlashResolution()

    /** A local-only note (`/print`, `/time`) — shown in the scrollback, never sent to the MU*. */
    data class SystemMessage(val text: String) : SlashResolution()

    /** A built-in command whose effect isn't "send text" (`/cls`, `/web`, `/reconnect`, ...). */
    data class Action(val action: BuiltInAction) : SlashResolution()
}

/** Side effects a built-in slash command can request; [com.rickd.mushclient.service.MudConnectionService] carries them out. */
sealed class BuiltInAction {
    data object ClearScreen : BuiltInAction()
    data class OpenUrl(val url: String) : BuiltInAction()
    data object ReconnectNow : BuiltInAction()
    data object Disconnect : BuiltInAction()
    data class Connect(val address: String) : BuiltInAction()
    /** null means toggle rather than set an explicit state. */
    data class SetDebug(val enabled: Boolean?) : BuiltInAction()
}

/**
 * Resolves a line typed into the input bar. Checks a small fixed set of built-in commands
 * first (mirroring the handful of Potato's ~50 `/commands` that make sense on a single-window
 * mobile client — see ANALYSIS.md §6), then user-defined [SlashCommand]s. Unlike Potato, a
 * leading `/` is only ever special when it names something recognized — an unrecognized
 * `/foo` is sent to the MU* exactly as typed, so players who haven't defined any slash
 * commands see no change in behavior. `//foo` always sends `/foo` literally, escaping a name
 * on purpose (matches Potato's convention).
 */
object SlashCommandEngine {

    private val SPEEDWALK_DIRECTIONS = mapOf(
        "n" to "north", "s" to "south", "e" to "east", "w" to "west",
        "nw" to "northwest", "ne" to "northeast", "se" to "southeast", "sw" to "southwest",
        "u" to "up", "d" to "down", "o" to "out",
    )
    private val SPEEDWALK_TOKEN = Regex("^(\\d*)([A-Za-z]+)$")
    private val DEFAULT_TIME_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d yyyy h:mm:ss a")

    fun resolve(input: String, commands: List<SlashCommand>): SlashResolution {
        // A stray leading space (an easy touch-keyboard slip) shouldn't defeat command
        // recognition — but a non-command literal send keeps its exact original text,
        // whitespace included, rather than silently trimming what the player typed.
        val trimmed = input.trimStart()
        if (trimmed.startsWith("//")) return SlashResolution.Literal(trimmed.substring(1))
        if (!trimmed.startsWith("/")) return SlashResolution.Literal(input)

        val withoutSlash = trimmed.substring(1)
        val spaceIndex = withoutSlash.indexOf(' ')
        val name = if (spaceIndex == -1) withoutSlash else withoutSlash.substring(0, spaceIndex)
        val args = if (spaceIndex == -1) "" else withoutSlash.substring(spaceIndex + 1)

        resolveBuiltIn(name, args)?.let { return it }

        // Name lookup is always case-insensitive, regardless of the command's own
        // caseSensitive flag — that flag only governs matching against `args`.
        val command = commands.firstOrNull { it.enabled && it.name.equals(name, ignoreCase = true) }
            ?: return SlashResolution.Literal(input)

        val captures = PatternMatcher.captures(args, command.pattern, command.matchType, command.caseSensitive)
            ?: return SlashResolution.Literal(input)

        val lines = command.sendTemplate.lines().filter { it.isNotBlank() }
            .map { PatternMatcher.substitute(it, captures) }
        return if (lines.isEmpty()) SlashResolution.Literal(input) else SlashResolution.Expanded(lines)
    }

    /** Null means "not a built-in" — falls through to the user-defined lookup in [resolve]. */
    private fun resolveBuiltIn(name: String, args: String): SlashResolution? = when (name.lowercase()) {
        "cls" -> SlashResolution.Action(BuiltInAction.ClearScreen)
        "print" -> SlashResolution.SystemMessage(args)
        "time" -> SlashResolution.SystemMessage(formatTime(args))
        "web" -> args.trim().takeIf { it.isNotEmpty() }
            ?.let { SlashResolution.Action(BuiltInAction.OpenUrl(normalizeUrl(it))) }
        "speedwalk" -> expandSpeedwalk(args)?.let { SlashResolution.Expanded(it) }
        "reconnect" -> SlashResolution.Action(BuiltInAction.ReconnectNow)
        "disconnect" -> SlashResolution.Action(BuiltInAction.Disconnect)
        "connect" -> args.trim().takeIf { it.isNotEmpty() }
            ?.let { SlashResolution.Action(BuiltInAction.Connect(it)) }
        "debug" -> SlashResolution.Action(BuiltInAction.SetDebug(parseOnOff(args)))
        else -> null
    }

    private fun formatTime(args: String): String {
        val now = LocalDateTime.now()
        if (args.isBlank()) return now.format(DEFAULT_TIME_FORMAT)
        return runCatching { now.format(DateTimeFormatter.ofPattern(args)) }
            .getOrElse { "Invalid time format: $args" }
    }

    private fun normalizeUrl(url: String): String = if (url.contains("://")) url else "https://$url"

    private fun parseOnOff(args: String): Boolean? = when (args.trim().lowercase()) {
        "on" -> true
        "off" -> false
        else -> null
    }

    /** Null if any token doesn't parse as `[count]direction` — falls back to sending literally. */
    private fun expandSpeedwalk(args: String): List<String>? {
        val tokens = args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val lines = mutableListOf<String>()
        for (token in tokens) {
            val match = SPEEDWALK_TOKEN.matchEntire(token) ?: return null
            val (countText, dirText) = match.destructured
            val direction = SPEEDWALK_DIRECTIONS[dirText.lowercase()] ?: return null
            val count = if (countText.isEmpty()) 1 else countText.toIntOrNull() ?: return null
            if (count <= 0) return null
            repeat(count) { lines += direction }
        }
        return lines
    }
}

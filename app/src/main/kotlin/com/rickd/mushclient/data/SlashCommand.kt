package com.rickd.mushclient.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined command invoked as `/name args...` from the input bar. The text after the
 * name is matched against [pattern]/[matchType]/[caseSensitive] (the name itself always
 * matches case-insensitively); on a match, [sendTemplate] is sent instead — %0-%9 substituted
 * with captures — while the original typed text is what's echoed into the scrollback/history,
 * so recalling it from history re-expands rather than replaying a stale expansion. Mirrors
 * Potato's per-world custom slash commands (e.g. Rick's own `/grab` -> `@decompile/tf %0`).
 * [worldId] null means Global, matched on every world in addition to that world's own commands.
 */
@Entity(
    tableName = "slash_commands",
    foreignKeys = [
        ForeignKey(
            entity = World::class,
            parentColumns = ["id"],
            childColumns = ["worldId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("worldId")],
)
data class SlashCommand(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val worldId: Long?,
    val name: String,
    val pattern: String,
    val matchType: PatternMatchType,
    val caseSensitive: Boolean = false,
    val enabled: Boolean = true,
    val sendTemplate: String,
)

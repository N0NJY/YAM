package com.rickd.mushclient.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pattern-matches incoming MU* lines and reacts: recolor, hide, auto-reply, or capture text
 * into the input box. [worldId] null means a Global trigger, matched against every world's
 * output (in addition to that world's own triggers) — mirrors Potato's World vs Global Events.
 */
@Entity(
    tableName = "triggers",
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
data class Trigger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val worldId: Long?,
    val name: String,
    val pattern: String,
    val matchType: PatternMatchType,
    val caseSensitive: Boolean = false,
    val enabled: Boolean = true,
    // If a match should still let later triggers in the list run against the same line.
    val continueMatching: Boolean = false,
    val omitFromDisplay: Boolean = false,
    // ARGB, as from androidx.compose.ui.graphics.Color.toArgb(); null = don't recolor.
    val recolorForeground: Int? = null,
    // Multi-line (one command per line); %0-%9 substituted with captures. Sent silently,
    // not echoed into the scrollback/history since the player didn't type it.
    val sendText: String? = null,
    // %0-%9 substituted with captures, placed into the input box for the player to review
    // and send themselves rather than firing automatically.
    val inputText: String? = null,
)

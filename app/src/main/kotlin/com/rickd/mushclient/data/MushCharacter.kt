package com.rickd.mushclient.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A login identity on a [World] — name matches this app's own vocabulary, "Character" would
 * collide in spirit (if not name) with java.lang.Character, hence the Mush- prefix.
 */
@Entity(
    tableName = "characters",
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
data class MushCharacter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val worldId: Long,
    val name: String,
    val password: String,
)

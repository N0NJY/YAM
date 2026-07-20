package com.rickd.mushclient.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A flat (non-nested) folder for organizing saved [World]s, mirroring Potato's Address Book
 * groups. Deleting a group never deletes the worlds inside it — see [WorldDao.clearGroup].
 */
@Entity(tableName = "world_groups")
data class WorldGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

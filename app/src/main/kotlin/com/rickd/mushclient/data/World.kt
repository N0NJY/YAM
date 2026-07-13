package com.rickd.mushclient.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved MUSH/MUD server to connect to. Table name predates the "world" terminology. */
@Entity(tableName = "connection_profiles")
data class World(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
)

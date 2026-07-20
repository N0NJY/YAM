package com.rickd.mushclient.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SlashCommandDao {
    @Query("SELECT * FROM slash_commands WHERE worldId = :worldId ORDER BY id")
    fun observeForWorld(worldId: Long): Flow<List<SlashCommand>>

    @Query("SELECT * FROM slash_commands WHERE worldId IS NULL ORDER BY id")
    fun observeGlobal(): Flow<List<SlashCommand>>

    // Every saved slash command across every world plus Global ones, for the flat management list.
    @Query(
        """
        SELECT slash_commands.*, connection_profiles.name AS worldName
        FROM slash_commands
        LEFT JOIN connection_profiles ON connection_profiles.id = slash_commands.worldId
        ORDER BY worldName COLLATE NOCASE, slash_commands.name COLLATE NOCASE
        """,
    )
    fun observeAllWithWorldName(): Flow<List<SlashCommandWithWorldName>>

    @Insert
    suspend fun insert(command: SlashCommand): Long

    @Update
    suspend fun update(command: SlashCommand)

    @Delete
    suspend fun delete(command: SlashCommand)
}

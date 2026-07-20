package com.rickd.mushclient.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TriggerDao {
    @Query("SELECT * FROM triggers WHERE worldId = :worldId ORDER BY id")
    fun observeForWorld(worldId: Long): Flow<List<Trigger>>

    @Query("SELECT * FROM triggers WHERE worldId IS NULL ORDER BY id")
    fun observeGlobal(): Flow<List<Trigger>>

    // Every saved trigger across every world plus Global ones, for the flat "Triggers" list.
    @Query(
        """
        SELECT triggers.*, connection_profiles.name AS worldName
        FROM triggers
        LEFT JOIN connection_profiles ON connection_profiles.id = triggers.worldId
        ORDER BY worldName COLLATE NOCASE, triggers.name COLLATE NOCASE
        """,
    )
    fun observeAllWithWorldName(): Flow<List<TriggerWithWorldName>>

    @Insert
    suspend fun insert(trigger: Trigger): Long

    @Update
    suspend fun update(trigger: Trigger)

    @Delete
    suspend fun delete(trigger: Trigger)
}

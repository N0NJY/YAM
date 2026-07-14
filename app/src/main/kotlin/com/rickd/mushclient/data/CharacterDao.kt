package com.rickd.mushclient.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters WHERE worldId = :worldId ORDER BY name COLLATE NOCASE")
    fun observeForWorld(worldId: Long): Flow<List<MushCharacter>>

    // Every saved login identity across every world, for the flat cross-world "Players" list.
    @Query(
        """
        SELECT characters.*, connection_profiles.name AS worldName,
               connection_profiles.host AS worldHost, connection_profiles.port AS worldPort
        FROM characters
        INNER JOIN connection_profiles ON connection_profiles.id = characters.worldId
        ORDER BY connection_profiles.name COLLATE NOCASE, characters.name COLLATE NOCASE
        """,
    )
    fun observeAllWithWorld(): Flow<List<CharacterWithWorld>>

    @Insert
    suspend fun insert(character: MushCharacter): Long

    @Update
    suspend fun update(character: MushCharacter)

    @Delete
    suspend fun delete(character: MushCharacter)
}

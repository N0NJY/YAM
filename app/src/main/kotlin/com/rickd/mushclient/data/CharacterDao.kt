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

    @Insert
    suspend fun insert(character: MushCharacter): Long

    @Update
    suspend fun update(character: MushCharacter)

    @Delete
    suspend fun delete(character: MushCharacter)
}

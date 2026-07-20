package com.rickd.mushclient.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldGroupDao {
    @Query("SELECT * FROM world_groups ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<WorldGroup>>

    @Insert
    suspend fun insert(group: WorldGroup): Long

    @Delete
    suspend fun delete(group: WorldGroup)
}

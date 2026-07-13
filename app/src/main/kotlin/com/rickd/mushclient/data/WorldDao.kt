package com.rickd.mushclient.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldDao {
    @Query("SELECT * FROM connection_profiles ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<World>>

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    suspend fun getById(id: Long): World?

    @Insert
    suspend fun insert(world: World): Long

    @Update
    suspend fun update(world: World)

    @Delete
    suspend fun delete(world: World)
}

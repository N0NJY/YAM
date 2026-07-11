package com.rickd.mushclient.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM connection_profiles ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ConnectionProfile>>

    @Insert
    suspend fun insert(profile: ConnectionProfile): Long

    @Update
    suspend fun update(profile: ConnectionProfile)

    @Delete
    suspend fun delete(profile: ConnectionProfile)
}

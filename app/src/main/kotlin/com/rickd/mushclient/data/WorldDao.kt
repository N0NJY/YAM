package com.rickd.mushclient.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldDao {
    // Excludes soft-deleted worlds — used by every world-picker dropdown (Triggers, Slash
    // Commands, Characters) and by MudConnectionService's `/connect <name>` lookup, all of
    // which should stop seeing a world once it's been moved to Temp/Deleted.
    @Query("SELECT * FROM connection_profiles WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<World>>

    @Query("SELECT * FROM connection_profiles WHERE id = :id")
    suspend fun getById(id: Long): World?

    @Insert
    suspend fun insert(world: World): Long

    @Update
    suspend fun update(world: World)

    /** Permanent removal — only ever called from the Temp/Deleted view. */
    @Delete
    suspend fun delete(world: World)

    // Three distinct queries (rather than one nullable groupId param) so "no filter" and
    // "explicitly ungrouped" can't get conflated.
    @Query(
        """
        SELECT connection_profiles.*, characters.name AS lastCharacterName
        FROM connection_profiles
        LEFT JOIN characters ON characters.id = connection_profiles.lastCharacterId
        WHERE connection_profiles.isDeleted = 0
        ORDER BY connection_profiles.name COLLATE NOCASE
        """,
    )
    fun observeAllWithLastCharacter(): Flow<List<WorldWithLastCharacter>>

    @Query(
        """
        SELECT connection_profiles.*, characters.name AS lastCharacterName
        FROM connection_profiles
        LEFT JOIN characters ON characters.id = connection_profiles.lastCharacterId
        WHERE connection_profiles.isDeleted = 0 AND connection_profiles.groupId IS NULL
        ORDER BY connection_profiles.name COLLATE NOCASE
        """,
    )
    fun observeUngroupedWithLastCharacter(): Flow<List<WorldWithLastCharacter>>

    @Query(
        """
        SELECT connection_profiles.*, characters.name AS lastCharacterName
        FROM connection_profiles
        LEFT JOIN characters ON characters.id = connection_profiles.lastCharacterId
        WHERE connection_profiles.isDeleted = 0 AND connection_profiles.groupId = :groupId
        ORDER BY connection_profiles.name COLLATE NOCASE
        """,
    )
    fun observeByGroupWithLastCharacter(groupId: Long): Flow<List<WorldWithLastCharacter>>

    @Query(
        """
        SELECT connection_profiles.*, characters.name AS lastCharacterName
        FROM connection_profiles
        LEFT JOIN characters ON characters.id = connection_profiles.lastCharacterId
        WHERE connection_profiles.isDeleted = 1
        ORDER BY connection_profiles.name COLLATE NOCASE
        """,
    )
    fun observeDeletedWithLastCharacter(): Flow<List<WorldWithLastCharacter>>

    @Query("UPDATE connection_profiles SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("UPDATE connection_profiles SET isDeleted = 0 WHERE id = :id")
    suspend fun restore(id: Long)

    /** Un-groups every world in a deleted group — a group's worlds always survive it. */
    @Query("UPDATE connection_profiles SET groupId = NULL WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: Long)

    @Query("UPDATE connection_profiles SET lastCharacterId = NULL WHERE lastCharacterId = :characterId")
    suspend fun clearLastCharacterId(characterId: Long)

    @Query("UPDATE connection_profiles SET lastCharacterId = :characterId WHERE id = :worldId")
    suspend fun setLastCharacterId(worldId: Long, characterId: Long?)
}

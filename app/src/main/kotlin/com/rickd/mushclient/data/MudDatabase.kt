package com.rickd.mushclient.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS characters (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                worldId INTEGER NOT NULL,
                name TEXT NOT NULL,
                password TEXT NOT NULL,
                FOREIGN KEY(worldId) REFERENCES connection_profiles(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_characters_worldId ON characters(worldId)")
    }
}

@Database(entities = [World::class, MushCharacter::class], version = 2, exportSchema = false)
abstract class MudDatabase : RoomDatabase() {
    abstract fun worldDao(): WorldDao
    abstract fun characterDao(): CharacterDao

    companion object {
        @Volatile private var instance: MudDatabase? = null

        fun get(context: Context): MudDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MudDatabase::class.java,
                    "mud_client.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}

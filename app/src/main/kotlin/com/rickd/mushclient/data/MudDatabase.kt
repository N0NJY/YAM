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

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS triggers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                worldId INTEGER,
                name TEXT NOT NULL,
                pattern TEXT NOT NULL,
                matchType TEXT NOT NULL,
                caseSensitive INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                continueMatching INTEGER NOT NULL,
                omitFromDisplay INTEGER NOT NULL,
                recolorForeground INTEGER,
                sendText TEXT,
                inputText TEXT,
                FOREIGN KEY(worldId) REFERENCES connection_profiles(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_triggers_worldId ON triggers(worldId)")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS slash_commands (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                worldId INTEGER,
                name TEXT NOT NULL,
                pattern TEXT NOT NULL,
                matchType TEXT NOT NULL,
                caseSensitive INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                sendTemplate TEXT NOT NULL,
                FOREIGN KEY(worldId) REFERENCES connection_profiles(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_slash_commands_worldId ON slash_commands(worldId)")
    }
}

@Database(
    entities = [World::class, MushCharacter::class, Trigger::class, SlashCommand::class],
    version = 4,
    exportSchema = false,
)
abstract class MudDatabase : RoomDatabase() {
    abstract fun worldDao(): WorldDao
    abstract fun characterDao(): CharacterDao
    abstract fun triggerDao(): TriggerDao
    abstract fun slashCommandDao(): SlashCommandDao

    companion object {
        @Volatile private var instance: MudDatabase? = null

        fun get(context: Context): MudDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MudDatabase::class.java,
                    "mud_client.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}

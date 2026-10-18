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

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // groupId/lastCharacterId are plain nullable columns, not real FK constraints — see
        // the World.kt doc comment for why (SQLite can't ALTER TABLE ADD COLUMN a new FK onto
        // an already-populated table without recreating it, which isn't worth the risk here).
        db.execSQL("ALTER TABLE connection_profiles ADD COLUMN groupId INTEGER")
        db.execSQL("ALTER TABLE connection_profiles ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE connection_profiles ADD COLUMN lastCharacterId INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS world_groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_connection_profiles_groupId ON connection_profiles(groupId)")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE connection_profiles ADD COLUMN nopKeepalive INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE connection_profiles ADD COLUMN useSsl INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ssl_pins (
                hostPort TEXT NOT NULL,
                fingerprint TEXT NOT NULL,
                PRIMARY KEY(hostPort)
            )
            """.trimIndent(),
        )
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE connection_profiles ADD COLUMN host2 TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE connection_profiles ADD COLUMN port2 INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE connection_profiles ADD COLUMN useSsl2 INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE connection_profiles ADD COLUMN proxyHost TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE connection_profiles ADD COLUMN proxyPort INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [World::class, MushCharacter::class, Trigger::class, SlashCommand::class, WorldGroup::class, SslPin::class],
    version = 9,
    exportSchema = false,
)
abstract class MudDatabase : RoomDatabase() {
    abstract fun sslPinDao(): SslPinDao
    abstract fun worldDao(): WorldDao
    abstract fun characterDao(): CharacterDao
    abstract fun triggerDao(): TriggerDao
    abstract fun slashCommandDao(): SlashCommandDao
    abstract fun worldGroupDao(): WorldGroupDao

    companion object {
        @Volatile private var instance: MudDatabase? = null

        fun get(context: Context): MudDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MudDatabase::class.java,
                    "mud_client.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build().also { instance = it }
            }
    }
}

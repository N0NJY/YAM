package com.rickd.mushclient.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ConnectionProfile::class], version = 1, exportSchema = false)
abstract class MudDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile private var instance: MudDatabase? = null

        fun get(context: Context): MudDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MudDatabase::class.java,
                    "mud_client.db",
                ).build().also { instance = it }
            }
    }
}

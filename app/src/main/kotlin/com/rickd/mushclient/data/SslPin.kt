package com.rickd.mushclient.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * The certificate fingerprint YAM saw the first time it connected to [hostPort] ("host:port")
 * over SSL — trust on first use, like SSH's known_hosts. Kept apart from Android's own
 * certificate store, which this never consults or changes.
 */
@Entity(tableName = "ssl_pins")
data class SslPin(
    @PrimaryKey val hostPort: String,
    val fingerprint: String,
)

@Dao
interface SslPinDao {
    @Query("SELECT fingerprint FROM ssl_pins WHERE hostPort = :hostPort")
    suspend fun get(hostPort: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(pin: SslPin)

    /** Returns how many rows were removed (0 = nothing was saved for that host:port). */
    @Query("DELETE FROM ssl_pins WHERE hostPort = :hostPort")
    suspend fun delete(hostPort: String): Int
}

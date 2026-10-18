package com.rickd.mushclient.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved MUSH/MUD server to connect to. Table name predates the "world" terminology.
 *
 * [groupId] and [lastCharacterId] are deliberately plain nullable columns, not Room
 * `@ForeignKey`s — both were added via `ALTER TABLE ADD COLUMN` to an already-populated table
 * (MIGRATION_4_5), and SQLite can't attach a new FK constraint that way without recreating the
 * table. Referential cleanup (nulling these out when the thing they point to is deleted) is
 * handled in application code instead — see [WorldDao.clearGroup]/[WorldDao.clearLastCharacterId].
 */
@Entity(tableName = "connection_profiles", indices = [Index("groupId")])
data class World(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val groupId: Long? = null,
    val isDeleted: Boolean = false,
    val lastCharacterId: Long? = null,
    // Per-world opt-in: send a telnet IAC NOP heartbeat every 60s (see TelnetClient). TCP
    // keepalive is separate and always on.
    val nopKeepalive: Boolean = false,
    // Per-world opt-in: implicit TLS on this world's port, with the server's certificate pinned
    // on first use (see telnet/CertificatePinning.kt).
    val useSsl: Boolean = false,
    // Optional fallback address, tried only if the primary can't be reached. Used only when
    // both [host2] and [port2] are set; has its own SSL flag.
    val host2: String = "",
    val port2: Int = 0,
    val useSsl2: Boolean = false,
    // Optional SOCKS4/4a proxy every connection for this world is relayed through. Used only when
    // both [proxyHost] and [proxyPort] are set.
    val proxyHost: String = "",
    val proxyPort: Int = 0,
)

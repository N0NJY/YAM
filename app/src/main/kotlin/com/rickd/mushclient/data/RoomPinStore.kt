package com.rickd.mushclient.data

import com.rickd.mushclient.telnet.PinStore
import kotlinx.coroutines.runBlocking

/**
 * Backs [PinStore] with the Room table. The TLS handshake asks synchronously, from a connection
 * thread that is already off the main thread, so blocking on the DAO here is fine.
 */
class RoomPinStore(private val dao: SslPinDao) : PinStore {
    override fun get(hostPort: String): String? = runBlocking { dao.get(hostPort) }

    override fun put(hostPort: String, fingerprint: String) = runBlocking { dao.put(SslPin(hostPort, fingerprint)) }

    override fun remove(hostPort: String): Boolean = runBlocking { dao.delete(hostPort) > 0 }
}

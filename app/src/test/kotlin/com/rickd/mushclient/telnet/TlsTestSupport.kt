package com.rickd.mushclient.telnet

import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/** Test-only: certificates generated once with keytool (see app/src/test/resources). */
object TlsTestSupport {
    // SHA-256 fingerprints as printed by `keytool -list` for tls-a.p12 / tls-b.p12.
    const val FINGERPRINT_A = "F2:C2:20:5C:29:E6:8F:46:D0:22:BD:99:4A:F5:29:74:0E:FC:0F:2D:6D:A3:B2:47:03:D5:9E:CF:23:64:78:DE"
    const val FINGERPRINT_B = "2D:90:FF:71:50:19:83:80:A0:E1:F9:D6:87:F1:0D:C4:6B:E6:E1:72:2B:6A:CE:BF:F5:CD:21:2A:F7:E9:0D:AF"

    private fun keyStore(name: String): KeyStore =
        KeyStore.getInstance("PKCS12").apply {
            TlsTestSupport::class.java.classLoader!!.getResourceAsStream(name)!!.use { load(it, "changeit".toCharArray()) }
        }

    fun certificate(name: String): X509Certificate = keyStore(name).getCertificate("test") as X509Certificate

    fun serverContext(name: String): SSLContext {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore(name), "changeit".toCharArray())
        return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
    }
}

class InMemoryPinStore : PinStore {
    val pins = mutableMapOf<String, String>()
    var gets = 0
    override fun get(hostPort: String): String? { gets++; return pins[hostPort] }
    override fun put(hostPort: String, fingerprint: String) { pins[hostPort] = fingerprint }
    override fun remove(hostPort: String): Boolean = pins.remove(hostPort) != null
}

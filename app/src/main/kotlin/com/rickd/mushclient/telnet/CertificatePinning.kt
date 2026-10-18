package com.rickd.mushclient.telnet

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/** Where certificate fingerprints are remembered, keyed by `"host:port"`. */
interface PinStore {
    fun get(hostPort: String): String?
    fun put(hostPort: String, fingerprint: String)

    /** True if something was actually saved for [hostPort]. */
    fun remove(hostPort: String): Boolean
}

/** Thrown during the TLS handshake when a server presents a different certificate than before. */
class CertificateChangedException(
    val host: String,
    val port: Int,
    val savedFingerprint: String,
    val presentedFingerprint: String,
) : CertificateException(
    "The SSL certificate for $host:$port has changed.",
)

object CertificatePinning {
    fun key(host: String, port: Int) = "$host:$port"

    /** SHA-256 of the DER-encoded certificate, as upper-case colon-separated hex. */
    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString(":") { "%02X".format(it.toInt() and 0xFF) }

    /** The shorter warning shown when a fallback address was used after [e] refused the primary. */
    fun fallbackWarning(e: CertificateChangedException): String {
        val where = key(e.host, e.port)
        return "[SSL] Warning: the SSL certificate for $where has CHANGED and was refused " +
            "(saved ${e.savedFingerprint}, now ${e.presentedFingerprint}). " +
            "If you expected this, type /ssl-forget $where."
    }

    /** What to show the player when [e] stopped a connection. */
    fun mismatchMessage(e: CertificateChangedException): String {
        val where = key(e.host, e.port)
        return "The SSL certificate for $where has CHANGED, so the connection was refused.\n" +
            "Saved:   ${e.savedFingerprint}\n" +
            "Now:     ${e.presentedFingerprint}\n" +
            "If you expected this (the server's certificate was replaced), type " +
            "/ssl-forget $where and reconnect. Auto-reconnect is paused."
    }
}

/**
 * Trust-on-first-use certificate pinning. MU* servers overwhelmingly use self-signed
 * certificates, so there is no CA chain to validate; instead the first certificate seen for a
 * host:port is remembered, and any different one later is refused — during the handshake, before
 * a single byte of the session is sent. Android's own trust store is deliberately not consulted.
 * Hostname checking is skipped for the same reason (a self-signed certificate rarely names its
 * host), which is exactly why the pin matters.
 */
class PinningTrustManager(
    private val host: String,
    private val port: Int,
    private val store: PinStore,
    private val onFirstUse: (fingerprint: String) -> Unit = {},
) : X509TrustManager {

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("The server sent no certificate.")
        val presented = CertificatePinning.fingerprint(leaf)
        val key = CertificatePinning.key(host, port)
        val saved = store.get(key)
        when {
            saved == null -> {
                store.put(key, presented)
                onFirstUse(presented)
            }
            saved == presented -> Unit
            else -> throw CertificateChangedException(host, port, saved, presented)
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("YAM does not accept client certificates.")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

package com.rickd.mushclient.telnet

import java.security.cert.CertificateException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CertificatePinningTest {

    private val certA = TlsTestSupport.certificate("tls-a.p12")
    private val certB = TlsTestSupport.certificate("tls-b.p12")

    @Test fun fingerprintMatchesWhatKeytoolReportsForTheSameCertificate() {
        assertEquals(TlsTestSupport.FINGERPRINT_A, CertificatePinning.fingerprint(certA))
        assertEquals(TlsTestSupport.FINGERPRINT_B, CertificatePinning.fingerprint(certB))
    }

    @Test fun fingerprintIsUpperCaseColonSeparatedSha256() {
        assertTrue(CertificatePinning.fingerprint(certA).matches(Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}")))
    }

    @Test fun keyIsHostColonPort() = assertEquals("mush.example:4443", CertificatePinning.key("mush.example", 4443))

    @Test fun firstUseSavesThePinAndReportsIt() {
        val store = InMemoryPinStore()
        var reported: String? = null
        PinningTrustManager("h", 1, store) { reported = it }.checkServerTrusted(arrayOf(certA), "RSA")
        assertEquals(TlsTestSupport.FINGERPRINT_A, store.pins["h:1"])
        assertEquals(TlsTestSupport.FINGERPRINT_A, reported)
    }

    @Test fun theSameCertificateAgainIsAcceptedSilently() {
        val store = InMemoryPinStore().apply { pins["h:1"] = TlsTestSupport.FINGERPRINT_A }
        var reports = 0
        PinningTrustManager("h", 1, store) { reports++ }.checkServerTrusted(arrayOf(certA), "RSA")
        assertEquals(0, reports)
    }

    @Test fun aDifferentCertificateIsRefusedWithBothFingerprintsAndTheStoreIsUntouched() {
        val store = InMemoryPinStore().apply { pins["h:1"] = TlsTestSupport.FINGERPRINT_A }
        try {
            PinningTrustManager("h", 1, store).checkServerTrusted(arrayOf(certB), "RSA")
            fail("a changed certificate must be refused")
        } catch (e: CertificateChangedException) {
            assertEquals(TlsTestSupport.FINGERPRINT_A, e.savedFingerprint)
            assertEquals(TlsTestSupport.FINGERPRINT_B, e.presentedFingerprint)
            assertEquals("h", e.host)
            assertEquals(1, e.port)
        }
        assertEquals(TlsTestSupport.FINGERPRINT_A, store.pins["h:1"])
    }

    @Test fun pinsAreKeptPerHostAndPort() {
        val store = InMemoryPinStore().apply { pins["h:1"] = TlsTestSupport.FINGERPRINT_A }
        // Same host, different port: nothing saved there yet, so a different certificate is fine.
        PinningTrustManager("h", 2, store).checkServerTrusted(arrayOf(certB), "RSA")
        assertEquals(TlsTestSupport.FINGERPRINT_B, store.pins["h:2"])
        assertEquals(TlsTestSupport.FINGERPRINT_A, store.pins["h:1"])
    }

    @Test fun onlyTheLeafCertificateIsPinned() {
        val store = InMemoryPinStore()
        PinningTrustManager("h", 1, store).checkServerTrusted(arrayOf(certA, certB), "RSA")
        assertEquals(TlsTestSupport.FINGERPRINT_A, store.pins["h:1"])
    }

    @Test fun anEmptyChainIsRefused() {
        try {
            PinningTrustManager("h", 1, InMemoryPinStore()).checkServerTrusted(emptyArray(), "RSA")
            fail("no certificate must be refused")
        } catch (_: CertificateException) {
        }
    }

    @Test fun clientCertificatesAreNotAccepted() {
        try {
            PinningTrustManager("h", 1, InMemoryPinStore()).checkClientTrusted(arrayOf(certA), "RSA")
            fail()
        } catch (_: CertificateException) {
        }
    }

    @Test fun theMismatchMessageNamesTheCommandToRunAndBothFingerprints() {
        val msg = CertificatePinning.mismatchMessage(
            CertificateChangedException("mush.example", 4443, "AA:AA", "BB:BB"),
        )
        assertTrue(msg.contains("/ssl-forget mush.example:4443"))
        assertTrue(msg.contains("AA:AA") && msg.contains("BB:BB"))
        assertTrue(msg.contains("CHANGED"))
    }

    @Test fun theTwoTestCertificatesReallyDiffer() =
        assertNotEquals(TlsTestSupport.FINGERPRINT_A, TlsTestSupport.FINGERPRINT_B)

    @Test fun removeReportsWhetherAnythingWasSaved() {
        val store = InMemoryPinStore().apply { pins["h:1"] = "x" }
        assertTrue(store.remove("h:1"))
        assertFalse(store.remove("h:1"))
    }
}

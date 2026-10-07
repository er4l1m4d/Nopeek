package com.nopeek.app.data.attest

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * S1 spike (plan Task 2): does this Redmi actually produce a hardware-backed
 * attestation chain? Nothing in the compliance-proof design may proceed on the
 * assumption that it does — some Xiaomi SKUs ship without provisioned
 * attestation keys.
 *
 * Scope note, deliberate: this test asserts only what can be decided reliably
 * on-device without a hand-rolled ASN.1 parser. `keyMintSecurityLevel`,
 * `rootOfTrust.deviceLocked` and `verifiedBootState` live inside the attestation
 * extension's DER and are decoded authoritatively in Task 3, which is where the
 * parser belongs. This test's job is to prove a chain exists, is rooted where it
 * claims, carries our challenge and package identity, holds a non-exportable
 * P-256 key — and to leave the raw bytes behind for Task 3 to read.
 */
@RunWith(AndroidJUnit4::class)
class KeyStoreAttestationTest {

    private val outDir: File
        get() = InstrumentationRegistry.getInstrumentation().targetContext.filesDir

    @Test
    fun attestsHardwareBackedEcKey() {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        ks.deleteEntry(ALIAS)

        val spec = KeyGenParameterSpec.Builder(ALIAS, PURPOSE)
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(BOOTSTRAP_CHALLENGE)
            .build()

        val pair = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
            .apply { initialize(spec) }
            .generateKeyPair()

        val chain = ks.getCertificateChain(ALIAS)
        assertNotNull("no certificate chain returned — attestation unsupported on this device", chain)
        assertTrue(
            "chain shorter than $MIN_CHAIN — a self-signed leaf means no certified attestation key",
            chain!!.size >= MIN_CHAIN
        )

        val leaf = chain[0] as X509Certificate
        // Compare the EC point, not the object: the generated key is an
        // AndroidKeyStore handle wrapper while the certificate's is a parsed
        // ECPublicKey, so equals() on the keys themselves can never agree.
        assertEquals(
            "the attested key in the chain is not the key this test generated",
            (pair.public as ECPublicKey).w,
            (leaf.publicKey as ECPublicKey).w,
        )
        val extDer = leaf.getExtensionValue(ATTESTATION_OID)
        assertNotNull(
            "attestation extension $ATTESTATION_OID absent — this key was not attested",
            extDer
        )

        // The extension embeds the developer challenge and, software-enforced, the
        // installer-declared package name. Asserted as an exact-length DER OCTET
        // STRING, not a substring: a substring match is satisfied by
        // "com.nopeek.app.test", which would let a self-instrumenting test APK
        // masquerade as the real app. Task 3 decodes the surrounding structures.
        assertContainsBytes(extDer, BOOTSTRAP_CHALLENGE, "attestation challenge")
        assertExactOctetString(extDer, PACKAGE, "package name")
        // The subject DN lives in the certificate itself, not in the attestation
        // extension, so it is read through the X500 API rather than byte-scanned.
        assertTrue(
            "leaf subject is not the Android Keystore attestation identity: " +
                leaf.subjectX500Principal.name,
            leaf.subjectX500Principal.name.contains("Android Keystore Key"),
        )

        val pub = pair.public as ECPublicKey
        assertEquals(
            "attested key is not P-256",
            256,
            pub.params.order.bitLength()
        )

        // Non-exportable is the whole premise: if the private bytes ever come out,
        // a report could be signed anywhere and the attestation is theatre.
        val priv = ks.getKey(ALIAS, null) as PrivateKey
        try {
            val encoded = priv.encoded
            if (encoded != null) {
                fail("private key material was extractable (${encoded.size} bytes)")
            }
        } catch (expected: UnsupportedOperationException) {
            // Expected: AndroidKeyStore hands back a handle, never the key.
        }

        writeFixtures(chain, extDer, leaf)
    }

    /**
     * StrongBox is a discrete secure element; many Redmi SKUs do not carry one.
     * Recorded rather than asserted — its absence does not invalidate a TEE chain.
     */
    @Test
    fun probesStrongBoxAvailability() {
        val result = try {
            val spec = KeyGenParameterSpec.Builder("$ALIAS-sb", PURPOSE)
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setIsStrongBoxBacked(true)
                .setAttestationChallenge(BOOTSTRAP_CHALLENGE)
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
                .apply { initialize(spec) }
                .generateKeyPair()
            "STRONGBOX"
        } catch (e: ProviderException) {
            "TEE-only: ${e.message}"
        }
        File(outDir, "strongbox.txt").writeText(result)
    }

    private fun writeFixtures(chain: Array<java.security.cert.Certificate>, extDer: ByteArray, leaf: X509Certificate) {
        val pem = chain.joinToString("\n") { cert ->
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(cert.encoded)
                .let { "-----BEGIN CERTIFICATE-----\n$it\n-----END CERTIFICATE-----" }
        }
        File(outDir, "device-chain.pem").writeText(pem + "\n")
        File(outDir, "device-attest-ext.der").writeBytes(extDer)
        File(outDir, "device-spki.b64").writeText(
            Base64.getEncoder().encodeToString(leaf.publicKey.encoded) + "\n"
        )
        val digest = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
        File(outDir, "device-cert-digest.b64").writeText(
            Base64.getEncoder().encodeToString(digest) + "\n"
        )

        // Identity of every issuer in the chain, so the root can be pinned out of
        // band against Google's published attestation CA rather than trusted
        // because the chain vouched for itself (ledger Row 9).
        val identities = chain.mapIndexed { i, c ->
            val x = c as X509Certificate
            "$i subject=${x.subjectX500Principal.name} issuer=${x.issuerX500Principal.name} serial=${x.serialNumber}"
        }
        File(outDir, "chain-dns.txt").writeText(identities.joinToString("\n") + "\n")
    }

    private fun assertContainsBytes(haystack: ByteArray, needle: ByteArray, label: String) {
        val at = indexOf(haystack, needle)
        assertTrue("$label not found inside the attestation extension", at >= 0)
    }

    /**
     * Asserts `value` appears as a DER OCTET STRING holding exactly those bytes:
     * tag 0x04, a short-form length equal to the value's length, then the value.
     * A longer string that merely starts with `value` cannot satisfy this, because
     * its length byte differs.
     */
    private fun assertExactOctetString(haystack: ByteArray, value: String, label: String) {
        val raw = value.toByteArray(Charsets.US_ASCII)
        assertTrue("value too long for short-form length", raw.size < 128)
        val needle = byteArrayOf(0x04, raw.size.toByte()) + raw
        assertTrue(
            "$label not present as an exact-length DER OCTET STRING — the attesting " +
                "package may not be the application under test",
            indexOf(haystack, needle) >= 0,
        )
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > hay.size) return -1
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) {
                if (hay[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "nopeek-s1-probe"
        const val CURVE = "secp256r1"
        const val PURPOSE = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        const val ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17"
        const val MIN_CHAIN = 3
        const val PACKAGE = "com.nopeek.app"
        val BOOTSTRAP_CHALLENGE: ByteArray = "nopeek-s1-probe".toByteArray(Charsets.US_ASCII)
    }
}

package com.nopeek.app.data.attest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * Task 7 Steps 4-6 + the Task 13 protocol fix, measured on real hardware.
 *
 * The contract asserted here changed on purpose. This suite used to prove the opposite —
 * `enrollsOnceAndReusesTheSameAlias` pinned one persistent enrolled key — and that pinned a
 * protocol that cannot work: the oracle's check 6 requires the attestation's
 * `attestationChallenge` to equal the per-window nonce, but that field is frozen into the
 * certificate by `setAttestationChallenge()` at key *creation*. A reused key carries the
 * enrollment challenge forever, so every honest report after the first would have been
 * rejected NONCE_MISMATCH -> UNVERIFIED, forfeiting an honest user's stake.
 *
 * So a key is now minted per window, with that window's nonce as its challenge. That makes
 * the strongest version of the pitch true — a Google-signed attestation minted for this
 * exact window — and leaves all fourteen oracle checks exactly as they were reviewed.
 *
 * What this file therefore measures is the assumption Task 13 would otherwise be built on
 * and unable to question: that this Redmi can mint an ATTESTED key more than once, and that
 * each chain carries its own challenge rather than a cached one. Task 2 minted a key once;
 * one success says nothing about the second. If repeated minting silently returned the first
 * chain, every window after the first would fail check 6 in production while the device
 * looked healthy — which is the same shape as the four format assumptions this project has
 * already had to retire by measurement instead of recall.
 *
 * Instrumentation only: a JVM test runs against the stubbed android.jar and an empty keystore.
 */
@RunWith(AndroidJUnit4::class)
class EnrollRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Two distinct 32-byte ASCII challenges. ASCII because the exact-length assertion below
     * encodes the needle as US_ASCII DER bytes, the same technique Task 2 used to tell
     * `com.nopeek.app` from `com.nopeek.app.test`.
     */
    private val challengeA = "nopeek-window-A-00000000000000000".toByteArray(Charsets.US_ASCII)
    private val challengeB = "nopeek-window-B-00000000000000000".toByteArray(Charsets.US_ASCII)

    private val b64 = Base64.getEncoder()

    private fun mint(challenge: ByteArray) =
        KeyStoreEnroller(context).mintForChallenge(b64.encodeToString(challenge))

    /**
     * The measurement Task 13 depends on. Three things have to be true at once for per-window
     * attestation to be a real design rather than an assumption: the second mint produces a
     * different key, it produces a different chain, and each chain carries its OWN challenge.
     */
    @Test
    fun everyWindowMintsItsOwnAttestedKeyCarryingThatWindowsChallenge() {
        assertTrue(
            "challenge must stay under 128 bytes for short-form DER length (was ${challengeA.size})",
            challengeA.size < 128,
        )

        val first = mint(challengeA)
        val second = mint(challengeB)

        assertNotEquals(
            "a second mint returned the same public key — this device caches attestation " +
                "keys, so per-window challenges cannot work and check 6 will fail for every " +
                "report after the first",
            first.spkiB64, second.spkiB64,
        )
        assertNotEquals("two mints returned byte-identical chains", first.chainPem, second.chainPem)

        // The challenge has to be inside the HARDWARE-SIGNED extension of the key that signed
        // the report — asserted as an exact-length DER OCTET STRING, not a substring, so a
        // chain carrying a longer value that merely starts with these bytes cannot pass.
        assertExactOctetString(attestationExtOf(first), challengeA, "first window's challenge")
        assertExactOctetString(attestationExtOf(second), challengeB, "second window's challenge")

        // And the older challenge must NOT be present in the newer chain: proof the challenge
        // is per-mint rather than something the keystore reused from the first key.
        assertTrue(
            "the second chain still carries the FIRST window's challenge — the attestation was " +
                "reused, so this window's nonce is not the one the hardware attested",
            indexOf(attestationExtOf(second), challengeA) < 0,
        )

        // Each chain must still be a real attestation, both times. A shorter chain on the
        // second mint would mean the device runs out of attestation material.
        for ((label, key) in listOf("first" to first, "second" to second)) {
            val count = Regex("BEGIN CERTIFICATE").findAll(key.chainPem).count()
            assertTrue("$label chain has only $count certificate(s) - not hardware-attested", count >= 3)
        }

        InstrumentationRegistry.getInstrumentation().run {
            android.util.Log.i(
                "NOPEEK_REMINT",
                "ok secondChainCerts=" + Regex("BEGIN CERTIFICATE").findAll(second.chainPem).count() +
                    " alias=" + second.alias,
            )
        }
    }

    /**
     * The four values `/enroll` takes must satisfy the live table's CHECK constraints.
     * Checked at the source because a violation surfaces as an opaque Postgres 400 through
     * two layers of HTTP, far from the value that caused it.
     */
    @Test
    fun mintedKeySatisfiesTheInstallKeysConstraints() {
        val key = mint(challengeA)

        assertTrue(
            "spkiB64 length ${key.spkiB64.length} outside the DB CHECK (40..256)",
            key.spkiB64.length in 40..256,
        )
        assertEquals(
            "certDigestB64 must be base64 of a SHA-256 (44 chars) or the DB rejects it",
            44, key.certDigestB64.length,
        )
        assertTrue(key.chainPem.contains("BEGIN CERTIFICATE"))

        // The digest must actually be the leaf's, not merely a 44-char string: the oracle
        // compares it against the leaf of the chain sent with the report.
        val leafDer = Base64.getMimeDecoder().decode(
            key.chainPem.substringAfter("-----BEGIN CERTIFICATE-----")
                .substringBefore("-----END CERTIFICATE-----").trim(),
        )
        val recomputed = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(leafDer),
        )
        assertEquals("certDigestB64 is not sha256 of the leaf DER", recomputed, key.certDigestB64)

        val ks = KeyStore.getInstance(KeyStoreEnroller.KEYSTORE).apply { load(null) }
        assertTrue("no key entry at ${key.alias} - mint did not persist", ks.isKeyEntry(key.alias))
        assertEquals(
            "the key that will sign must be the key the chain attests",
            key.spkiB64,
            b64.encodeToString(ks.getCertificate(key.alias)!!.publicKey.encoded),
        )
    }

    /**
     * The minted key must sign the bytes a report will carry, and say what SHAPE it signs in.
     *
     * Measured, not assumed: the oracle's verifyEcdsaSignature documents "raw P-1363, exactly
     * what the device produces with SHA256withECDSA". On this device that claim was FALSE —
     * AndroidKeyStore emits DER (`len=72 firstByte=0x30`), which is why Task 13 converts
     * DER->raw device-side and the oracle stays strict. The log line below is the standing
     * guard: if a future device or provider changes the encoding, the oracle's single pinned
     * format silently starts rejecting genuine signatures.
     */
    @Test
    fun mintedKeySignsCanonicalBytesAndReportsTheSignatureShape() {
        val key = mint(challengeA)

        val ks = KeyStore.getInstance(KeyStoreEnroller.KEYSTORE).apply { load(null) }
        val entry = ks.getKey(key.alias, null)
        assertNotNull("keystore returned no private key for ${key.alias}", entry)
        val privateKey = entry as PrivateKey

        val payload = "nopeek evidence payload under test".toByteArray()
        // "SHA256withECDSA", NOT KeyProperties.DIGEST_SHA256 + "withECDSA": the latter
        // composes to "SHA-256withECDSA" and throws NoSuchAlgorithmException. The dashed name
        // belongs to setDigests(); the JCA Signature algorithm has its own spelling.
        val signature = Signature.getInstance("SHA256withECDSA")
            .apply {
                initSign(privateKey)
                update(payload)
            }
            .sign()

        val verified = Signature.getInstance("SHA256withECDSA")
            .apply {
                initVerify(ks.getCertificate(key.alias)!!.publicKey)
                update(payload)
            }
            .verify(signature)
        assertTrue("the key's own signature did not verify against its own public key", verified)

        val derShaped = signature.isNotEmpty() && signature[0].toInt() == 0x30
        InstrumentationRegistry.getInstrumentation().run {
            android.util.Log.i(
                "NOPEEK_SIGN_SHAPE",
                "len=${signature.size} firstByte=0x${(signature[0].toInt() and 0xff).toString(16)} " +
                    "der=$derShaped rawP1363=${signature.size == 64}",
            )
        }
        assertTrue(
            "signature is ${signature.size} bytes and does not start 0x30 - neither DER nor raw",
            derShaped || signature.size == 64,
        )
    }

    /**
     * AUTH-1 measurement: can the platform sign with a Solana wallet key at all?
     *
     * This device is API 35, so a pass here says nothing about whether we may SHIP it —
     * minSdk is 26, and a capability that only exists on newer Androids is a crash on the
     * older ones. The decision this feeds: an in-app ed25519 signer (needed for the /enroll
     * and /register-challenge signatures, and for Solana transactions anyway) comes from the
     * platform or from a dependency, and this project does not add unvetted crypto without
     * saying so out loud.
     */
    @Test
    fun ed25519IsAvailableFromThePlatformKeystoreProviders() {
        val pair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val message = "nopeek-enroll/v1".toByteArray()
        val signature = Signature.getInstance("Ed25519")
            .apply { initSign(pair.private); update(message) }
            .sign()
        val verified = Signature.getInstance("Ed25519")
            .apply { initVerify(pair.public); update(message) }
            .verify(signature)
        assertTrue("platform ed25519 sign/verify round trip failed", verified)
        assertEquals("an ed25519 signature is 64 bytes", 64, signature.size)
    }

    // --- helpers -------------------------------------------------------------

    /** The leaf certificate's KeyDescription extension, as raw DER bytes. */
    private fun attestationExtOf(key: EnrolledKey): ByteArray {
        val der = Base64.getMimeDecoder().decode(
            key.chainPem.substringAfter("-----BEGIN CERTIFICATE-----")
                .substringBefore("-----END CERTIFICATE-----").trim(),
        )
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        val ext = cert.getExtensionValue(ATTESTATION_OID)
        assertNotNull(
            "attestation extension $ATTESTATION_OID absent — this key was not attested",
            ext,
        )
        return ext
    }

    /**
     * Asserts `value` appears as a DER OCTET STRING holding exactly those bytes: tag 0x04, a
     * short-form length equal to the value's length, then the value. A longer value that
     * merely starts with these bytes cannot satisfy this, because its length byte differs —
     * the same reason Task 2 could not tell com.nopeek.app from com.nopeek.app.test.
     */
    private fun assertExactOctetString(haystack: ByteArray, value: ByteArray, label: String) {
        assertTrue("value too long for short-form length", value.size < 128)
        val needle = byteArrayOf(0x04, value.size.toByte()) + value
        assertTrue(
            "$label not present as an exact-length DER OCTET STRING inside the attestation " +
                "extension — the hardware did not attest this window's nonce",
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
        /** id-ce-extension of the Android Key Attestation ASN.1 structure. */
        const val ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17"
    }
}

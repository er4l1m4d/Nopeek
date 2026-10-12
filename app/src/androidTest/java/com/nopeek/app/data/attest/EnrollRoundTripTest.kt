package com.nopeek.app.data.attest

import android.content.Context
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.security.PrivateKey
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64

/**
 * Task 7 Steps 4-6: the production enrollment path on real hardware.
 *
 * Task 2 proved this Redmi *can* produce a hardware attestation chain, but it did that
 * inside a test with its own throwaway alias. KeyStoreEnroller is the class the app
 * actually uses, so these tests are about the three properties the oracle depends on:
 * the key is created once and reused, the values handed to /enroll satisfy the
 * install_keys CHECK constraints, and the private half can sign the exact bytes a report
 * will carry.
 *
 * Instrumentation only: a JVM test runs against the stubbed android.jar and would be
 * testing an empty keystore.
 */
@RunWith(AndroidJUnit4::class)
class EnrollRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Any 32-byte value works as a bootstrap challenge; the oracle re-checks it later. */
    private val bootstrapChallenge = "nopeek-enroll-round-trip".toByteArray()

    @Test
    fun enrollsOnceAndReusesTheSameAlias() {
        val enroller = KeyStoreEnroller(context)

        val first = enroller.enrollOrLoad(Base64.getEncoder().encodeToString(bootstrapChallenge))
        val second = enroller.enrollOrLoad(Base64.getEncoder().encodeToString(bootstrapChallenge))

        // Reuse, not regeneration. Every re-enrol would replace the enrolled key in the
        // oracle's install_keys row, and a device that minted a new attested key per app
        // start would leave earlier signed reports unverifiable against the LATEST key -
        // the honest user's own history would start failing check 7.
        assertEquals("alias must be stable across calls", first.alias, second.alias)
        assertEquals("SPKI must be the same key, not a fresh one", first.spkiB64, second.spkiB64)
        assertEquals("chain must be the same chain", first.chainPem, second.chainPem)

        val ks = KeyStore.getInstance(KeyStoreEnroller.KEYSTORE).apply { load(null) }
        assertTrue(
            "no key entry at ${first.alias} in AndroidKeyStore - enrollment did not persist",
            ks.isKeyEntry(first.alias),
        )

        // The exact bounds install_keys enforces (20261007_nopeek_full_schema.sql). If any
        // of these is wrong the oracle's INSERT is rejected by Postgres, which surfaces as
        // an opaque 400 far from the real cause - so it is checked here, at the source.
        assertTrue(
            "spkiB64 length ${first.spkiB64.length} outside the DB CHECK (40..256)",
            first.spkiB64.length in 40..256,
        )
        assertEquals(
            "certDigestB64 must be base64 of a SHA-256 (44 chars) or the DB rejects it",
            44, first.certDigestB64.length,
        )
        assertTrue(first.chainPem.contains("BEGIN CERTIFICATE"))

        // A chain shorter than Task 2's MIN_CHAIN means a self-signed leaf: no Google-certified
        // attestation key behind it, which is the whole basis of the compliance claim.
        val chainCount = Regex("BEGIN CERTIFICATE").findAll(first.chainPem).count()
        assertTrue(
            "chain has only $chainCount certificate(s) - not a hardware-attested chain",
            chainCount >= 3,
        )

        // The digest must actually be the leaf's, not just a 44-char string. Recomputed
        // independently from the PEM the class returned.
        val leafDer = Base64.getMimeDecoder().decode(
            first.chainPem.substringAfter("-----BEGIN CERTIFICATE-----")
                .substringBefore("-----END CERTIFICATE-----").trim(),
        )
        val recomputed = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(leafDer),
        )
        assertEquals("certDigestB64 is not sha256 of the leaf DER", recomputed, first.certDigestB64)
    }

    /**
     * The enrolled key must sign the bytes a report will carry. Task 13 is the class that
     * assembles and submits them, and its biggest unknown is the SIGNATURE SHAPE: the
     * oracle's verifyEcdsaSignature() documents "raw P-1363 r||s, exactly what the device
     * produces with SHA256withECDSA". If Java actually emits DER, that comment is wrong and
     * every real report fails BAD_SIGNATURE while all tests stay green.
     *
     * This does not assert a guess. It signs, verifies against the enrolled public key, and
     * logs the observed shape to logcat under NOPEEK_SIGN_SHAPE, where the gate script
     * reads it. The number that comes back decides whether the oracle needs a DER->raw
     * conversion on the report path.
     */
    @Test
    fun enrolledKeySignsCanonicalBytesAndReportsTheSignatureShape() {
        val enroller = KeyStoreEnroller(context)
        val enrolled = enroller.enrollOrLoad(Base64.getEncoder().encodeToString(bootstrapChallenge))

        val ks = KeyStore.getInstance(KeyStoreEnroller.KEYSTORE).apply { load(null) }
        val entry = ks.getKey(enrolled.alias, null)
        assertNotNull("keystore returned no private key for ${enrolled.alias}", entry)
        val privateKey = entry as PrivateKey

        val payload = "nopeek evidence payload under test".toByteArray()
        // "SHA256withECDSA", NOT KeyProperties.DIGEST_SHA256 + "withECDSA": the latter
        // composes to "SHA-256withECDSA", which the provider rejects. DIGEST_SHA256 is the
        // keystore's digest-selector name used in KeyGenParameterSpec.setDigests(); the JCA
        // Signature algorithm has its own spelling. Measured on this device, which threw
        // NoSuchAlgorithmException for the dashed form.
        val signature = Signature.getInstance("SHA256withECDSA")
            .apply {
                initSign(privateKey)
                update(payload)
            }
            .sign()

        val verified = Signature.getInstance("SHA256withECDSA")
            .apply {
                initVerify(ks.getCertificate(enrolled.alias)!!.publicKey)
                update(payload)
            }
            .verify(signature)
        assertTrue("the enrolled key's own signature did not verify against its own public key", verified)

        val derShaped = signature.isNotEmpty() && signature[0].toInt() == 0x30
        InstrumentationRegistry.getInstrumentation().run {
            android.util.Log.i(
                "NOPEEK_SIGN_SHAPE",
                "len=${signature.size} firstByte=0x${(signature[0].toInt() and 0xff).toString(16)} " +
                    "der=$derShaped rawP1363=${signature.size == 64}",
            )
        }
        // Deliberate: the useful assertion is that the shape is exactly one of the two, so a
        // truncated or empty signature cannot pass silently.
        assertTrue(
            "signature is ${signature.size} bytes and does not start 0x30 - neither DER nor raw",
            derShaped || signature.size == 64,
        )
    }

    /**
     * AUTH-1 measurement: can the platform sign with a Solana wallet key at all?
     *
     * This device is API 35, so a pass here says nothing about whether we may SHIP it -
     * minSdk is 26, and a capability that only exists on newer Androids is a crash on the
     * older ones we claim to support. The decision this feeds: an in-app ed25519 signer
     * (needed for /enroll and /register-challenge signatures, and for Solana transactions
     * anyway) comes from the platform or from a dependency, and this project does not add
     * unvetted crypto without saying so out loud.
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
}

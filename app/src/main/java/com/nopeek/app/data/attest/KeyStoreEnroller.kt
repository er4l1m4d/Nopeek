package com.nopeek.app.data.attest

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * The four values `/enroll` takes, already in the exact shapes the `install_keys` table
 * checks them against. Produced by [KeyStoreEnroller.enrollOrLoad] and passed straight to
 * the oracle — nothing here is re-derived later, because a value derived twice is a value
 * that can disagree with the bytes that were signed.
 */
data class EnrolledKey(
    val alias: String,
    /** DER SubjectPublicKeyInfo of the attested EC P-256 key, standard base64 (no line breaks). */
    val spkiB64: String,
    /** The full chain the device presented, PEM, leaf first. */
    val chainPem: String,
    /** Base64 of SHA-256 over the leaf certificate DER. Identifies this leaf, not the APK signer. */
    val certDigestB64: String,
)

/**
 * Creates, or reuses, the one hardware key this install of Nopeek signs compliance
 * reports with (plan Task 7).
 *
 * Why the key is reused rather than minted per call: the oracle binds a wallet to a single
 * enrolled SPKI (check 7 compares the presented chain's leaf against `install_keys`). A
 * device that generated a fresh attested key on each app start would silently invalidate
 * every report it had already signed — an honest user's own history starting to fail check 7
 * against the *latest* key. So an existing, genuinely attested entry is loaded and returned.
 *
 * Everything here runs against the real AndroidKeyStore, which is why it is exercised by
 * instrumentation: a JVM test sees a stubbed android.jar and an always-empty keystore.
 *
 * Attestation is not optional. A key that comes back without a chain long enough to reach a
 * Google-certified attestation CA throws: a self-signed leaf attests nothing, and enrolling
 * one would make every report from this device unverifiable while looking like success.
 */
class KeyStoreEnroller(@Suppress("UNUSED_PARAMETER") context: Context) {

    // The keystore is process-global and keyed by the app's package name, so no Context is
    // needed to reach it. The parameter stays because the caller supplies one and Task 13
    // may need it for preference storage.

    /**
     * Returns the enrolled key, creating it on first call.
     *
     * @param bootstrapChallengeB64 base64 of the bytes Google will embed in the attestation
     *   extension as `attestationChallenge`. On first enrollment this is a fresh value from
     *   the oracle's `/nonce`; it is what binds this key's chain to this install rather than
     *   a chain captured from another device.
     */
    fun enrollOrLoad(bootstrapChallengeB64: String): EnrolledKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

        keyStore.getCertificateChain(ALIAS)?.let { existing ->
            if (existing.size < MIN_CHAIN) {
                throw IllegalStateException(
                    "keystore entry $ALIAS has a ${existing.size}-certificate chain; a hardware-attested " +
                        "chain needs at least $MIN_CHAIN. Refusing to reuse it.",
                )
            }
            return toEnrolledKey(existing)
        }

        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(Base64.getDecoder().decode(bootstrapChallengeB64))
            // Deliberately NOT calling setUserAuthenticationRequired / setUserAuthenticationParameters,
            // even though this device is API 35 and both exist: the report is produced by a
            // background WorkManager run with no user present, and requiring authentication would
            // make it fail with a prompt it can never answer. This is a security choice made
            // out loud, not an oversight — the key proves the device, not the person.
            .build()

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
            .apply { initialize(spec) }
            .generateKeyPair()

        val chain = keyStore.getCertificateChain(ALIAS)
            ?: throw IllegalStateException(
                "keystore created $ALIAS but returned no certificate chain — this device does not " +
                    "provision hardware attestation keys, so the compliance claim cannot be made on it.",
            )
        if (chain.size < MIN_CHAIN) {
            throw IllegalStateException(
                "attestation chain has ${chain.size} certificate(s), fewer than $MIN_CHAIN: the leaf is " +
                    "not certified by a Google attestation root.",
            )
        }
        return toEnrolledKey(chain)
    }

    private fun toEnrolledKey(chain: Array<out Certificate>): EnrolledKey {
        val leaf = chain.first()
        val pem = chain.joinToString("\n") { certificate ->
            val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(certificate.encoded)
            "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(leaf.encoded)
        return EnrolledKey(
            alias = ALIAS,
            // publicKey.encoded is SubjectPublicKeyInfo DER — the same encoding the oracle
            // imports to check the leaf against the enrolled value.
            spkiB64 = Base64.getEncoder().encodeToString(leaf.publicKey.encoded),
            chainPem = "$pem\n",
            certDigestB64 = Base64.getEncoder().encodeToString(digest),
        )
    }

    companion object {
        const val KEYSTORE = "AndroidKeyStore"
        /** Stable across installs: one enrolled key per install, per the reuse argument above. */
        const val ALIAS = "NopeekAttest.v1"
        private const val CURVE = "secp256r1"
        /**
         * Task 2 measured 4 certificates on this Redmi (leaf, batch, intermediate, root).
         * Three is the floor that still proves a certified issuer above the leaf; anything
         * less is a self-signed leaf and attests nothing.
         */
        private const val MIN_CHAIN = 3
    }
}

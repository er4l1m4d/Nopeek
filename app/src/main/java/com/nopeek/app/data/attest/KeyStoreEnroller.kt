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
 * checks them against. Produced by [KeyStoreEnroller.mintForChallenge] and passed straight to
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
 * Mints the hardware key that signs ONE compliance window (spec 4.1, plan Task 7/13).
 *
 * One key per window, deliberately — not one key per install. This class previously created
 * a key once and reused it forever, and that design was impossible rather than merely
 * suboptimal: the oracle's check 6 requires the attestation's `attestationChallenge` to
 * equal the report's per-window nonce, but that field is frozen into the certificate by
 * `setAttestationChallenge()` at key creation and cannot be changed afterwards. A reused key
 * therefore carries the enrollment challenge for the rest of its life, so every honest
 * report after the first would be rejected NONCE_MISMATCH -> UNVERIFIED, which under the
 * fail-closed rule forfeits the honest user's stake. Reuse also made the parameter above
 * a lie: the load path accepted a challenge and silently discarded it.
 *
 * Minting per window is what makes the strongest version of the pitch actually true: the
 * oracle is shown a Google-signed attestation created for THIS window's nonce, not a
 * certificate pinned at install time and quoted ever after. All fourteen checks stay
 * exactly as reviewed, and the freshness of each window is enforced by hardware rather than
 * by the app's word.
 *
 * The cost, stated so it is not discovered later: each report re-mints the key and re-POSTs
 * `/enroll`, so `install_keys` holds the CURRENT key for a wallet. Already-stored reports are
 * unaffected — they were verified at submission time and settle from their own row and
 * evidence hash, not by re-checking today's enrolled key.
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
     * Creates a fresh non-exportable attested key whose certificate embeds
     * [challengeB64], replacing whatever this install minted last.
     *
     * @param challengeB64 base64 of the bytes Google embeds as `attestationChallenge` — the
     *   value `/nonce` issued for this window. It is what lets the oracle tell a chain minted
     *   for this window from one captured from an earlier one, so it is the reason the key is
     *   new. Decoded here rather than taken as bytes so a caller cannot pass an empty or
     *   non-base64 value and get a key attested against a challenge nothing issued.
     */
    fun mintForChallenge(challengeB64: String): EnrolledKey {
        val challenge = try {
            Base64.getDecoder().decode(challengeB64)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("attestation challenge is not base64", e)
        }
        require(challenge.isNotEmpty()) {
            "attestation challenge decoded to zero bytes; an empty challenge would make every " +
                "window's chain look interchangeable to the oracle"
        }

        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        // Replace rather than skip. Keeping the previous window's key would recreate exactly
        // the bug this method exists to fix: its certificate carries the OLD challenge, so
        // the new window's report fails check 6.
        if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)

        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(challenge)
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
        /**
         * Single alias, overwritten per window. The name is versioned because a change to the
         * key's parameters (curve, purpose, attestation) is a change to what the oracle can
         * verify, and an old entry must not survive into a new contract.
         */
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

package com.volo.app.data.identity

/**
 * Plan 10.1 Step 2: mock provider — no Seeker hardware, so verification is
 * always Unverified; .skr resolution uses fixed demo addresses.
 *
 * Plan-text deviation: the printed demo addresses were not valid Solana pubkeys
 * (one contains 'l', outside base58; the other decodes to 35 bytes). They are
 * kept to their plan prefixes and completed to valid 32-byte keys — the unit
 * test asserts the decode size.
 */
object MockSeekerProvider : SeekerIdentityProvider {

    private val domains = mapOf(
        "alice.skr" to "6o9DpPwoNMyfJx2Bw6rFByrZ2Z3r5qF7hJ8eD3aK9nB",
        "bob.skr" to "9fG8jK2hL4mN7pQ1rT6sW3xY8bV5cD2eF9gH7jK4ABC",
    )

    override fun verifyGenesisToken(wallet: String): IdentityVerificationResult =
        IdentityVerificationResult.Unverified

    override fun resolveDomain(skrDomain: String): String? =
        domains[skrDomain.trim().lowercase()]

    /** Reverse lookup for UI badges ("Opponent: alice.skr [Unverified]"). */
    fun handleFor(address: String): String? =
        domains.entries.firstOrNull { it.value == address }?.key
}

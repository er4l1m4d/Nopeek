package com.volo.app.data.identity

/**
 * Plan 10.1 Step 1: Seeker identity surface.
 * Real hardware verification (Seed Vault / Genesis Token) is deferred; see
 * docs/seeker-future-integration.md.
 */
interface SeekerIdentityProvider {
    fun verifyGenesisToken(wallet: String): IdentityVerificationResult
    fun resolveDomain(skrDomain: String): String?
}

sealed interface IdentityVerificationResult {
    data class VerifiedSeeker(val wallet: String) : IdentityVerificationResult
    data object Unverified : IdentityVerificationResult
    data class Error(val message: String) : IdentityVerificationResult
}

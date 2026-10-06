package com.nopeek.app.data

/**
 * The result of verifying a usage report against a challenge limit.
 *
 * Contract mirrors the oracle settle logic in the plan:
 *  - Tamper signals (permission revoked, clock changed, report late) -> UNVERIFIED (treats report as untrustworthy).
 *  - Otherwise, PASS if usedSeconds <= limitSeconds, else FAIL.
 */
enum class VerificationStatus {
    PASS,
    FAIL,
    UNVERIFIED;

    companion object {
        fun ordered(): List<VerificationStatus> = listOf(PASS, FAIL, UNVERIFIED)
    }
}

/**
 * Flags collected by the usage engine that make a report untrustworthy.
 * Used by the oracle to mark a result UNVERIFIED instead of PASS/FAIL.
 */
data class TamperSignals(
    val permissionRevoked: Boolean = false,
    val clockChanged: Boolean = false,
    val reportLate: Boolean = false
) {
    fun isCompromised(): Boolean = permissionRevoked || clockChanged || reportLate
}

/**
 * A usage report submitted by the app to the oracle for a challenge window.
 *
 * The evidence fields are all defaulted so this stays the same DTO the existing
 * submission path builds, and so a report can exist before the oracle has ruled
 * on it: `verified` is the oracle's answer, not the device's opinion. They are
 * transport-neutral on purpose — the byte format lives in
 * `data/attest/CanonicalPayload`, and a future React Native product layer reads
 * these fields without owning how they were produced.
 */
data class UsageReport(
    val id: String = "",
    val challengePubkey: String = "",
    val walletPubkey: String = "",
    val usedSeconds: Int,
    val windowStart: Long = 0L,
    val windowEnd: Long = 0L,
    val tamperSignals: TamperSignals,
    val submittedAt: Long = System.currentTimeMillis(),
    val evidenceHash: String? = null,
    val canonicalBytesHex: String? = null,
    val verified: Boolean = false,
    val rejectReason: String? = null,
    val deviceActiveSeconds: Long = 0L,
    val eventCount: Int = 0,
    val eventSetDigestHex: String = "",
) {
    /**
     * Resolve the verification status for this report against the challenge limit.
     * Tamper signals always win -> UNVERIFIED, matching oracle settle behavior.
     */
    fun resolveResult(limitSeconds: Int): VerificationStatus {
        if (tamperSignals.isCompromised()) return VerificationStatus.UNVERIFIED
        return if (usedSeconds <= limitSeconds) VerificationStatus.PASS else VerificationStatus.FAIL
    }
}

package com.nopeek.app.data.verify

import com.nopeek.app.data.TamperSignals
import com.nopeek.app.data.UsageReport
import com.nopeek.app.data.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the CommitmentVerifier extraction changed no behavior: the local
 * implementation must reproduce exactly the matrix `UsageReportTest` already pins
 * against `resolveResult`. If this table and that suite ever disagree, the seam
 * has become a second source of truth rather than one.
 */
class CommitmentVerifierTest {

    private val local = LocalRuleCommitmentVerifier()
    private val oracle = OracleVerdictCommitmentVerifier()

    @Test
    fun localRuleVerifierMatchesHistoricalResolveResultMatrix() {
        val cases = listOf(
            Triple("under limit", report(usedSeconds = 300), VerificationStatus.PASS),
            Triple("over limit", report(usedSeconds = 700), VerificationStatus.FAIL),
            Triple("at limit", report(usedSeconds = 600), VerificationStatus.PASS),
            Triple(
                "permission revoked while under limit",
                report(10, TamperSignals(permissionRevoked = true)),
                VerificationStatus.UNVERIFIED,
            ),
            Triple(
                "clock changed while under limit",
                report(10, TamperSignals(clockChanged = true)),
                VerificationStatus.UNVERIFIED,
            ),
            Triple(
                "report late while under limit",
                report(10, TamperSignals(reportLate = true)),
                VerificationStatus.UNVERIFIED,
            ),
        )
        for ((name, usage, expected) in cases) {
            assertEquals(name, expected, local.verify(usage, limitSeconds = 600))
        }
    }

    @Test
    fun tamperSignalsOverrideAPassingUsageNumber() {
        // The fail-closed rule is the product. A report that says "0 seconds" while
        // the permission was revoked is not a pass, and no refactor may make it one.
        val clean = report(usedSeconds = 0)
        val compromised = report(0, TamperSignals(permissionRevoked = true))
        assertEquals(VerificationStatus.PASS, local.verify(clean, 600))
        assertEquals(VerificationStatus.UNVERIFIED, local.verify(compromised, 600))
    }

    @Test
    fun oracleVerifierRefusesToPassUnverifiedEvidence() {
        val unverified = report(usedSeconds = 10).copy(verified = false, rejectReason = "BAD_SIGNATURE")
        assertEquals(VerificationStatus.UNVERIFIED, oracle.verify(unverified, 600))

        val verifiedUnder = report(usedSeconds = 10).copy(verified = true)
        assertEquals(VerificationStatus.PASS, oracle.verify(verifiedUnder, 600))

        val verifiedOver = report(usedSeconds = 900).copy(verified = true)
        assertEquals(VerificationStatus.FAIL, oracle.verify(verifiedOver, 600))
    }

    @Test
    fun oracleVerifierTreatsAContradictionBetweenSignalsAndAcceptedEvidenceAsUnverified() {
        // If the device reported tampering yet the oracle accepted the signed bytes,
        // the two disagree and neither can be believed alone. Fail closed: a
        // contradiction is never a PASS.
        val contradictory = report(10, TamperSignals(permissionRevoked = true)).copy(verified = true)
        assertEquals(VerificationStatus.UNVERIFIED, oracle.verify(contradictory, 600))
    }

    private fun report(usedSeconds: Int, signals: TamperSignals = TamperSignals()) = UsageReport(
        challengePubkey = "Ch1111111111111111111111111111111111111111322",
        walletPubkey = "6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj",
        usedSeconds = usedSeconds,
        windowStart = 1727623200L,
        windowEnd = 1727626800L,
        tamperSignals = signals,
    )
}

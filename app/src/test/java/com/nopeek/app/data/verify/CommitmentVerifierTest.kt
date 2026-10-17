package com.nopeek.app.data.verify

import com.nopeek.app.data.attest.ReportOutcome
import com.nopeek.app.data.TamperSignals
import com.nopeek.app.data.UsageReport
import com.nopeek.app.data.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun oracleVerifierRejectsEvidenceTheOracleMarkedRejectedDespiteClaimingVerified() {
        // verified and rejectReason together describe an oracle that contradicted
        // itself. Without a case that sets rejectReason while verified == true, the
        // guard is unreachable from the suite and deleting it changes nothing.
        val contradictory = report(10).copy(verified = true, rejectReason = "NONCE_REPLAYED")
        assertEquals(VerificationStatus.UNVERIFIED, oracle.verify(contradictory, 600))
    }

    @Test
    fun localVerifierAgreesWithFlagForVerificationOnEverySignalCombination() {
        // This equivalence is what makes routing UsageWorker through the verifier
        // behavior-identical rather than a hope: the worker's existing
        // PENDING_VERIFICATION trigger must fire exactly when verify says UNVERIFIED.
        for (revoked in listOf(false, true)) {
            for (clock in listOf(false, true)) {
                for (late in listOf(false, true)) {
                    for (forward in listOf(false, true)) {
                        // Named, not positional: a fourth boolean on a data class of
                        // booleans is exactly how a reorder would mis-map silently.
                        val signals = TamperSignals(
                            permissionRevoked = revoked,
                            clockChanged = clock,
                            reportLate = late,
                            clockJumpedForward = forward,
                        )
                        val verdict = local.verify(report(10, signals), 600)
                        assertEquals(
                            "signals=$signals",
                            com.nopeek.app.data.usage.flagForVerification(signals),
                            verdict == VerificationStatus.UNVERIFIED,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun forwardClockJumpAloneIsEnoughToFailVerification() {
        // Pinned separately so the new dimension cannot be dropped from isCompromised()
        // without a named test failing, rather than only showing up as one of 16 rows.
        val onlyForward = TamperSignals(clockJumpedForward = true)
        assertEquals(VerificationStatus.UNVERIFIED, local.verify(report(10, onlyForward), 600))
        assertTrue(com.nopeek.app.data.usage.flagForVerification(onlyForward))
    }

    private fun report(usedSeconds: Int, signals: TamperSignals = TamperSignals()) = UsageReport(
        challengePubkey = "Ch1111111111111111111111111111111111111111322",
        walletPubkey = "6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj",
        usedSeconds = usedSeconds,
        windowStart = 1727623200L,
        windowEnd = 1727626800L,
        tamperSignals = signals,
    )

    // ---------------------------------------------------------------------------
    // Task 15: the oracle verdict, not the device's number, drives settlement when
    // the app can reach the oracle. selectVerifier is the one selector that owns
    // "which path is authoritative" — the worker calls verify() on its result and
    // reads no signal fields directly afterwards.
    // ---------------------------------------------------------------------------

    @Test
    fun oracleAcceptedOutcomeUsesTheOracleVerifierAndMarksTheReportVerified() {
        // The oracle answered "this is real evidence" — the device's local number is
        // no longer the authority; it is evidence only, subject to the oracle's
        // PASS/FAIL bound as recorded on-chain.
        val (verifier, report) = selectVerifier(
            ReportOutcome.Accepted("deadbeef"), local = report(usedSeconds = 300),
        )
        assertTrue(verifier is OracleVerdictCommitmentVerifier)
        assertEquals(true, report.verified)
        assertEquals("deadbeef", report.evidenceHash)
        assertEquals(null, report.rejectReason)
    }

    @Test
    fun oracleRefusedOutcomeUsesTheOracleVerifierAndMarksTheReportRejected() {
        // Same rule in the other direction: a refused submission is a verdict the
        // oracle rendered; the local verifier must not re-derive PASS from the
        // device's own usage number after the oracle said no.
        val (verifier, report) = selectVerifier(
            ReportOutcome.Rejected("BAD_SIGNATURE"), local = report(usedSeconds = 300),
        )
        assertTrue(verifier is OracleVerdictCommitmentVerifier)
        assertEquals(false, report.verified)
        assertEquals("BAD_SIGNATURE", report.rejectReason)
    }

    @Test
    fun transportFailureOrUnreachedWindowFallsBackToTheLocalRule() {
        // No oracle answer exists: a network miss, or a window that was due but
        // never submitted (NotYetDue). The local rule is fallback ONLY — never the
        // input to settlement when the oracle was reachable.
        for (outcome in listOf<ReportOutcome>(
            ReportOutcome.TransportFailed("timeout"), ReportOutcome.NotYetDue,
        )) {
            val (verifier, report) = selectVerifier(outcome, local = report(usedSeconds = 300))
            assertEquals("fallback for $outcome", LocalRuleCommitmentVerifier::class, verifier::class)
            // The fallback path never stamps oracle fields: it computes from the
            // device numbers alone, exactly as UsageReportTest pins it.
            assertEquals(false, report.verified)
            assertEquals(null, report.evidenceHash)
            assertEquals(null, report.rejectReason)
        }
    }
}

package com.nopeek.app.data.verify

import com.nopeek.app.data.UsageReport
import com.nopeek.app.data.VerificationStatus
import com.nopeek.app.data.attest.ReportOutcome

/**
 * The one seam between "what the device measured" and "whether the commitment was
 * kept".
 *
 * Existence and shape are the point: nothing above this interface may reason about
 * tamper signals or usage numbers directly, so a React Native product layer can
 * later call the same contract over a bridge without any UI code changing its
 * mind about how compliance is decided. The Kotlin usage and attestation code
 * becomes the native module behind it rather than being rewritten.
 */
interface CommitmentVerifier {
    fun verify(report: UsageReport, limitSeconds: Int): VerificationStatus
}

/**
 * Today's behavior, unchanged: the device's own signals and numbers decide.
 *
 * Retained for the offline path and as the regression baseline the extraction was
 * proven against. It must never be the authority for a settlement, because a
 * report it would pass is exactly what an attacker controls.
 */
class LocalRuleCommitmentVerifier : CommitmentVerifier {
    override fun verify(report: UsageReport, limitSeconds: Int): VerificationStatus =
        report.resolveResult(limitSeconds)
}

/**
 * The verifier the app binds once the oracle is wired (plan Task 15).
 *
 * Two independent conditions must agree before anything is a PASS: the oracle
 * accepted the signed, attested bytes, and the device did not report tampering.
 * Either one failing is UNVERIFIED, which the existing fail-closed rule turns into
 * a loss. That is deliberate — if the device says its own permission was revoked
 * yet bytes were accepted, the two disagree and no verdict can be trusted, so a
 * contradiction settles against the claim rather than against the skeptic.
 */
class OracleVerdictCommitmentVerifier : CommitmentVerifier {
    override fun verify(report: UsageReport, limitSeconds: Int): VerificationStatus {
        if (!report.verified || report.rejectReason != null) return VerificationStatus.UNVERIFIED
        if (report.tamperSignals.isCompromised()) return VerificationStatus.UNVERIFIED
        return if (report.usedSeconds <= limitSeconds) VerificationStatus.PASS else VerificationStatus.FAIL
    }
}

/**
 * The Task 15 selector: which verifier is authoritative for THIS report, and the
 * verdict fields to stamp onto it from that source.
 *
 * One rule only — nothing above the interface reads the fields it sets, so the
 * worker stays behavior-identical and only its authority source changes:
 *
 * - Accepted | Rejected by the oracle: the oracle's verdict is real. Stamp the
 *   report's verified/rejectReason/evidenceHash from that answer and use the
 *   oracle verifier. The device's own usedSeconds is evidence the oracle
 *   ratified, never the authority.
 * - Transport failed, or the window was never even submitted (NotYetDue): no
 *   oracle answer exists, so the local rule is the offline fallback. It stamps
 *   nothing — the report keeps its defaults so resolveResult() /
 *   flagForVerification() stay the sole authority for that path.
 *
 * Returning the chosen verifier alongside the (possibly-stamped) report is what
 * lets this single decision be proven in a JVM test with no network or keystore.
 */
fun selectVerifier(
    outcome: ReportOutcome,
    local: UsageReport,
): Pair<CommitmentVerifier, UsageReport> = when (outcome) {
    is ReportOutcome.Accepted -> {
        val stamped = local.copy(verified = true, evidenceHash = outcome.evidenceHash)
        Pair(OracleVerdictCommitmentVerifier(), stamped)
    }
    is ReportOutcome.Rejected -> {
        val stamped = local.copy(verified = false, rejectReason = outcome.reason)
        Pair(OracleVerdictCommitmentVerifier(), stamped)
    }
    // TransportFailed and NotYetDue both mean: no oracle verdict to read yet.
    is ReportOutcome.TransportFailed,
    ReportOutcome.NotYetDue -> Pair(LocalRuleCommitmentVerifier(), local)
}

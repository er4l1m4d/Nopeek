package com.nopeek.app.data.verify

import com.nopeek.app.data.UsageReport
import com.nopeek.app.data.VerificationStatus

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

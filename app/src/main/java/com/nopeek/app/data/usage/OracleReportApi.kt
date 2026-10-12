package com.nopeek.app.data.usage

import com.nopeek.app.data.TamperSignals
import com.nopeek.app.data.UsageReport
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Supabase endpoints + report payloads (Phase 8.1).
 * URL placeholder: replace with your project ref per contracts/USER_ACTIONS.md Phase 6.
 */
object NopeekBackend {
    const val SUPABASE_URL = "https://ybcavetnzivwsfnwzvtd.supabase.co"

    /**
     * PostgREST key for public reads (settlement_results) — see migration public-read policy.
     *
     * This is a publishable client credential, not a secret: it is compiled into the APK, so
     * anyone with the app can already extract it, and committing it exposes nothing extra. The
     * thing that actually protects the data is the grant boundary, which was verified against
     * the live project on 2026-10-11 — anon can read settlement_results and is refused on
     * challenges, usage_reports, install_keys and report_nonces. The service_role key never
     * appears here and lives only in Supabase Edge Function secrets.
     */
    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InliY2F2ZXRueml2d3Nmbnd6dnRkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE0MTM0MTUsImV4cCI6MjEwNjk4OTQxNX0.A2o4YPy0bYua3jgj5Lh07fWZxvZqBWjya-tMeDitvJM"

    fun isConfigured(): Boolean =
        !SUPABASE_URL.contains("PROJECT-REF") && !SUPABASE_ANON_KEY.contains("PLACEHOLDER")

    fun oracleUrl(path: String): String = "$SUPABASE_URL/functions/v1/oracle/$path"

    fun restUrl(path: String): String = "$SUPABASE_URL/rest/v1/$path"
}

fun isoUtc(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(java.util.Date(epochMs))

/** Plan 8.1 Step 1: only challengePubkey, walletPubkey, usedSeconds, window times, tamperSignals. */
fun reportPayload(report: UsageReport): Map<String, Any> = mapOf(
    "challengePubkey" to report.challengePubkey,
    "walletPubkey" to report.walletPubkey,
    "usedSeconds" to report.usedSeconds,
    "windowStart" to isoUtc(report.windowStart),
    "windowEnd" to isoUtc(report.windowEnd),
    "tamperSignals" to tamperSignalNames(report.tamperSignals),
)

fun tamperSignalNames(signals: TamperSignals): List<String> = buildList {
    if (signals.permissionRevoked) add("PERMISSION_REVOKED")
    if (signals.clockChanged) add("CLOCK_CHANGED")
    if (signals.clockJumpedForward) add("CLOCK_JUMPED_FORWARD")
    if (signals.reportLate) add("REPORT_LATE")
}

/** Plan 8.1 Step 3: any present tamper signal -> local STATUS_PENDING_VERIFICATION. */
fun flagForVerification(signals: TamperSignals): Boolean = signals.isCompromised()

// The legacy unsigned POST lived here. It is deleted rather than left beside the signed path:
// the deployed oracle answers that body with EVIDENCE_MISSING, so a caller using it would
// believe a report had been filed when nothing was verified. Reports are signed and submitted
// through com.nopeek.app.data.attest.EvidenceReporter, which reuses reportPayload() from this
// file for the plain fields and adds the attested evidence to the same body.

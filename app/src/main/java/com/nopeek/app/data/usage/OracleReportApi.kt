package com.nopeek.app.data.usage

import com.nopeek.app.data.TamperSignals
import com.nopeek.app.data.UsageReport
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Supabase endpoints + report payloads (Phase 8.1).
 * URL placeholder: replace with your project ref per contracts/USER_ACTIONS.md Phase 6.
 */
object NopeekBackend {
    const val SUPABASE_URL = "https://PROJECT-REF.supabase.co"

    /** PostgREST key for public reads (settlement_results) — see migration public-read policy. */
    const val SUPABASE_ANON_KEY = "ANON-KEY-PLACEHOLDER"

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

object OracleReportApi {

    /**
     * POST /submit-report. Fire-and-forget: returns false when unconfigured or the
     * network failed — the 15-minute periodic worker retries next cycle.
     */
    fun submit(report: UsageReport): Boolean {
        if (!NopeekBackend.isConfigured()) return false
        val body = JSONObject(reportPayload(report) as Map<*, *>)
        val connection = (URL(NopeekBackend.oracleUrl("submit-report")).openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            connection.responseCode in 200..299
        } catch (e: IOException) {
            false
        } finally {
            connection.disconnect()
        }
    }
}

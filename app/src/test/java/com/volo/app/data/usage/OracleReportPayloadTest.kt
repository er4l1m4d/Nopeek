package com.volo.app.data.usage

import com.volo.app.data.TamperSignals
import com.volo.app.data.UsageReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class OracleReportPayloadTest {

    private fun report(
        signals: TamperSignals = TamperSignals(),
        windowStart: Long = 1_760_000_000_000L,
        windowEnd: Long = 1_760_086_400_000L,
    ) = UsageReport(
        challengePubkey = "11111111111111111111111111111113",
        walletPubkey = "11111111111111111111111111111114",
        usedSeconds = 1234,
        windowStart = windowStart,
        windowEnd = windowEnd,
        tamperSignals = signals,
        submittedAt = 1_760_090_000_000L,
    )

    @Test
    fun payload_containsOnlyTheFivePlanFields() {
        val payload = reportPayload(report())
        assertEquals(
            setOf(
                "challengePubkey", "walletPubkey", "usedSeconds",
                "windowStart", "windowEnd", "tamperSignals",
            ),
            payload.keys,
        )
    }

    @Test
    fun payload_passesNumbersThrough() {
        val payload = reportPayload(report())
        assertEquals("11111111111111111111111111111113", payload["challengePubkey"])
        assertEquals("11111111111111111111111111111114", payload["walletPubkey"])
        assertEquals(1234, payload["usedSeconds"])
    }

    @Test
    fun windowTimes_serializeAsIso8601Utc() {
        val start = 1_760_000_000_000L
        val payload = reportPayload(report(windowStart = start, windowEnd = start + 86_400_000L))
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsedStart = format.parse(payload["windowStart"] as String)
        val parsedEnd = format.parse(payload["windowEnd"] as String)
        assertEquals(Date(start), parsedStart)
        assertEquals(Date(start + 86_400_000L), parsedEnd)
    }

    @Test
    fun tamperSignals_serializeAsNameArray() {
        assertEquals(emptyList<String>(), reportPayload(report())["tamperSignals"])
        assertEquals(
            listOf("PERMISSION_REVOKED", "CLOCK_CHANGED"),
            reportPayload(
                report(signals = TamperSignals(permissionRevoked = true, clockChanged = true))
            )["tamperSignals"],
        )
        assertEquals(
            listOf("REPORT_LATE"),
            reportPayload(report(signals = TamperSignals(reportLate = true)))["tamperSignals"],
        )
    }

    @Test
    fun compromised_reportsFlaggedForVerification() {
        assertTrue(flagForVerification(TamperSignals(permissionRevoked = true)))
        assertTrue(flagForVerification(TamperSignals(clockChanged = true)))
        assertTrue(flagForVerification(TamperSignals(reportLate = true)))
        assertTrue(!flagForVerification(TamperSignals()))
    }
}

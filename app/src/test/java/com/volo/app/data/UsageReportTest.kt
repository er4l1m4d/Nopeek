package com.volo.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageReportTest {

    @Test
    fun result_passes_whenUnderLimit() {
        val report = UsageReport(
            challengePubkey = "Ch1111111111111111111111111111111111111111322",
            walletPubkey = "6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj",
            usedSeconds = 300,
            windowStart = 1727623200L,
            windowEnd = 1727626800L,
            tamperSignals = TamperSignals()
        )
        assertEquals(VerificationStatus.PASS, report.resolveResult(limitSeconds = 600))
    }

    @Test
    fun result_fails_whenOverLimit() {
        val report = UsageReport(
            usedSeconds = 700,
            tamperSignals = TamperSignals()
        )
        assertEquals(VerificationStatus.FAIL, report.resolveResult(limitSeconds = 600))
    }

    @Test
    fun result_unverified_whenPermissionRevoked() {
        val report = UsageReport(
            usedSeconds = 10,
            tamperSignals = TamperSignals(permissionRevoked = true)
        )
        assertEquals(VerificationStatus.UNVERIFIED, report.resolveResult(limitSeconds = 600))
    }

    @Test
    fun result_unverified_whenClockChanged() {
        val report = UsageReport(
            usedSeconds = 10,
            tamperSignals = TamperSignals(clockChanged = true)
        )
        assertEquals(VerificationStatus.UNVERIFIED, report.resolveResult(limitSeconds = 600))
    }

    @Test
    fun result_unverified_whenReportLate() {
        val report = UsageReport(
            usedSeconds = 10,
            tamperSignals = TamperSignals(reportLate = true)
        )
        assertEquals(VerificationStatus.UNVERIFIED, report.resolveResult(limitSeconds = 600))
    }

    @Test
    fun emptyTamperSignals_defaultAllFalse() {
        val signals = TamperSignals()
        assertEquals(false, signals.permissionRevoked)
        assertEquals(false, signals.clockChanged)
        assertEquals(false, signals.reportLate)
    }

    @Test
    fun statusOrder_matchesContract() {
        assertEquals(listOf(VerificationStatus.PASS, VerificationStatus.FAIL, VerificationStatus.UNVERIFIED),
            VerificationStatus.ordered())
    }
}

package com.volo.app.data.usage

import com.volo.app.data.TamperSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAggregatorTest {

    private val windowStart = 0L
    private val windowEnd = 100_000L // [0, 100_000] ms window

    private fun event(pkg: String, type: Int, ts: Long) = UsageEvent(pkg, type, ts)

    @Test
    fun single_full_session_within_window() {
        // Foreground 20s -> Background 40s => 20s used
        val events = listOf(
            event("com.target", USAGE_EVENT_MOVE_TO_FOREGROUND, 20_000),
            event("com.target", USAGE_EVENT_MOVE_TO_BACKGROUND, 40_000)
        )
        val used = aggregateUsage(events, windowStart, windowEnd)
        assertEquals(20, used)
    }

    @Test
    fun session_clipped_at_window_start_when_fg_before_window() {
        // fg before window, bg at 40s => only counts 0..40s = 40s
        val events = listOf(
            event("com.target", USAGE_EVENT_MOVE_TO_FOREGROUND, -10_000),
            event("com.target", USAGE_EVENT_MOVE_TO_BACKGROUND, 40_000)
        )
        val used = aggregateUsage(events, windowStart, windowEnd)
        assertEquals(40, used)
    }

    @Test
    fun session_clipped_at_window_end_when_bg_after_window() {
        // fg at 20s, bg after window => counts 20s..100s = 80s
        val events = listOf(
            event("com.target", USAGE_EVENT_MOVE_TO_FOREGROUND, 20_000),
            event("com.target", USAGE_EVENT_MOVE_TO_BACKGROUND, 120_000)
        )
        val used = aggregateUsage(events, windowStart, windowEnd)
        assertEquals(80, used)
    }

    @Test
    fun unclosed_session_counts_to_window_end() {
        // fg at 30s, no background => counts 30s..100s = 70s
        val events = listOf(
            event("com.target", USAGE_EVENT_MOVE_TO_FOREGROUND, 30_000)
        )
        val used = aggregateUsage(events, windowStart, windowEnd)
        assertEquals(70, used)
    }

    @Test
    fun only_target_package_counted() {
        // Two apps active simultaneously; only target package counted
        val events = listOf(
            event("com.target", USAGE_EVENT_MOVE_TO_FOREGROUND, 0),
            event("com.other", USAGE_EVENT_MOVE_TO_FOREGROUND, 10_000),
            event("com.target", USAGE_EVENT_MOVE_TO_BACKGROUND, 50_000),
            event("com.other", USAGE_EVENT_MOVE_TO_BACKGROUND, 60_000)
        )
        val used = aggregateUsage(events, windowStart, windowEnd, targetPackage = "com.target")
        assertEquals(50, used)
    }

    @Test
    fun tamper_permission_revoked_sets_signal() {
        val t = detectTamperSignals(
            permissionRevoked = true, clockJumped = false,
            reportTime = windowEnd, windowEnd = windowEnd, graceSeconds = 60
        )
        assertTrue(t.permissionRevoked)
        assertTrue(t.isCompromised())
    }

    @Test
    fun tamper_clock_jump_sets_signal() {
        val t = detectTamperSignals(
            permissionRevoked = false, clockJumped = true,
            reportTime = windowEnd, windowEnd = windowEnd, graceSeconds = 60
        )
        assertTrue(t.clockChanged)
        assertTrue(t.isCompromised())
    }

    @Test
    fun report_late_within_grace_is_not_late() {
        val t = detectTamperSignals(
            permissionRevoked = false, clockJumped = false,
            reportTime = windowEnd + 30_000, windowEnd = windowEnd, graceSeconds = 60
        )
        assertFalse(t.reportLate)
        assertFalse(t.isCompromised())
    }

    @Test
    fun report_late_beyond_grace_is_flagged() {
        val t = detectTamperSignals(
            permissionRevoked = false, clockJumped = false,
            reportTime = windowEnd + 61_000, windowEnd = windowEnd, graceSeconds = 60
        )
        assertTrue(t.reportLate)
    }
}

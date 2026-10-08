package com.nopeek.app.data.usage

import com.nopeek.app.data.TamperSignals
import com.nopeek.app.data.UsageReport

/**
 * Mirrors the event-type int constants of `android.app.usage.UsageEvents.Event`.
 * Inlined plain ints so JVM unit tests compile without the android.jar stub.
 *
 * These are NOT arbitrary tags: `UsageStatsManager` delivers exactly these numbers, so a
 * value that drifts from the platform makes every filter below match nothing, and the
 * failure is invisible in tests because the tests build events from these same symbols.
 * `UsageEventTypeConstantsTest` pins each one against the real framework class.
 * (`ACTIVITY_RESUMED`/`ACTIVITY_PAUSED` are the modern aliases for the same 1 and 2.)
 */
object UsageEventType {
    const val MOVE_TO_FOREGROUND = 1
    const val MOVE_TO_BACKGROUND = 2
}

val USAGE_EVENT_MOVE_TO_FOREGROUND = UsageEventType.MOVE_TO_FOREGROUND
val USAGE_EVENT_MOVE_TO_BACKGROUND = UsageEventType.MOVE_TO_BACKGROUND

/**
 * A discrete usage event: a package moving to foreground/background at [timestamp] ms (epoch).
 * Events are consumed in chronological order; the aggregator sorts defensively.
 */
data class UsageEvent(
    val packageName: String,
    val eventType: Int,
    val timestamp: Long
)

data class UsageSyncParams(
    val challengePubkey: String,
    val walletPubkey: String,
    val targetPackage: String,
    val windowStart: Long,
    val windowEnd: Long,
    val limitSeconds: Int,
    val graceSeconds: Int = 60,
    val reportTime: Long = System.currentTimeMillis()
)

/**
 * Pure aggregation of foreground usage seconds for [targetPackage] within the window
 * [windowStart, windowEnd] (epoch ms).
 *
 * Sessions are derived from MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND event pairs.
 * Unclosed sessions (foreground with no following background) are charged up to [windowEnd].
 */
fun aggregateUsage(
    events: List<UsageEvent>,
    windowStart: Long,
    windowEnd: Long,
    targetPackage: String? = null
): Int {
    val pkg = targetPackage
    val relevant = events
        .asSequence()
        .filter { pkg == null || it.packageName == pkg }
        .filter { it.eventType == USAGE_EVENT_MOVE_TO_FOREGROUND || it.eventType == USAGE_EVENT_MOVE_TO_BACKGROUND }
        .sortedBy { it.timestamp }
        .toList()

    var totalMs = 0L
    var fgStart: Long? = null

    for (event in relevant) {
        when (event.eventType) {
            USAGE_EVENT_MOVE_TO_FOREGROUND -> {
                // A new foreground begins; close any still-open session defensively.
                fgStart = event.timestamp
            }
            USAGE_EVENT_MOVE_TO_BACKGROUND -> {
                val start = fgStart ?: continue
                if (event.timestamp > start) {
                    val segStart = start.coerceIn(windowStart, windowEnd)
                    val segEnd = event.timestamp.coerceIn(windowStart, windowEnd)
                    totalMs += (segEnd - segStart).coerceAtLeast(0L)
                }
                fgStart = null
            }
        }
    }

    // Unclosed session charges up to windowEnd.
    fgStart?.let { start ->
        if (start < windowEnd) {
            val segStart = start.coerceIn(windowStart, windowEnd)
            totalMs += (windowEnd - segStart).coerceAtLeast(0L)
        }
    }

    return (totalMs / 1000L).toInt()
}

/**
 * Build tamper signals from independently-collected flags.
 *
 * @param permissionRevoked whether AppOpsManager reports Usage Access DENY since last window.
 * @param clockJumped whether the wall clock jumped backward >5 min during the window.
 * @param reportTime epoch ms at which the report is produced.
 * @param windowEnd epoch ms of the challenge window end.
 * @param graceSeconds allowed reporting delay before the report is considered late.
 * @param clockJumpedForward wall clock outran monotonic time during the window; see
 *   [isClockJumpedForward]. Distinct from [clockJumped], which is a backward jump.
 */
fun detectTamperSignals(
    permissionRevoked: Boolean,
    clockJumped: Boolean,
    reportTime: Long,
    windowEnd: Long,
    graceSeconds: Int,
    clockJumpedForward: Boolean = false,
): TamperSignals {
    val reportLate = (reportTime - windowEnd) > (graceSeconds * 1000L)
    return TamperSignals(
        permissionRevoked = permissionRevoked,
        clockChanged = clockJumped,
        reportLate = reportLate,
        clockJumpedForward = clockJumpedForward,
    )
}

/**
 * Whether the wall clock advanced further than real time did between two observations.
 *
 * Monotonic time (`SystemClock.elapsedRealtime`) cannot be set by the user, so it is the
 * yardstick. A device that moved the clock forward to end a window early, or back-dated a
 * session, shows up as wall-clock gain exceeding monotonic gain by more than [allowanceMs]
 * — the tolerance for scheduling jitter between the two reads.
 */
fun isClockJumpedForward(
    lastWallMs: Long,
    lastElapsedRealtimeMs: Long,
    wallNowMs: Long,
    nowElapsedRealtimeMs: Long,
    allowanceMs: Long,
): Boolean {
    val monotonicGain = nowElapsedRealtimeMs - lastElapsedRealtimeMs
    if (monotonicGain < 0L) {
        // `elapsedRealtime` counts up from boot, so it cannot go backwards on its own.
        // A negative gain means the boot epoch reset between the two observations - the
        // device rebooted - and the stored wall-clock anchor is now measured against a
        // different boot. That is not evidence of innocence; it is the check losing its
        // yardstick, and an unchecked comparison is one a power-button reboot would
        // switch off mid-duel. Reject.
        return true
    }
    return wallNowMs - lastWallMs > monotonicGain + allowanceMs
}

/**
 * How much further the wall clock may run than monotonic time before it counts as
 * tampering, sized to the window being proved.
 *
 * The tolerance exists for honest clock corrections (an NTP/NITZ fix after the clock
 * drifted), not for anything a user sets: timezone and DST changes do not move
 * `currentTimeMillis`, which is UTC epoch. Sizing it off the window is what keeps it
 * meaningful — the previous behaviour of reusing the 5-minute backward-jump threshold
 * gave a cheater 299 s of free forward drift in a 120 s duel, which is longer than the
 * duel and longer than the limit combined. Capped at a minute so a 60-minute window does
 * not buy six free minutes, floored at five seconds so a sub-minute window still allows
 * one genuine correction.
 */
fun forwardJumpAllowanceMs(windowStartMs: Long, windowEndMs: Long): Long =
    ((windowEndMs - windowStartMs) / 10L)
        .coerceAtLeast(5_000L)
        .coerceAtMost(60_000L)

/** Plan 8.1 Step 2: submit to the oracle's POST /submit-report (no-op until backend configured). */
fun postReport(report: UsageReport) {
    OracleReportApi.submit(report)
}

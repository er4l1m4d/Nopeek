package com.nopeek.app.data.usage

import com.nopeek.app.data.TamperSignals
import com.nopeek.app.data.UsageReport

/**
 * Mirrors the event-type int constants of `android.app.usage.UsageEvents.Event`.
 * Inlined plain ints so JVM unit tests compile without the android.jar stub.
 */
object UsageEventType {
    const val MOVE_TO_FOREGROUND = 100
    const val MOVE_TO_BACKGROUND = 101
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
    if (monotonicGain < 0L) return false
    return wallNowMs - lastWallMs > monotonicGain + allowanceMs
}

/** Plan 8.1 Step 2: submit to the oracle's POST /submit-report (no-op until backend configured). */
fun postReport(report: UsageReport) {
    OracleReportApi.submit(report)
}

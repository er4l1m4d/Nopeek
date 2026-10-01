package com.volo.app.data.usage

import com.volo.app.data.TamperSignals
import com.volo.app.data.UsageReport

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
 */
fun detectTamperSignals(
    permissionRevoked: Boolean,
    clockJumped: Boolean,
    reportTime: Long,
    windowEnd: Long,
    graceSeconds: Int
): TamperSignals {
    val reportLate = (reportTime - windowEnd) > (graceSeconds * 1000L)
    return TamperSignals(
        permissionRevoked = permissionRevoked,
        clockChanged = clockJumped,
        reportLate = reportLate
    )
}

fun postReport(report: UsageReport) {}

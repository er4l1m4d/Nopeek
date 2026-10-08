package com.nopeek.app.data.usage

import android.content.Context
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.security.MessageDigest

/**
 * Everything the oracle needs to cross-check a usage report, derived from ONE read of
 * the device's event stream.
 *
 * What this constrains is internal contradiction, not truthfulness. A device cannot
 * inflate `deviceActiveSeconds` to make a large `usedSeconds` look plausible, because the
 * figure is the union of every package's foreground span and the digest commits the whole
 * stream behind it. What it cannot do is prove the phone was quiet when it wasn't: that
 * would need screen-on broadcasts a background app cannot get, and spec 5 says so out
 * loud. The honest reading is that these three numbers make a lie checkable by someone
 * else, not that they make a lie impossible.
 *
 * Union, not sum: two packages foreground at once are one stretch of device activity.
 */
data class EventSet(
    val events: List<UsageEvent>,
    val windowStartMs: Long,
    val windowEndMs: Long,
    /** Union of every package's foreground span inside the window, in whole seconds. */
    val deviceActiveSeconds: Long,
    /** Count of distinct `package:eventType:timestamp` triples inside the window, which is the digest's domain. */
    val eventCount: Int,
    /** Lowercase hex SHA-256 over the sorted, de-duplicated in-window triples joined by newlines. */
    val eventSetDigestHex: String,
)

/**
 * The device's own record of what happened, signed and shipped as evidence.
 *
 * `collect()` is the only Android-dependent part; the derivation below is pure so it
 * can be proven on the JVM. A React Native layer would call this same collector through
 * a bridge rather than re-deriving the digest — the digest is the contract, not the class.
 */
class UsageEventCollector(private val context: Context) {

    fun collect(windowStartMs: Long, windowEndMs: Long): EventSet = buildEventSet(
        queryEvents(windowStartMs, windowEndMs),
        windowStartMs,
        windowEndMs,
    )

    fun usedSecondsFor(eventSet: EventSet, targetPackage: String): Int =
        usedSecondsWithin(eventSet, targetPackage)

    /**
     * Reads the device's event stream for the window, plus a lookbehind.
     *
     * The lookbehind exists because a session that opened before `windowStartMs` and is
     * still running produces no foreground event inside the window at all — asking only
     * about the window would report 0 s used for an app that never left the screen.
     * `buildEventSet` clamps those spans to the window and keeps them out of the
     * committed digest, so the lookbehind buys accuracy in the arithmetic without
     * changing what the device signs.
     *
     * Every event type is kept. `foregroundIntervals` and `aggregateUsage` pick the two
     * they care about; the digest must cover the whole stream, or a report with no
     * evidence collected is indistinguishable from a phone that was genuinely idle.
     */
    private fun queryEvents(windowStartMs: Long, windowEndMs: Long): List<UsageEvent> {
        val manager = context.applicationContext
            .getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val out = ArrayList<UsageEvent>()
        val stream = manager.queryEvents(windowStartMs - LOOKBEHIND_MS, windowEndMs)
        val event = UsageEvents.Event()
        while (stream.hasNextEvent()) {
            stream.getNextEvent(event)
            val pkg = event.packageName ?: continue
            out.add(UsageEvent(pkg, event.eventType, event.timeStamp))
        }
        return out
    }

    companion object {
        /** How far before the window to read, so a session straddling the start is charged. */
        const val LOOKBEHIND_MS = 6 * 60 * 60 * 1000L
    }
}

/** Pure derivation, so the digest and the bounds are testable without a device. */
fun buildEventSet(
    events: List<UsageEvent>,
    windowStartMs: Long,
    windowEndMs: Long,
): EventSet {
    // Committed domain: everything the device reported INSIDE the window, every event
    // type, all packages — spec 4.2. Not filtered to foreground/background, because a
    // narrower hash is what would make "no evidence collected" and "a quiet phone" the
    // same bytes. Events outside the window may be passed in for span arithmetic (a
    // session that opened before windowStart and is charged inside it) and are dropped
    // here.
    val inWindow = events.filter { it.timestamp in windowStartMs..windowEndMs }
    val triples = inWindow
        .map { "${it.packageName}:${it.eventType}:${it.timestamp}" }
        .toSortedSet()
        .toList()

    val activeMs = foregroundIntervals(events, windowStartMs, windowEndMs)
        .mergeMillis()

    return EventSet(
        events = events,
        windowStartMs = windowStartMs,
        windowEndMs = windowEndMs,
        deviceActiveSeconds = activeMs / 1000L,
        eventCount = triples.size,
        eventSetDigestHex = sha256Hex(triples.joinToString("\n")),
    )
}

/** Per-package seconds only. The whole-device figure is deliberately not this function. */
fun usedSecondsWithin(eventSet: EventSet, targetPackage: String): Int =
    aggregateUsage(
        events = eventSet.events,
        windowStart = eventSet.windowStartMs,
        windowEnd = eventSet.windowEndMs,
        targetPackage = targetPackage,
    )

/**
 * Foreground spans per package, clamped to the window. A session still open at
 * collection time is charged to [windowEndMs], matching `aggregateUsage`.
 */
private fun foregroundIntervals(
    events: List<UsageEvent>,
    windowStartMs: Long,
    windowEndMs: Long,
): List<LongRange> {
    val byPackage = events
        .filter { it.eventType == UsageEventType.MOVE_TO_FOREGROUND || it.eventType == UsageEventType.MOVE_TO_BACKGROUND }
        .groupBy { it.packageName }

    val spans = ArrayList<LongRange>()
    for ((_, list) in byPackage) {
        var open: Long? = null
        for (event in list.sortedBy { it.timestamp }) {
            when (event.eventType) {
                UsageEventType.MOVE_TO_FOREGROUND -> open = event.timestamp
                UsageEventType.MOVE_TO_BACKGROUND -> {
                    val start = open
                    open = null
                    if (start != null && event.timestamp > start) {
                        addClamped(spans, start, event.timestamp, windowStartMs, windowEndMs)
                    }
                }
            }
        }
        open?.let { addClamped(spans, it, windowEndMs, windowStartMs, windowEndMs) }
    }
    return spans
}

private fun addClamped(
    into: MutableList<LongRange>,
    startMs: Long,
    endMs: Long,
    windowStartMs: Long,
    windowEndMs: Long,
) {
    val s = startMs.coerceIn(windowStartMs, windowEndMs)
    val e = endMs.coerceIn(windowStartMs, windowEndMs)
    if (e > s) into.add(s..e)
}

/** Union, not sum: overlapping spans of device activity count once. */
private fun List<LongRange>.mergeMillis(): Long {
    if (isEmpty()) return 0L
    val sorted = sortedBy { it.first }
    var total = 0L
    var curStart = sorted.first().first
    var curEnd = sorted.first().last
    for (span in sorted.drop(1)) {
        if (span.first <= curEnd) {
            if (span.last > curEnd) curEnd = span.last
        } else {
            total += curEnd - curStart
            curStart = span.first
            curEnd = span.last
        }
    }
    total += curEnd - curStart
    return total
}

private fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

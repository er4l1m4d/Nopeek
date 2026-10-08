package com.nopeek.app.data.usage

import android.content.Context
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.security.MessageDigest

/**
 * Everything the oracle needs to cross-check a usage report, derived from ONE read of
 * the device's event stream.
 *
 * The point of collecting the whole device rather than only the target package is the
 * consistency bound: a report claiming 40 minutes of Instagram on a device whose own
 * event stream says it was active for 12 minutes is physically impossible, and the
 * oracle rejects it (`USAGE_EXCEEDS_ACTIVE`) without trusting this app's arithmetic.
 * That only works if the device-wide figure is honest, so it is the union of all
 * packages' foreground spans — NOT the sum, which a device with two overlapping
 * sessions would inflate.
 */
data class EventSet(
    val events: List<UsageEvent>,
    val windowStartMs: Long,
    val windowEndMs: Long,
    /** Union of every package's foreground span inside the window, in whole seconds. */
    val deviceActiveSeconds: Long,
    /** Count of distinct `package:eventType:timestamp` triples, matching the digest's domain. */
    val eventCount: Int,
    /** Lowercase hex SHA-256 over the sorted, de-duplicated triples joined by newlines. */
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
     * Reads foreground/background transitions for the window.
     *
     * `queryEvents` returns events up to the moment of the call, so an open session is
     * charged to [windowEndMs] by `buildEventSet` — the same rule `aggregateUsage` uses.
     */
    private fun queryEvents(windowStartMs: Long, windowEndMs: Long): List<UsageEvent> {
        val manager = context.applicationContext
            .getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val out = ArrayList<UsageEvent>()
        val stream = manager.queryEvents(windowStartMs, windowEndMs)
        val event = UsageEvents.Event()
        while (stream.hasNextEvent()) {
            stream.getNextEvent(event)
            if (event.eventType == UsageEventType.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEventType.MOVE_TO_BACKGROUND
            ) {
                event.packageName?.let { pkg ->
                    out.add(UsageEvent(pkg, event.eventType, event.timeStamp))
                }
            }
        }
        return out
    }
}

/** Pure derivation, so the digest and the bounds are testable without a device. */
fun buildEventSet(
    events: List<UsageEvent>,
    windowStartMs: Long,
    windowEndMs: Long,
): EventSet {
    val triples = events
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

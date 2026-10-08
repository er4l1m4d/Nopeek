package com.nopeek.app.data.usage

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Task 12 Step 3: the measurement a JVM test cannot make.
 *
 * Every unit test in this package builds its `UsageEvent`s from `UsageEventType`'s own
 * constants, so a constant that does not match what `UsageStatsManager` actually emits
 * makes the whole suite pass while the device returns an empty event set. That is not
 * hypothetical — it is exactly what happened (100/101 vs the platform's 1/2), and it made
 * every report read 0 seconds, which the oracle scored as a PASS. Only a real device can
 * tell. These tests are the standing guard for that class of bug.
 *
 * Numbers go to logcat under `NOPEEK_STEP3` and are transcribed into
 * `contracts/ATTESTATION_DEPLOY_NOTES.md`.
 */
class UsageCollectorOnDeviceTest {

    private val collector = UsageEventCollector(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun realStreamContainsForegroundEventsTheCollectorMatches() {
        val end = System.currentTimeMillis()
        val start = end - SIX_HOURS_MS
        val set = collector.collect(start, end)

        val histogram = set.events.groupingBy { it.eventType }.eachCount()
        Log.i(TAG, "history window=6h eventCount=${set.eventCount} " +
            "deviceActiveSeconds=${set.deviceActiveSeconds} digest=${set.eventSetDigestHex}")
        Log.i(TAG, "typeHistogram=$histogram (looking for " +
            "FG=${UsageEventType.MOVE_TO_FOREGROUND}, BG=${UsageEventType.MOVE_TO_BACKGROUND})")

        assertTrue(
            "queryEvents returned nothing over 6h of real use — usage access not " +
                "effective, or the platform delivered no events at all",
            set.events.isNotEmpty(),
        )
        // The bug-catcher: these two must be non-zero, because the collector only turns
        // events into seconds when it recognises foreground and background transitions.
        val fg = histogram[UsageEventType.MOVE_TO_FOREGROUND] ?: 0
        val bg = histogram[UsageEventType.MOVE_TO_BACKGROUND] ?: 0
        assertTrue(
            "MOVE_TO_FOREGROUND=${UsageEventType.MOVE_TO_FOREGROUND} matched $fg events and " +
                "MOVE_TO_BACKGROUND=${UsageEventType.MOVE_TO_BACKGROUND} matched $bg in a real " +
                "6h stream (histogram=$histogram). If both are 0 the constants do not match " +
                "the platform and every report will read 0 seconds.",
            fg > 0 && bg > 0,
        )
        assertTrue("foreground events exist but deviceActiveSeconds is 0", set.deviceActiveSeconds > 0)
    }

    /**
     * Runs only when driven with an explicit window and package, so a plain `./gradlew
     * connectedAndroidTest` does not silently skip the honest check:
     *
     *   am instrument -e class ...#knownIntervalMatchesReality \
     *     -e windowStartMs <ms> -e windowEndMs <ms> -e targetPackage com.instagram.android
     */
    @Test
    fun knownIntervalMatchesReality() {
        val args = InstrumentationRegistry.getArguments()
        val start = args.getString("windowStartMs")?.toLongOrNull()
        val end = args.getString("windowEndMs")?.toLongOrNull()
        val target = args.getString("targetPackage")
        assumeTrue("driven without -e windowStartMs/-e windowEndMs/-e targetPackage", start != null && end != null && target != null)
        require(start != null && end != null && target != null)

        val set = collector.collect(start, end)
        val used = collector.usedSecondsFor(set, target)
        val actualIntervalSec = (end - start) / 1000.0

        Log.i(TAG, "KNOWN INTERVAL target=$target intervalSeconds=${"%.1f".format(actualIntervalSec)}")
        Log.i(TAG, "  usedSecondsFor=$used deviceActiveSeconds=${set.deviceActiveSeconds} " +
            "eventCount=${set.eventCount}")
        Log.i(TAG, "  digest=${set.eventSetDigestHex}")
        val topByPkg = set.events
            .groupingBy { it.packageName }
            .eachCount()
            .entries.sortedByDescending { it.value }
            .take(8)
        Log.i(TAG, "  busiestPackages=$topByPkg")

        // Invariants that must hold for any honest reading of the window.
        assertTrue("$target was opened for a known interval but usedSecondsFor read $used", used > 0)
        assertTrue(
            "usedSeconds ($used) exceeded the device's own active time (${set.deviceActiveSeconds}) " +
                "— the oracle would reject this report as USAGE_EXCEEDS_ACTIVE",
            used <= set.deviceActiveSeconds,
        )
        // The report cannot claim more than the interval it covers.
        assertTrue("usedSeconds $used is larger than the ${actualIntervalSec}s window", used <= actualIntervalSec + 1)
        // And the target really is in the committed event set, not only in the aggregate.
        assertTrue(
            "$target produced no events inside the window (histogram by package: $topByPkg)",
            set.events.any { it.packageName == target },
        )
    }

    private companion object {
        const val TAG = "NOPEEK_STEP3"
        const val SIX_HOURS_MS = 6 * 60 * 60 * 1000L
    }
}

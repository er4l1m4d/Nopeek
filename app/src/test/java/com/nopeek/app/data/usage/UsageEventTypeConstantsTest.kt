package com.nopeek.app.data.usage

import android.app.usage.UsageEvents
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The inlined ints in [UsageEventType] exist so the pure aggregation code runs in JVM
 * tests without an android.jar stub. That convenience has a cost this test pays: every
 * other test builds its events FROM those symbols, so a symbol that does not match what
 * `UsageStatsManager` actually delivers makes the whole suite pass while the device
 * returns nothing.
 *
 * Verified against the real framework class, not the compile-time stub, which is why this
 * runs under Robolectric. `ACTIVITY_RESUMED`/`ACTIVITY_PAUSED` are the current names for
 * the same two values; `MOVE_TO_*` are the deprecated aliases and are what the event
 * stream is documented to emit for older targets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageEventTypeConstantsTest {

    @Test
    fun foregroundConstantIsThePlatformValue() {
        assertEquals(UsageEvents.Event.MOVE_TO_FOREGROUND, UsageEventType.MOVE_TO_FOREGROUND)
        assertEquals(UsageEvents.Event.ACTIVITY_RESUMED, UsageEventType.MOVE_TO_FOREGROUND)
    }

    @Test
    fun backgroundConstantIsThePlatformValue() {
        assertEquals(UsageEvents.Event.MOVE_TO_BACKGROUND, UsageEventType.MOVE_TO_BACKGROUND)
        assertEquals(UsageEvents.Event.ACTIVITY_PAUSED, UsageEventType.MOVE_TO_BACKGROUND)
    }
}

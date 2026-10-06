package com.nopeek.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsagePermissionStateTest {

    // AppOpsManager constants (inlined at compile time; safe for JVM tests)
    private val MODE_ALLOWED = 0
    private val MODE_IGNORED = 1
    private val MODE_ERRORED = 2
    private val MODE_DEFAULT = 3
    private val MODE_FOREGROUND = 4

    @Test
    fun allowedMode_mapsToGranted() {
        assertEquals(UsagePermissionState.GRANTED, usagePermissionStateForMode(MODE_ALLOWED))
    }

    @Test
    fun foregroundMode_mapsToGranted() {
        // Some OEMs report FOREGROUND (4) for Usage Access when granted.
        assertEquals(UsagePermissionState.GRANTED, usagePermissionStateForMode(MODE_FOREGROUND))
    }

    @Test
    fun ignoredMode_mapsToNeedsPermission() {
        assertEquals(UsagePermissionState.NEEDS_PERMISSION, usagePermissionStateForMode(MODE_IGNORED))
    }

    @Test
    fun erroredMode_mapsToUnavailable() {
        assertEquals(UsagePermissionState.UNAVAILABLE, usagePermissionStateForMode(MODE_ERRORED))
    }

    @Test
    fun defaultMode_mapsToNeedsPermission() {
        assertEquals(UsagePermissionState.NEEDS_PERMISSION, usagePermissionStateForMode(MODE_DEFAULT))
    }

    @Test
    fun unknownMode_mapsToUnavailable() {
        assertEquals(UsagePermissionState.UNAVAILABLE, usagePermissionStateForMode(99))
    }
}

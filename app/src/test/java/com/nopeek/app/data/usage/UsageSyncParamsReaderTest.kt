package com.nopeek.app.data.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageSyncParamsReaderTest {

    private val full = mapOf(
        UsageSyncParamsReader.KEY_CHALLENGE to "chal-1",
        UsageSyncParamsReader.KEY_WALLET to "wall-1",
        UsageSyncParamsReader.KEY_TARGET_PACKAGE to "com.target",
        UsageSyncParamsReader.KEY_WINDOW_START to 0L,
        UsageSyncParamsReader.KEY_WINDOW_END to 100_000L,
        UsageSyncParamsReader.KEY_LIMIT_SECONDS to 300,
        UsageSyncParamsReader.KEY_GRACE_SECONDS to 60
    )

    @Test
    fun full_map_parses_to_params() {
        val p = UsageSyncParamsReader.from(full)!!
        assertEquals("chal-1", p.challengePubkey)
        assertEquals("wall-1", p.walletPubkey)
        assertEquals("com.target", p.targetPackage)
        assertEquals(0L, p.windowStart)
        assertEquals(100_000L, p.windowEnd)
        assertEquals(300, p.limitSeconds)
        assertEquals(60, p.graceSeconds)
    }

    @Test
    fun missing_challenge_returns_null() {
        assertNull(UsageSyncParamsReader.from(full - UsageSyncParamsReader.KEY_CHALLENGE))
    }

    @Test
    fun missing_window_end_returns_null() {
        assertNull(UsageSyncParamsReader.from(full - UsageSyncParamsReader.KEY_WINDOW_END))
    }

    @Test
    fun wrong_type_limit_returns_null() {
        assertNull(UsageSyncParamsReader.from(full + (UsageSyncParamsReader.KEY_LIMIT_SECONDS to "300")))
    }

    @Test
    fun grace_defaults_to_60_when_missing() {
        val p = UsageSyncParamsReader.from(full - UsageSyncParamsReader.KEY_GRACE_SECONDS)!!
        assertEquals(60, p.graceSeconds)
    }
}

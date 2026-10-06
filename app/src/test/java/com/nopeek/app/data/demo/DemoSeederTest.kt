package com.nopeek.app.data.demo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoSeederTest {

    private val config = JSONObject(
        """{
            "targetApp": "com.tiktok.android",
            "limitSeconds": 60,
            "stakeAmountRaw": 1000000,
            "windowSeconds": 300,
            "opponentWallet": "9fG8jK2hL4mN7pQ1rT6sW3xY8bV5cD2eF9gH7jK4ABC",
            "challengePubkey": null
        }"""
    )

    @Test
    fun demoChallenge_matchesPlanParameters() {
        val now = 1_760_000_000_000L
        val entity = demoChallengeFrom(config, now)
        assertEquals("demo-challenge", entity.id)
        assertEquals("com.tiktok.android", entity.targetApp)
        assertEquals(60, entity.limitSeconds)
        assertEquals(1_000_000L, entity.stakeAmountRaw)
        assertEquals(300L, entity.windowSeconds)
        assertEquals(now, entity.startTime)
        assertEquals(now + 300_000L, entity.endTime)
        assertEquals("CREATED", entity.localStatus)
        assertEquals("9fG8jK2hL4mN7pQ1rT6sW3xY8bV5cD2eF9gH7jK4ABC", entity.opponentWallet)
        assertEquals(null, entity.challengePubkey)
    }
}

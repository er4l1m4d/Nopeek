package com.volo.app.data.local

import com.volo.app.data.Challenge
import org.junit.Assert.assertEquals
import org.junit.Test

class ChallengeMappingTest {

    private fun challenge() = Challenge(
        id = "local-1",
        challengePubkey = null,
        creatorWallet = "creatorABC",
        opponentWallet = "11111111111111111111111111111111",
        targetApp = "com.instagram.android",
        limitSeconds = 1_800,
        stakeAmountRaw = 5_000_000L,
        windowSeconds = 86_400L,
        startTime = 1_700_000_000_000L,
        endTime = 1_700_086_400_000L
    )

    @Test
    fun model_to_entity_preserves_all_fields() {
        val entity = challenge().toEntity()
        assertEquals("local-1", entity.id)
        assertEquals(null, entity.challengePubkey)
        assertEquals("creatorABC", entity.creatorWallet)
        assertEquals("11111111111111111111111111111111", entity.opponentWallet)
        assertEquals("com.instagram.android", entity.targetApp)
        assertEquals(1_800, entity.limitSeconds)
        assertEquals(5_000_000L, entity.stakeAmountRaw)
        assertEquals(86_400L, entity.windowSeconds)
        assertEquals(1_700_000_000_000L, entity.startTime)
        assertEquals(1_700_086_400_000L, entity.endTime)
        assertEquals("CREATED", entity.localStatus)
    }

    @Test
    fun entity_to_model_roundtrip_is_lossless() {
        val original = challenge().copy(localStatus = "ACTIVE")
        assertEquals(original, original.toEntity().toModel())
    }

    @Test
    fun local_status_defaults_to_created() {
        assertEquals("CREATED", challenge().localStatus)
    }
}

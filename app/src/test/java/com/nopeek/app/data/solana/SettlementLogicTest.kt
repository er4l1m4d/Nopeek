package com.nopeek.app.data.solana

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementLogicTest {

    private fun oracleBody(
        bothFail: Boolean = false,
        settled: Boolean = true,
        creatorResult: Int = 1,
        opponentResult: Int = 3,
        settlementResult: Int = 1,
        status: String = "CREATOR_WON",
    ) = JSONObject()
        .put("ok", true)
        .put("challengePubkey", "Chal1111111111111111111111111111111111111111")
        .put("status", status)
        .put("bothFail", bothFail)
        .put("settled", settled)
        .put("creatorResult", creatorResult)
        .put("opponentResult", opponentResult)
        .put("settlementResult", settlementResult)

    private fun restRow(
        bothFail: Boolean = false,
        settled: Boolean = true,
        creatorCode: Int = 1,
        opponentCode: Int = 3,
        statusCode: Int = 1,
        status: String = "CREATOR_WON",
    ) = JSONArray().put(
        JSONObject()
            .put("challenge_pubkey", "Chal1111111111111111111111111111111111111111")
            .put("status", status)
            .put("both_fail", bothFail)
            .put("settled", settled)
            .put("creator_code", creatorCode)
            .put("opponent_code", opponentCode)
            .put("status_code", statusCode)
    )

    @Test
    fun parseSettleResponse_readsOraclePostFields() {
        val parsed = parseSettleResponse(oracleBody())
        assertEquals("Chal1111111111111111111111111111111111111111", parsed.challengePubkey)
        assertEquals(1, parsed.creatorResult)
        assertEquals(3, parsed.opponentResult)
        assertEquals(1, parsed.settlementResult)
        assertEquals("SETTLED", parsed.localStatus)
    }

    @Test
    fun parseSettleResponse_bothFailBranch() {
        val parsed = parseSettleResponse(
            oracleBody(
                bothFail = true,
                settled = false,
                creatorResult = 2,
                opponentResult = 2,
                settlementResult = 4,
                status = "BOTH_FAIL_LOCKED",
            )
        )
        assertTrue(parsed.bothFail)
        assertFalse(parsed.settled)
        assertEquals(4, parsed.settlementResult)
        assertEquals("BOTH_FAIL_LOCKED", parsed.localStatus)
    }

    @Test
    fun parseSettlementRow_mapsSnakeCaseColumns() {
        val parsed = parseSettlementRow(restRow(creatorCode = 3, opponentCode = 2, statusCode = 2, status = "OPPONENT_WON"))
        assertEquals(3, parsed!!.creatorResult)
        assertEquals(2, parsed.opponentResult)
        assertEquals(2, parsed.settlementResult)
        assertEquals("OPPONENT_WON", parsed.status)
        assertEquals("SETTLED", parsed.localStatus)
    }

    @Test
    fun parseSettlementRow_emptyArray_isNull() {
        assertNull(parseSettlementRow(JSONArray()))
    }

    @Test
    fun ready_requiresWindowEnd() {
        val now = 1_760_000_000_000L
        val row = parseSettlementRow(restRow())
        assertFalse(isSettlementReady(now, now + 1, row))
    }

    @Test
    fun ready_settledRowAfterEnd() {
        val now = 1_760_000_000_000L
        val row = parseSettlementRow(restRow())
        assertTrue(isSettlementReady(now, now - 1, row))
    }

    @Test
    fun ready_bothFailRowAfterEnd() {
        val now = 1_760_000_000_000L
        val row = parseSettlementRow(restRow(bothFail = true, settled = false, statusCode = 4, status = "BOTH_FAIL_LOCKED"))
        assertTrue(isSettlementReady(now, now - 1, row))
    }

    @Test
    fun ready_noRow_false() {
        val now = 1_760_000_000_000L
        assertFalse(isSettlementReady(now, now - 1, null))
    }

    @Test
    fun ready_unsettledNonBothFail_false() {
        val now = 1_760_000_000_000L
        val row = parseSettlementRow(restRow(settled = false, bothFail = false))
        assertFalse(isSettlementReady(now, now - 1, row))
    }

    @Test
    fun registerBody_matchesOracleContract() {
        val entity = com.nopeek.app.data.local.ChallengeEntity(
            id = "e1",
            challengePubkey = "Chal1111111111111111111111111111111111111111",
            creatorWallet = "11111111111111111111111111111113",
            opponentWallet = "11111111111111111111111111111114",
            targetApp = "com.example.app",
            limitSeconds = 1200,
            stakeAmountRaw = 5_000_000,
            windowSeconds = 86_400,
            startTime = 1_760_000_000_000L,
            endTime = 1_760_086_400_000L,
            localStatus = "ACTIVE",
        )
        val body = registerChallengeBody(entity)
        assertEquals(
            setOf(
                "challengePubkey", "creatorPubkey", "opponentPubkey", "targetApp",
                "limitSeconds", "windowSeconds", "stakeAmount", "endTime",
            ),
            body.keys().asSequence().toSet(),
        )
        assertEquals("Chal1111111111111111111111111111111111111111", body.getString("challengePubkey"))
        assertEquals("11111111111111111111111111111113", body.getString("creatorPubkey"))
        assertEquals("com.example.app", body.getString("targetApp"))
        assertEquals(1200, body.getInt("limitSeconds"))
        assertEquals(5_000_000, body.getLong("stakeAmount"))
        assertTrue(body.getString("endTime").endsWith("Z"))
    }

    @Test
    fun canSettle_onlyActiveAfterWindowEnd() {
        val now = 1_760_000_000_000L
        fun entity(status: String, end: Long, pk: String? = "Chal1111111111111111111111111111111111111111") =
            com.nopeek.app.data.local.ChallengeEntity(
                id = "e1",
                challengePubkey = pk,
                creatorWallet = "11111111111111111111111111111113",
                opponentWallet = "11111111111111111111111111111114",
                targetApp = "com.example.app",
                limitSeconds = 1200,
                stakeAmountRaw = 5_000_000,
                windowSeconds = 86_400,
                startTime = now - 90_000,
                endTime = end,
                localStatus = status,
            )

        assertTrue(com.nopeek.app.ui.screens.canSettle(entity("ACTIVE", now - 1), now))
        assertFalse(com.nopeek.app.ui.screens.canSettle(entity("ACTIVE", now + 1), now))
        assertFalse(com.nopeek.app.ui.screens.canSettle(entity("CREATED", now - 1), now))
        assertFalse(com.nopeek.app.ui.screens.canSettle(entity("BOTH_FAIL_LOCKED", now - 1), now))
        assertFalse(com.nopeek.app.ui.screens.canSettle(entity("ACTIVE", now - 1, pk = null), now))
    }
}

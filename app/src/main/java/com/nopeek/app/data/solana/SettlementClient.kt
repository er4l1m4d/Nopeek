package com.nopeek.app.data.solana

import com.nopeek.app.data.local.ChallengeEntity
import com.nopeek.app.data.usage.NopeekBackend
import com.nopeek.app.data.usage.isoUtc
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plan Task 8.2: settlement readiness + oracle trigger + result fetching.
 *
 * Settlement flow: after window end the app asks the oracle (POST /settle) to
 * evaluate reports; the oracle stores a settlement_results row. The on-chain
 * settle transaction itself needs the oracle wallet signature (MWA, user present)
 * and is sent via ChallengeRepository.settleChallenge — except the both-fail case,
 * which locks locally for the Recovery Lock screen (plan 8.2 Step 3).
 */
data class OracleSettlement(
    val challengePubkey: String,
    val status: String,
    val bothFail: Boolean,
    val settled: Boolean,
    val creatorResult: Int,
    val opponentResult: Int,
    val settlementResult: Int,
) {
    val localStatus: String
        get() = if (bothFail) "BOTH_FAIL_LOCKED" else "SETTLED"
}

/** POST /settle response body (oracle index.ts). */
fun parseSettleResponse(json: JSONObject): OracleSettlement = OracleSettlement(
    challengePubkey = json.optString("challengePubkey"),
    status = json.optString("status"),
    bothFail = json.optBoolean("bothFail"),
    settled = json.optBoolean("settled"),
    creatorResult = json.optInt("creatorResult"),
    opponentResult = json.optInt("opponentResult"),
    settlementResult = json.optInt("settlementResult"),
)

/** PostgREST GET settlement_results?challenge_pubkey=eq.X — returns a JSON array. */
fun parseSettlementRow(rows: JSONArray): OracleSettlement? {
    if (rows.length() == 0) return null
    val row = rows.getJSONObject(0)
    return OracleSettlement(
        challengePubkey = row.optString("challenge_pubkey"),
        status = row.optString("status"),
        bothFail = row.optBoolean("both_fail"),
        settled = row.optBoolean("settled"),
        creatorResult = row.optInt("creator_code"),
        opponentResult = row.optInt("opponent_code"),
        settlementResult = row.optInt("status_code"),
    )
}

/**
 * Plan 8.2 Step 1: poll for readiness — window over AND the settlement row is
 * final (settled=true, or bothFail which finalizes as a lock instead of a payout).
 */
fun isSettlementReady(nowMs: Long, endTimeMs: Long, row: OracleSettlement?): Boolean =
    nowMs > endTimeMs && row != null && (row.settled || row.bothFail)

class SettlementClient {

    /** GET the settlement_results row, or null when absent/unconfigured/failed. */
    suspend fun fetchSettlementResult(challengePk: String): OracleSettlement? {
        if (!NopeekBackend.isConfigured()) return null
        val connection = (URL(
            NopeekBackend.restUrl("settlement_results?challenge_pubkey=eq.$challengePk")
        ).openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("apikey", NopeekBackend.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${NopeekBackend.SUPABASE_ANON_KEY}")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
            if (connection.responseCode in 200..299) parseSettlementRow(JSONArray(body)) else null
        } catch (e: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** POST /settle — ask the oracle to evaluate reports and store the outcome. */
    suspend fun triggerSettlement(challengePk: String): OracleSettlement? {
        if (!NopeekBackend.isConfigured()) return null
        val connection = (URL(NopeekBackend.oracleUrl("settle")).openConnection() as HttpURLConnection)
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("challengePubkey", challengePk).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            when (connection.responseCode) {
                in 200..299 -> parseSettleResponse(
                    JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).readText())
                )
                409 -> fetchSettlementResult(challengePk) // already settled upstream — read the row
                else -> null
            }
        } catch (e: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

/**
 * Plan 8.2 Step 1 driver: existing row wins; otherwise, past the window,
 * register the challenge (plan 6.1 annotation: app calls /register-challenge —
 * the oracle's challenges row must exist or /settle 404s) and trigger the
 * oracle. Returns null while not ready (keeps polling).
 */
suspend fun sync(entity: ChallengeEntity): OracleSettlement? {
    val pk = entity.challengePubkey ?: return null
    fetchSettlementResult(pk)?.let { return it }
    if (System.currentTimeMillis() <= entity.endTime) return null
    if (entity.creatorWallet != null) registerChallenge(entity)
    return triggerSettlement(pk)
}

/** POST /register-challenge — idempotent upsert on the oracle side. */
private suspend fun registerChallenge(entity: ChallengeEntity): Boolean {
    if (!NopeekBackend.isConfigured()) return false
    val connection = (URL(NopeekBackend.oracleUrl("register-challenge")).openConnection() as HttpURLConnection)
    return try {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use {
            it.write(registerChallengeBody(entity).toString().toByteArray(Charsets.UTF_8))
        }
        connection.responseCode in 200..299
    } catch (e: IOException) {
        false
    } finally {
        connection.disconnect()
    }
}
}

/** Plan 6.1 annotation / oracle register contract (camelCase, pure, tested). */
fun registerChallengeBody(entity: ChallengeEntity): JSONObject = JSONObject()
    .put("challengePubkey", entity.challengePubkey)
    .put("creatorPubkey", entity.creatorWallet)
    .put("opponentPubkey", entity.opponentWallet)
    .put("targetApp", entity.targetApp)
    .put("limitSeconds", entity.limitSeconds)
    .put("windowSeconds", entity.windowSeconds)
    .put("stakeAmount", entity.stakeAmountRaw)
    .put("endTime", isoUtc(entity.endTime))

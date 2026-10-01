package com.volo.app.data.solana

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class RpcException(message: String) : Exception(message)

/**
 * JSON-RPC client for devnet plus IDL-driven transaction builders (Phase 7.1/7.2).
 * Build methods compile unsigned wire-format transactions; signing happens through MWA
 * (see ChallengeSender).
 */
class ProgramClient(
    private val idl: VoloIdl,
    private val rpcUrl: String = SolanaConfig.RPC_URL,
) {
    val programId: String = ProgramInstructions.programIdFrom(idl)

    suspend fun getRecentBlockhash(): String = withContext(Dispatchers.IO) {
        val params = JSONArray().put(JSONObject().put("commitment", "finalized"))
        val result = rpc("getLatestBlockhash", params)
            .optJSONObject("value")
            ?: throw RpcException("missing blockhash value")
        val blockhash = result.optString("blockhash")
        if (blockhash.isEmpty()) throw RpcException("empty blockhash")
        blockhash
    }

    suspend fun getChallenge(pubkey: String): ChallengeAccount? = withContext(Dispatchers.IO) {
        val config = JSONObject().put("encoding", "base64").put("commitment", "confirmed")
        val params = JSONArray().put(pubkey).put(config)
        val result = rpc("getAccountInfo", params)
        val value = result.optJSONObject("value") ?: return@withContext null
        if (value.isNull("data")) return@withContext null
        val data = value.getJSONArray("data").getString(0)
        val bytes = Base64.getDecoder().decode(data)
        ChallengeAccountCodec.decode(bytes)
    }

    suspend fun sendTransaction(signedTx: ByteArray): String = withContext(Dispatchers.IO) {
        val config = JSONObject()
            .put("encoding", "base64")
            .put("preflightCommitment", "confirmed")
        val params = JSONArray()
            .put(Base64.getEncoder().encodeToString(signedTx))
            .put(config)
        val signature = rpc("sendTransaction", params).optString("result")
        if (signature.isEmpty()) throw RpcException("sendTransaction returned no signature")
        signature
    }

    suspend fun buildCreateChallenge(params: CreateChallengeParams): ByteArray {
        val ix = ProgramInstructions.createChallenge(idl, params)
        val blockhash = getRecentBlockhash()
        return TransactionCompiler.compileTransaction(blockhash, params.creator, listOf(ix))
    }

    suspend fun buildJoinChallenge(params: JoinChallengeParams): ByteArray {
        val ix = ProgramInstructions.joinChallenge(idl, params)
        val blockhash = getRecentBlockhash()
        return TransactionCompiler.compileTransaction(blockhash, params.opponent, listOf(ix))
    }

    suspend fun buildSettleChallenge(params: SettleChallengeParams): ByteArray {
        val ix = ProgramInstructions.settleChallenge(idl, params)
        val blockhash = getRecentBlockhash()
        return TransactionCompiler.compileTransaction(blockhash, params.oracle, listOf(ix))
    }

    suspend fun buildRefundUnjoined(params: RefundUnjoinedParams): ByteArray {
        val ix = ProgramInstructions.refundUnjoined(idl, params)
        val blockhash = getRecentBlockhash()
        return TransactionCompiler.compileTransaction(blockhash, params.creator, listOf(ix))
    }

    private fun rpc(method: String, params: JSONArray): JSONObject {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", method)
            .put("params", params)
        val connection = URL(rpcUrl).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: throw RpcException("RPC HTTP ${connection.responseCode}")
            }
            val text = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            if (json.has("error")) {
                val err = json.getJSONObject("error")
                throw RpcException("RPC $method failed: ${err.opt("message") ?: err}")
            }
            json
        } finally {
            connection.disconnect()
        }
    }
}

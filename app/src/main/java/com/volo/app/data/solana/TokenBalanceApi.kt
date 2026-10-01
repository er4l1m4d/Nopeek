package com.volo.app.data.solana

import com.volo.app.data.wallet.Base58
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Plan 9.1: SOL + USDC + SKR balances for the connected wallet. */
data class TokenBalances(
    val solLamports: Long,
    val usdcRaw: Long,
    val usdcDecimals: Int,
    val skrRaw: Long,
    val skrDecimals: Int,
)

/** getBalance result -> lamports. */
fun parseBalanceLamports(result: JSONObject): Long = when (val value = result.opt("value")) {
    is Number -> value.toLong()
    else -> 0L
}

/** getTokenAccountBalance result -> (raw amount, decimals); missing account -> (0, 0). */
fun parseTokenAccountBalance(result: JSONObject): Pair<Long, Int> {
    val value = result.optJSONObject("value") ?: return 0L to 0
    val amount = value.optString("amount").toLongOrNull() ?: 0L
    return amount to value.optInt("decimals", 0)
}

fun pow10(decimals: Int): Long {
    var result = 1L
    repeat(decimals.coerceIn(0, 18)) { result *= 10 }
    return result
}

/** Raw base units -> display string ("1.5", "42"). */
fun formatUiAmount(raw: Long, decimals: Int): String {
    if (decimals <= 0) return raw.toString()
    val divisor = pow10(decimals)
    val whole = raw / divisor
    val frac = raw % divisor
    if (frac == 0L) return whole.toString()
    val fracStr = frac.toString().padStart(decimals, '0').trimEnd('0')
    return "$whole.$fracStr"
}

/** Plan 9.1 Step 3: Open Duels gate = SKR balance >= 10. */
fun openDuelsGate(skrRaw: Long, decimals: Int): Boolean =
    skrRaw >= 10L * pow10(decimals)

fun openDuelsLabel(open: Boolean): String =
    if (open) "Open Duels: Available" else "Unlock by staking 10 SKR"

/**
 * Plan 9.1 Step 1: SPL-token balances via ProgramClient's RPC transport.
 * USDC/SKR read their associated token account; a missing account (never funded)
 * is a zero balance, not an error.
 */
class TokenBalanceApi(private val client: ProgramClient) {

    suspend fun getSolBalance(wallet: String): Long = withContext(Dispatchers.IO) {
        val params = JSONArray().put(wallet)
            .put(JSONObject().put("commitment", "confirmed"))
        try {
            parseBalanceLamports(client.rpc("getBalance", params))
        } catch (e: RpcException) {
            0L
        }
    }

    suspend fun getTokenBalance(wallet: String, mint: String): Pair<Long, Int> =
        withContext(Dispatchers.IO) {
            val owner = Base58.decode(wallet) ?: return@withContext 0L to 0
            val mintBytes = Base58.decode(mint) ?: return@withContext 0L to 0
            val ata = Base58.encode(ProgramInstructions.ataAddress(owner, mintBytes).address)
            val params = JSONArray().put(ata)
                .put(JSONObject().put("commitment", "confirmed"))
            try {
                parseTokenAccountBalance(client.rpc("getTokenAccountBalance", params))
            } catch (e: RpcException) {
                0L to 0
            }
        }

    suspend fun getAll(wallet: String): TokenBalances {
        val usdc = getTokenBalance(wallet, SolanaConfig.USDC_MINT)
        val skr = getTokenBalance(wallet, SolanaConfig.SKR_MINT)
        return TokenBalances(
            solLamports = getSolBalance(wallet),
            usdcRaw = usdc.first,
            usdcDecimals = usdc.second,
            skrRaw = skr.first,
            skrDecimals = skr.second,
        )
    }
}

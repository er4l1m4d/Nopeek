package com.volo.app.data.demo

import android.content.Context
import android.content.pm.ApplicationInfo
import com.volo.app.data.local.AppDatabase
import com.volo.app.data.local.ChallengeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Plan 11.1 Step 1: demo challenge template from assets/demo-config.json (pure, tested). */
fun demoChallengeFrom(config: JSONObject, nowMs: Long): ChallengeEntity = ChallengeEntity(
    id = "demo-challenge",
    challengePubkey = if (config.isNull("challengePubkey")) null
    else config.optString("challengePubkey").ifEmpty { null },
    creatorWallet = null,
    opponentWallet = config.getString("opponentWallet"),
    targetApp = config.getString("targetApp"),
    limitSeconds = config.getInt("limitSeconds"),
    stakeAmountRaw = config.getLong("stakeAmountRaw"),
    windowSeconds = config.getLong("windowSeconds"),
    startTime = nowMs,
    endTime = nowMs + config.getLong("windowSeconds") * 1000L,
    localStatus = "CREATED",
)

/**
 * Plan 11.1 Step 1: pre-create the demo challenge on first launch of a dev build
 * (target app / limit / stake / window come from assets/demo-config.json).
 * The on-chain pubkey is not known until a wallet signs create — it is stored in
 * Room's challengePubkey when that happens (config keeps the null template).
 */
object DemoSeeder {

    fun seedIfNeeded(context: Context) {
        val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebug) return
        val prefs = context.getSharedPreferences("volo_demo", Context.MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val json = context.assets.open("demo-config.json")
                    .bufferedReader().use { it.readText() }
                val entity = demoChallengeFrom(JSONObject(json), System.currentTimeMillis())
                AppDatabase.getInstance(context).challengeDao().insert(entity)
                prefs.edit().putBoolean("seeded", true).apply()
            }
        }
    }
}

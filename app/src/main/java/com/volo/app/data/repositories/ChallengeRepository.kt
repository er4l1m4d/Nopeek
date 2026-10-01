package com.volo.app.data.repositories

import android.content.Context
import com.volo.app.data.local.AppDatabase
import com.volo.app.data.local.ChallengeDao
import com.volo.app.data.solana.IdlParser
import com.volo.app.data.solana.ProgramClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plan 7.3: poll on-chain challenge accounts every 5s and mirror status changes into Room.
 * On-chain status bytes are defined in contracts/program/src/lib.rs (STATUS_* constants).
 */
fun resolveNextStatus(localStatus: String, onChainStatus: Int): String? {
    val next = when (onChainStatus) {
        0 -> "CREATED"
        1 -> "ACTIVE"
        2 -> "SETTLED"
        3 -> "REFUNDED"
        4 -> "BOTH_FAIL_LOCKED"
        else -> return null
    }
    return next.takeIf { it != localStatus }
}

class ChallengeRepository internal constructor(
    private val dao: ChallengeDao,
    private val client: ProgramClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    fun startPolling(intervalMs: Long = POLL_INTERVAL_MS) {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                pollOnce()
                delay(intervalMs)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    suspend fun pollOnce() {
        val active = dao.getActiveChallenges()
        for (entity in active) {
            val pubkey = entity.challengePubkey ?: continue
            try {
                val account = client.getChallenge(pubkey) ?: continue
                val next = resolveNextStatus(entity.localStatus, account.status) ?: continue
                dao.updateStatus(pubkey, next)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // RPC unreachable / account not deployed yet — retry on the next tick.
            }
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 5_000L

        @Volatile
        private var instance: ChallengeRepository? = null

        fun getInstance(context: Context): ChallengeRepository =
            instance ?: synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext
                    val json = app.assets.open("idl.json").bufferedReader().use { it.readText() }
                    val client = ProgramClient(IdlParser.parse(json))
                    ChallengeRepository(AppDatabase.getInstance(app).challengeDao(), client)
                        .also { instance = it }
                }
            }
    }
}

package com.nopeek.app.data.repositories

import android.content.Context
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.nopeek.app.data.local.AppDatabase
import com.nopeek.app.data.local.ChallengeDao
import com.nopeek.app.data.local.ChallengeEntity
import com.nopeek.app.data.solana.ChallengeSender
import com.nopeek.app.data.solana.IdlParser
import com.nopeek.app.data.solana.ProgramClient
import com.nopeek.app.data.solana.ProgramInstructions
import com.nopeek.app.data.solana.SettleChallengeParams
import com.nopeek.app.data.solana.SettlementClient
import com.nopeek.app.data.solana.SolanaConfig
import com.nopeek.app.data.solana.NopeekIdl
import com.nopeek.app.data.wallet.Base58
import com.nopeek.app.data.wallet.walletManager
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
    private val idl: NopeekIdl,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private val settlementClient = SettlementClient()
    private val challengeSender by lazy { ChallengeSender(client, idl) }

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
        checkSettlements(active)
    }

    /**
     * Plan 8.2 Step 1: past the window, poll settlement_results (triggering the
     * oracle once when no row exists). Both-fail flips the local status for the
     * Recovery Lock screen (Step 3); other outcomes need the user-signed on-chain
     * settle tx, which [settleWithUser] sends from the UI.
     */
    private suspend fun checkSettlements(active: List<ChallengeEntity>) {
        val now = System.currentTimeMillis()
        for (entity in active) {
            if (now <= entity.endTime) continue
            if (entity.localStatus == "PENDING_VERIFICATION") continue
            try {
                val settlement = settlementClient.sync(entity) ?: continue
                if (settlement.bothFail) dao.updateStatus(entity.challengePubkey ?: continue, "BOTH_FAIL_LOCKED")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Oracle unreachable — try again next tick.
            }
        }
    }

    /**
     * Plan 8.2 Step 2: fetch/trigger the oracle outcome, then build, MWA-sign
     * (oracle wallet = the connected wallet, per USER_ACTIONS Phase 5) and send
     * the on-chain settle transaction.
     */
    suspend fun settleWithUser(entity: ChallengeEntity, mwaSender: ActivityResultSender): Result<String> {
        val pubkey = entity.challengePubkey
            ?: return Result.failure(IllegalStateException("Challenge has no on-chain account yet."))
        val oracle = walletManager.walletAddress
            ?: return Result.failure(IllegalStateException("Connect your wallet first — the oracle wallet signs settlement."))
        val creator = entity.creatorWallet
            ?: return Result.failure(IllegalStateException("Challenge is missing its creator wallet."))

        val settlement = settlementClient.sync(entity)
            ?: return Result.failure(IllegalStateException("Settlement not ready yet — usage reports still pending."))
        if (settlement.bothFail) {
            dao.updateStatus(pubkey, "BOTH_FAIL_LOCKED")
            return Result.failure(IllegalStateException("Both players failed — stake locked for recovery."))
        }

        val mint = Base58.decode(SolanaConfig.USDC_MINT) ?: return Result.failure(IllegalStateException("Bad mint"))
        val params = SettleChallengeParams(
            challenge = pubkey,
            oracle = oracle,
            creatorToken = Base58.encode(ProgramInstructions.ataAddress(Base58.decode(creator) ?: return Result.failure(IllegalStateException("Bad creator")), mint).address),
            opponentToken = Base58.encode(ProgramInstructions.ataAddress(Base58.decode(entity.opponentWallet) ?: return Result.failure(IllegalStateException("Bad opponent")), mint).address),
            creatorResult = settlement.creatorResult,
            opponentResult = settlement.opponentResult,
        )
        return challengeSender.settleChallenge(params, mwaSender)
            .onSuccess { dao.updateStatus(pubkey, "SETTLED") }
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
                    val idl = IdlParser.parse(json)
                    val client = ProgramClient(idl)
                    ChallengeRepository(AppDatabase.getInstance(app).challengeDao(), client, idl)
                        .also { instance = it }
                }
            }
    }
}

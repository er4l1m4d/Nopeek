package com.volo.app.data.solana

import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.volo.app.data.wallet.SignOutcome
import com.volo.app.data.wallet.WalletManager
import com.volo.app.data.wallet.walletManager

/**
 * Phase 7.2: build -> MWA-sign -> RPC-send orchestration for challenge transactions.
 * Staking transfers happen inside the program (create_challenge / join_challenge CPI),
 * so transactions carry exactly one instruction.
 */
internal class ChallengeSender(
    private val client: ProgramClient,
    private val idl: VoloIdl,
    private val wallet: WalletManager = walletManager,
) {

    suspend fun createChallenge(
        params: CreateChallengeParams,
        sender: ActivityResultSender,
    ): Result<String> = send(
        txProvider = { client.buildCreateChallenge(params) },
        feePayer = params.creator,
        instructions = listOf(ProgramInstructions.createChallenge(idl, params)),
        sender = sender,
    )

    suspend fun joinChallenge(
        params: JoinChallengeParams,
        sender: ActivityResultSender,
    ): Result<String> = send(
        txProvider = { client.buildJoinChallenge(params) },
        feePayer = params.opponent,
        instructions = listOf(ProgramInstructions.joinChallenge(idl, params)),
        sender = sender,
    )

    suspend fun settleChallenge(
        params: SettleChallengeParams,
        sender: ActivityResultSender,
    ): Result<String> = send(
        txProvider = { client.buildSettleChallenge(params) },
        feePayer = params.oracle,
        instructions = listOf(ProgramInstructions.settleChallenge(idl, params)),
        sender = sender,
    )

    suspend fun refundUnjoined(
        params: RefundUnjoinedParams,
        sender: ActivityResultSender,
    ): Result<String> = send(
        txProvider = { client.buildRefundUnjoined(params) },
        feePayer = params.creator,
        instructions = listOf(ProgramInstructions.refundUnjoined(idl, params)),
        sender = sender,
    )

    private suspend fun send(
        txProvider: suspend () -> ByteArray,
        feePayer: String,
        instructions: List<BuiltInstruction>,
        sender: ActivityResultSender,
    ): Result<String> = runCatching {
        // Recompile at send time is unnecessary — txProvider already fetched a fresh
        // blockhash; feePayer/instructions are listed here for clarity of intent.
        check(feePayer.isNotEmpty() && instructions.isNotEmpty())
        val tx = txProvider()
        val signed = when (val outcome = wallet.signTransaction(sender, tx)) {
            is SignOutcome.Signed -> outcome.transaction
            SignOutcome.NoWallet -> throw IllegalStateException("No Solana wallet found. Install Phantom or Solflare.")
            SignOutcome.Cancelled -> throw IllegalStateException("Signing cancelled in the wallet.")
            is SignOutcome.Failed -> throw IllegalStateException(outcome.message)
        }
        client.sendTransaction(signed)
    }
}

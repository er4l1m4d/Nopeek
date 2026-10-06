package com.nopeek.app.data.solana

import com.nopeek.app.data.wallet.Base58

data class AccountKey(
    val pubkey: String,
    val isSigner: Boolean,
    val isWritable: Boolean,
    val name: String = "",
)

data class BuiltInstruction(
    val programId: String,
    val accounts: List<AccountKey>,
    val data: ByteArray,
)

data class CreateChallengeParams(
    val creator: String,
    val opponent: String,
    val stakeAmount: Long,
    val targetApp: String,
    val limitSeconds: Int,
    val windowSeconds: Int,
    val creatorTokenAccount: String,
    val mint: String = SolanaConfig.USDC_MINT,
)

data class JoinChallengeParams(
    val challenge: String,
    val opponent: String,
    val opponentToken: String,
    val escrow: String = "",
)

data class SettleChallengeParams(
    val challenge: String,
    val oracle: String,
    val creatorToken: String,
    val opponentToken: String,
    val creatorResult: Int,
    val opponentResult: Int,
    val escrow: String = "",
)

data class RefundUnjoinedParams(
    val challenge: String,
    val creator: String,
    val creatorToken: String,
    val escrow: String = "",
)

object ProgramInstructions {

    fun programIdFrom(idl: NopeekIdl): String = idl.address

    fun challengePda(creator: String, opponent: String, programId: String): PdaAddress =
        Pda.findProgramAddress(
            listOf(
                "challenge".toByteArray(Charsets.UTF_8),
                Base58.decode(creator) ?: error("bad creator pubkey"),
                Base58.decode(opponent) ?: error("bad opponent pubkey"),
            ),
            Base58.decode(programId) ?: error("bad program id"),
        )

    fun ataAddress(owner: ByteArray, mint: ByteArray): PdaAddress =
        Pda.findProgramAddress(
            listOf(
                owner,
                Base58.decode(SolanaConfig.TOKEN_PROGRAM_ID)!!,
                mint,
            ),
            Base58.decode(SolanaConfig.ASSOCIATED_TOKEN_PROGRAM_ID)!!,
        )

    fun challengeEscrowAta(creator: String, opponent: String, mint: String, programId: String): PdaAddress {
        val pda = challengePda(creator, opponent, programId)
        return ataAddress(pda.address, Base58.decode(mint) ?: error("bad mint"))
    }

    fun createChallenge(idl: NopeekIdl, params: CreateChallengeParams): BuiltInstruction {
        val programId = programIdFrom(idl)
        val pda = challengePda(params.creator, params.opponent, programId)
        val escrow = challengeEscrowAta(params.creator, params.opponent, params.mint, programId)

        val appBytes = params.targetApp.toByteArray(Charsets.UTF_8)
        require(appBytes.size <= 32) { "target app label too long" }
        val data = IdlParser.discriminator("create_challenge") +
            le64(params.stakeAmount) +
            le32(appBytes.size) + appBytes +
            le32(params.limitSeconds) +
            le32(params.windowSeconds)

        val accounts = listOf(
            AccountKey(Base58.encode(pda.address), isSigner = true, isWritable = true, name = "challenge"),
            AccountKey(params.creator, isSigner = true, isWritable = true, name = "creator"),
            AccountKey(params.opponent, isSigner = false, isWritable = false, name = "opponent"),
            AccountKey(params.mint, isSigner = false, isWritable = false, name = "mint"),
            AccountKey(params.creatorTokenAccount, isSigner = false, isWritable = true, name = "creator_token"),
            AccountKey(Base58.encode(escrow.address), isSigner = false, isWritable = true, name = "escrow"),
            AccountKey(SolanaConfig.TOKEN_PROGRAM_ID, false, false, name = "token_program"),
            AccountKey(SolanaConfig.ASSOCIATED_TOKEN_PROGRAM_ID, false, false, name = "associated_token_program"),
            AccountKey(SolanaConfig.SYSTEM_PROGRAM_ID, false, false, name = "system_program"),
        )
        return BuiltInstruction(programId, accounts, data)
    }

    fun joinChallenge(idl: NopeekIdl, params: JoinChallengeParams): BuiltInstruction {
        val programId = programIdFrom(idl)
        val escrow = if (params.escrow.isNotEmpty()) {
            params.escrow
        } else {
            val challenge = Base58.decode(params.challenge) ?: error("bad challenge pubkey")
            val mintBytes = Base58.decode(SolanaConfig.USDC_MINT)!!
            // escrow owner is the challenge PDA encoded in the challenge account address
            Base58.encode(ataAddress(challenge, mintBytes).address)
        }
        val accounts = listOf(
            AccountKey(params.challenge, false, true, name = "challenge"),
            AccountKey(params.opponent, true, true, name = "opponent"),
            AccountKey(params.opponentToken, false, true, name = "opponent_token"),
            AccountKey(escrow, false, true, name = "escrow"),
            AccountKey(SolanaConfig.TOKEN_PROGRAM_ID, false, false, name = "token_program"),
            AccountKey(SolanaConfig.ASSOCIATED_TOKEN_PROGRAM_ID, false, false, name = "associated_token_program"),
        )
        return BuiltInstruction(programId, accounts, IdlParser.discriminator("join_challenge"))
    }

    fun settleChallenge(idl: NopeekIdl, params: SettleChallengeParams): BuiltInstruction {
        val programId = programIdFrom(idl)
        val escrow = if (params.escrow.isNotEmpty()) {
            params.escrow
        } else {
            val challenge = Base58.decode(params.challenge) ?: error("bad challenge pubkey")
            val mintBytes = Base58.decode(SolanaConfig.USDC_MINT)!!
            Base58.encode(ataAddress(challenge, mintBytes).address)
        }
        require(params.creatorResult in 1..3) { "creator result must be 1..3" }
        require(params.opponentResult in 1..3) { "opponent result must be 1..3" }
        val data = IdlParser.discriminator("settle_challenge") +
            byteArrayOf(params.creatorResult.toByte(), params.opponentResult.toByte())
        val accounts = listOf(
            AccountKey(params.challenge, false, true, name = "challenge"),
            AccountKey(params.oracle, true, false, name = "oracle"),
            AccountKey(escrow, false, true, name = "escrow"),
            AccountKey(params.creatorToken, false, true, name = "creator_token"),
            AccountKey(params.opponentToken, false, true, name = "opponent_token"),
            AccountKey(SolanaConfig.TOKEN_PROGRAM_ID, false, false, name = "token_program"),
            AccountKey(SolanaConfig.ASSOCIATED_TOKEN_PROGRAM_ID, false, false, name = "associated_token_program"),
        )
        return BuiltInstruction(programId, accounts, data)
    }

    fun refundUnjoined(idl: NopeekIdl, params: RefundUnjoinedParams): BuiltInstruction {
        val programId = programIdFrom(idl)
        val escrow = if (params.escrow.isNotEmpty()) {
            params.escrow
        } else {
            val challenge = Base58.decode(params.challenge) ?: error("bad challenge pubkey")
            val mintBytes = Base58.decode(SolanaConfig.USDC_MINT)!!
            Base58.encode(ataAddress(challenge, mintBytes).address)
        }
        val accounts = listOf(
            AccountKey(params.challenge, false, true, name = "challenge"),
            AccountKey(params.creator, true, true, name = "creator"),
            AccountKey(escrow, false, true, name = "escrow"),
            AccountKey(params.creatorToken, false, true, name = "creator_token"),
            AccountKey(SolanaConfig.TOKEN_PROGRAM_ID, false, false, name = "token_program"),
            AccountKey(SolanaConfig.ASSOCIATED_TOKEN_PROGRAM_ID, false, false, name = "associated_token_program"),
        )
        return BuiltInstruction(programId, accounts, IdlParser.discriminator("refund_unjoined"))
    }

    fun splTokenTransfer(source: String, destination: String, authority: String, amount: Long): BuiltInstruction {
        val data = byteArrayOf(3) + le64(amount)
        val accounts = listOf(
            AccountKey(source, false, true, name = "source"),
            AccountKey(destination, false, true, name = "destination"),
            AccountKey(authority, true, false, name = "authority"),
        )
        return BuiltInstruction(SolanaConfig.TOKEN_PROGRAM_ID, accounts, data)
    }

    private fun le64(v: Long): ByteArray =
        ByteArray(8) { i -> (v ushr (8 * i)).toByte() }

    private fun le32(v: Int): ByteArray =
        ByteArray(4) { i -> (v ushr (8 * i)).toByte() }
}

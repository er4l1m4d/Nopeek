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
    /** v2: makes the pair re-duelable — the PDA seed carries it little-endian. */
    val nonce: Int,
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
    /** The reputation PDAs, derived per wallet with [Pda.reputationPda]. */
    val creatorReputation: String,
    val opponentReputation: String,
    /**
     * The sha-256 of each side's attested canonical bytes — the oracle's evidenceHash.
     * The chain refuses a PASS whose hash is zero (EvidenceZero), and the client mirrors
     * that below so the wallet is never asked to sign a transaction the chain rejects.
     */
    val creatorEvidenceHash: ByteArray,
    val opponentEvidenceHash: ByteArray,
    val escrow: String = "",
)

data class ResolveLockParams(
    val challenge: String,
    val oracle: String,
    val mint: String,
    val escrow: String,
    val treasury: String,
    val treasuryToken: String,
)

data class RefundUnjoinedParams(
    val challenge: String,
    val creator: String,
    val creatorToken: String,
    val escrow: String = "",
)

object ProgramInstructions {

    fun programIdFrom(idl: NopeekIdl): String = idl.address

    /**
     * The challenge PDA for a duel, v2: [b"challenge", creator, opponent, nonce.to_le_bytes()].
     * The nonce makes the same pair re-duelable — under v1 the second duel died with
     * AccountAlreadyInitialized because the address was already taken. Nonce 1 little-endian
     * is the byte sequence 01 00 00 00.
     */
    fun challengePda(creator: String, opponent: String, nonce: Int, programId: String): PdaAddress =
        Pda.findProgramAddress(
            listOf(
                "challenge".toByteArray(Charsets.UTF_8),
                Base58.decode(creator) ?: error("bad creator pubkey"),
                Base58.decode(opponent) ?: error("bad opponent pubkey"),
                le32(nonce),
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

    fun challengeEscrowAta(creator: String, opponent: String, mint: String, programId: String, nonce: Int): PdaAddress {
        val pda = challengePda(creator, opponent, nonce, programId)
        return ataAddress(pda.address, Base58.decode(mint) ?: error("bad mint"))
    }

    fun createChallenge(idl: NopeekIdl, params: CreateChallengeParams): BuiltInstruction {
        val programId = programIdFrom(idl)
        val pda = challengePda(params.creator, params.opponent, params.nonce, programId)
        val escrow = challengeEscrowAta(params.creator, params.opponent, params.mint, programId, params.nonce)

        val appBytes = params.targetApp.toByteArray(Charsets.UTF_8)
        require(appBytes.size <= 32) { "target app label too long" }
        val data = IdlParser.discriminator("create_challenge") +
            le64(params.stakeAmount) +
            le32(appBytes.size) + appBytes +
            le32(params.limitSeconds) +
            le32(params.windowSeconds) +
            le32(params.nonce)

        // The challenge account is the PDA itself: writable, but NOT a transaction signer.
        // PDAs have no keypair and sign via invoke_signed inside the program, so a signer
        // flag here would demand a wallet signature that can never exist (the tx would die
        // at signature verification). v1's challenge WAS a client keypair and so a signer;
        // v2 moved it to a PDA and this flag must not carry over.
        val accounts = listOf(
            AccountKey(Base58.encode(pda.address), isSigner = false, isWritable = true, name = "challenge"),
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
        require(params.creatorEvidenceHash.size == 32) { "creator evidence hash must be 32 bytes" }
        require(params.opponentEvidenceHash.size == 32) { "opponent evidence hash must be 32 bytes" }
        // The program refuses a PASS whose evidence hash is zero (EvidenceZero). Mirrored
        // here so the wallet is never asked to sign a transaction the chain will reject.
        if (params.creatorResult == 1) {
            require(!params.creatorEvidenceHash.all { it == 0.toByte() }) {
                "a PASS verdict requires a nonzero evidence hash"
            }
        }
        if (params.opponentResult == 1) {
            require(!params.opponentEvidenceHash.all { it == 0.toByte() }) {
                "a PASS verdict requires a nonzero evidence hash"
            }
        }
        val data = IdlParser.discriminator("settle_challenge") +
            byteArrayOf(params.creatorResult.toByte(), params.opponentResult.toByte()) +
            params.creatorEvidenceHash +
            params.opponentEvidenceHash
        val accounts = listOf(
            AccountKey(params.challenge, false, true, name = "challenge"),
            AccountKey(params.oracle, true, true, name = "oracle"),
            AccountKey(escrow, false, true, name = "escrow"),
            AccountKey(params.creatorToken, false, true, name = "creator_token"),
            AccountKey(params.opponentToken, false, true, name = "opponent_token"),
            AccountKey(params.creatorReputation, false, true, name = "creator_reputation"),
            AccountKey(params.opponentReputation, false, true, name = "opponent_reputation"),
            AccountKey(SolanaConfig.TOKEN_PROGRAM_ID, false, false, name = "token_program"),
            AccountKey(SolanaConfig.ASSOCIATED_TOKEN_PROGRAM_ID, false, false, name = "associated_token_program"),
            AccountKey(SolanaConfig.SYSTEM_PROGRAM_ID, false, false, name = "system_program"),
        )
        return BuiltInstruction(programId, accounts, data)
    }

    /**
     * v2: sweep a both-fail-locked escrow to the treasury once the lock hour has passed.
     * The treasury owner rides as a read-only account (Anchor's ATA init CPIs with the
     * authority's AccountInfo, so a bare pubkey constant will not do).
     */
    fun resolveLock(idl: NopeekIdl, params: ResolveLockParams): BuiltInstruction {
        val programId = programIdFrom(idl)
        val accounts = listOf(
            AccountKey(params.challenge, false, true, name = "challenge"),
            AccountKey(params.oracle, true, true, name = "oracle"),
            AccountKey(params.mint, false, false, name = "mint"),
            AccountKey(params.escrow, false, true, name = "escrow"),
            AccountKey(params.treasury, false, false, name = "treasury"),
            AccountKey(params.treasuryToken, false, true, name = "treasury_token"),
            AccountKey(SolanaConfig.TOKEN_PROGRAM_ID, false, false, name = "token_program"),
            AccountKey(SolanaConfig.ASSOCIATED_TOKEN_PROGRAM_ID, false, false, name = "associated_token_program"),
            AccountKey(SolanaConfig.SYSTEM_PROGRAM_ID, false, false, name = "system_program"),
        )
        return BuiltInstruction(programId, accounts, IdlParser.discriminator("resolve_lock"))
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

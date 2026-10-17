package com.nopeek.app.data.solana

import com.nopeek.app.data.wallet.Base58
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramInstructionsTest {

    private val idl by lazy { IdlParser.parse(SAMPLE_IDL) }

    private fun le64(v: Long): ByteArray =
        ByteArray(8) { i -> (v ushr (8 * i)).toByte() }

    private fun le32(v: Int): ByteArray =
        ByteArray(4) { i -> (v ushr (8 * i)).toByte() }

    @Test
    fun createChallenge_dataBytes_layoutMatchesIdlArgs() {
        val params = CreateChallengeParams(
            creator = "11111111111111111111111111111113",
            opponent = "11111111111111111111111111111114",
            stakeAmount = 5_000_000L,
            targetApp = "com.x",
            limitSeconds = 1800,
            windowSeconds = 86400,
            creatorTokenAccount = "11111111111111111111111111111115",
            nonce = 0,
        )
        val ix = ProgramInstructions.createChallenge(idl, params)
        val expected = IdlParser.discriminator("create_challenge") +
            le64(5_000_000L) +
            le32(5) + "com.x".toByteArray() +
            le32(1800) +
            le32(86400) +
            le32(0)
        assertArrayEquals(expected, ix.data)
    }

    @Test
    fun createChallenge_accounts_followIdlOrder() {
        val params = CreateChallengeParams(
            creator = "1".repeat(31) + "C",
            opponent = "1".repeat(31) + "D",
            stakeAmount = 1L,
            targetApp = "a",
            limitSeconds = 1,
            windowSeconds = 2,
            creatorTokenAccount = "1".repeat(31) + "T",
            nonce = 0,
        )
        val ix = ProgramInstructions.createChallenge(idl, params)
        assertEquals(
            idl.instruction("create_challenge")!!.accounts.map { it.name },
            ix.accounts.map { it.name },
        )
        // challenge PDA derived from seeds [challenge, creator, opponent, nonce LE]
        val creatorBytes = Base58.decode(params.creator)!!
        val opponentBytes = Base58.decode(params.opponent)!!
        val pda = Pda.findProgramAddress(
            listOf("challenge".toByteArray(), creatorBytes, opponentBytes, byteArrayOf(0, 0, 0, 0)),
            Base58.decode(ProgramInstructions.programIdFrom(idl))!!,
        )
        assertEquals(Base58.encode(pda.address), ix.accounts[0].pubkey)
        assertEquals(params.creator, ix.accounts[1].pubkey)
        assertTrue(ix.accounts[0].isWritable)
        assertFalse(ix.accounts[0].isSigner) // PDA — signs via invoke_signed on-chain, never via the wallet
        assertTrue(ix.accounts[1].isSigner)
    }

    @Test
    fun splTransfer_dataAndAccountSemantics() {
        val ix = ProgramInstructions.splTokenTransfer(
            source = "S".repeat(32),
            destination = "D".repeat(32),
            authority = "A".repeat(32),
            amount = 5_000_000L,
        )
        assertArrayEquals(byteArrayOf(3) + le64(5_000_000L), ix.data)
        assertEquals(SolanaConfig.TOKEN_PROGRAM_ID, ix.programId)
        assertEquals(listOf("source", "destination", "authority"), ix.accounts.map { it.name })
        assertEquals(true, ix.accounts[0].isWritable)
        assertEquals(true, ix.accounts[1].isWritable)
        assertEquals(true, ix.accounts[2].isSigner)
        assertEquals(false, ix.accounts[2].isWritable)
    }

    @Test
    fun settleChallenge_dataBytes_reportCodesAsU8() {
        val params = SettleChallengeParams(
            challenge = "11111111111111111111111111111116",
            oracle = "11111111111111111111111111111117",
            creatorToken = "11111111111111111111111111111118",
            opponentToken = "11111111111111111111111111111119",
            creatorResult = 1,
            opponentResult = 2,
            creatorReputation = "1111111111111111111111111111111C",
            opponentReputation = "1111111111111111111111111111111D",
            creatorEvidenceHash = creatorHash,
            opponentEvidenceHash = opponentHash,
        )
        val ix = ProgramInstructions.settleChallenge(idl, params)
        assertArrayEquals(
            IdlParser.discriminator("settle_challenge") + byteArrayOf(1, 2) + creatorHash + opponentHash,
            ix.data,
        )
        assertEquals(
            idl.instruction("settle_challenge")!!.accounts.map { it.name },
            ix.accounts.map { it.name },
        )
    }

    @Test
    fun settleEncodesTwoEvidenceHashesInOrder() {
        // The on-chain settlement must record WHICH evidence it settled from: the two
        // 32-byte hashes are the sha-256 of each duelist's attested canonical bytes, and a
        // swapped pair would attribute the wrong proof to the wrong side.
        val creatorHash = ByteArray(32) { (it + 1).toByte() }
        val opponentHash = ByteArray(32) { (it + 9).toByte() }
        val params = SettleChallengeParams(
            challenge = "11111111111111111111111111111116",
            oracle = "11111111111111111111111111111117",
            creatorToken = "11111111111111111111111111111118",
            opponentToken = "11111111111111111111111111111119",
            creatorResult = 1,
            opponentResult = 2,
            creatorReputation = "1111111111111111111111111111111C",
            opponentReputation = "1111111111111111111111111111111D",
            creatorEvidenceHash = creatorHash,
            opponentEvidenceHash = opponentHash,
        )
        val ix = ProgramInstructions.settleChallenge(idl, params)
        val body = ix.data.drop(8 + 2) // disc + the two u8 result codes
        assertEquals(64, body.size)
        assertArrayEquals(creatorHash, body.take(32).toByteArray())
        assertArrayEquals(opponentHash, body.drop(32).toByteArray())
        // And the reputations ride as writable, non-signing accounts after the tokens.
        val names = ix.accounts.map { it.name }
        assertEquals("creator_reputation", names[5])
        assertEquals("opponent_reputation", names[6])
        assertTrue(ix.accounts[5].isWritable && !ix.accounts[5].isSigner)
        assertTrue(ix.accounts[6].isWritable && !ix.accounts[6].isSigner)
        assertEquals("system_program", names[9])
    }

    @Test
    fun settleChallenge_passWithZeroEvidenceHash_throws() {
        // The program refuses a PASS whose evidence hash is zero (EvidenceZero). The
        // client mirrors it so the wallet is never asked to sign a transaction the chain
        // will reject - a rejected settle burns the user's time and the oracle's poll.
        val params = SettleChallengeParams(
            challenge = "11111111111111111111111111111116",
            oracle = "11111111111111111111111111111117",
            creatorToken = "11111111111111111111111111111118",
            opponentToken = "11111111111111111111111111111119",
            creatorResult = 1,
            opponentResult = 2,
            creatorReputation = "1111111111111111111111111111111C",
            opponentReputation = "1111111111111111111111111111111D",
            creatorEvidenceHash = ByteArray(32),
            opponentEvidenceHash = opponentHash,
        )
        try {
            ProgramInstructions.settleChallenge(idl, params)
            throw AssertionError("expected PASS with zero evidence hash to be refused")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun createChallenge_dataBytes_carryNonceAsLittleEndianU32() {
        val params = CreateChallengeParams(
            creator = "11111111111111111111111111111112",
            opponent = "11111111111111111111111111111113",
            stakeAmount = 5_000_000L,
            targetApp = "com.instagram.android",
            limitSeconds = 1800,
            windowSeconds = 3600,
            creatorTokenAccount = "11111111111111111111111111111114",
            nonce = 1,
        )
        val ix = ProgramInstructions.createChallenge(idl, params)
        val expected = IdlParser.discriminator("create_challenge") +
            le64(5_000_000L) +
            le32("com.instagram.android".length) + "com.instagram.android".toByteArray() +
            le32(1800) + le32(3600) + le32(1)
        assertArrayEquals(expected, ix.data)
        // The challenge PDA itself must be derived WITH the nonce seed (v2 re-duel).
        val handDerived = Pda.findProgramAddress(
            listOf(
                "challenge".toByteArray(),
                Base58.decode(params.creator)!!,
                Base58.decode(params.opponent)!!,
                byteArrayOf(1, 0, 0, 0),
            ),
            Base58.decode(idl.address)!!,
        )
        assertArrayEquals(handDerived.address, Base58.decode(ix.accounts[0].pubkey))
    }

    @Test
    fun resolveLockBuildsExpectedAccountList() {
        val params = ResolveLockParams(
            challenge = "11111111111111111111111111111116",
            oracle = "11111111111111111111111111111117",
            mint = "11111111111111111111111111111115",
            escrow = "1111111111111111111111111111111A",
            treasury = "1111111111111111111111111111111B",
            treasuryToken = "1111111111111111111111111111111C",
        )
        val ix = ProgramInstructions.resolveLock(idl, params)
        assertArrayEquals(IdlParser.discriminator("resolve_lock"), ix.data)
        assertEquals(
            listOf(
                "challenge", "oracle", "mint", "escrow", "treasury", "treasury_token",
                "token_program", "associated_token_program", "system_program",
            ),
            ix.accounts.map { it.name },
        )
        assertTrue(ix.accounts[1].isSigner) // oracle signs the sweep
        assertTrue(ix.accounts[5].isWritable) // treasury_token receives the funds
        assertFalse(ix.accounts[4].isWritable) // treasury owner account is read-only
    }

    private val creatorHash = ByteArray(32) { 0x44 }
    private val opponentHash = ByteArray(32) { 0x55 }

    private val SAMPLE_IDL = """
        {"address":"11111111111111111111111111111111",
         "instructions":[
           {"name":"create_challenge",
            "accounts":[
              {"name":"challenge","writable":true},
              {"name":"creator","writable":true,"signer":true},
              {"name":"opponent"},
              {"name":"mint"},
              {"name":"creator_token","writable":true},
              {"name":"escrow","writable":true},
              {"name":"token_program"},
              {"name":"associated_token_program"},
              {"name":"system_program"}],
            "args":[{"name":"stake_amount","type":"u64"},{"name":"target_app","type":"string"},
                    {"name":"limit_seconds","type":"u32"},{"name":"window_seconds","type":"u32"},
                    {"name":"nonce","type":"u32"}]},
           {"name":"join_challenge",
            "accounts":[{"name":"challenge","writable":true},
                        {"name":"opponent","writable":true,"signer":true},
                        {"name":"opponent_token","writable":true},
                        {"name":"escrow","writable":true},
                        {"name":"token_program"},
                        {"name":"associated_token_program"}],
            "args":[]},
           {"name":"settle_challenge",
            "accounts":[{"name":"challenge","writable":true},
                        {"name":"oracle","writable":true,"signer":true},
                        {"name":"escrow","writable":true},
                        {"name":"creator_token","writable":true},
                        {"name":"opponent_token","writable":true},
                        {"name":"creator_reputation","writable":true},
                        {"name":"opponent_reputation","writable":true},
                        {"name":"token_program"},
                        {"name":"associated_token_program"},
                        {"name":"system_program"}],
            "args":[{"name":"creator_result","type":"u8"},{"name":"opponent_result","type":"u8"},
                    {"name":"creator_evidence_hash","type":{"array":["u8",32]}},
                    {"name":"opponent_evidence_hash","type":{"array":["u8",32]}}]},
           {"name":"resolve_lock",
            "accounts":[{"name":"challenge","writable":true},
                        {"name":"oracle","writable":true,"signer":true},
                        {"name":"mint"},
                        {"name":"escrow","writable":true},
                        {"name":"treasury"},
                        {"name":"treasury_token","writable":true},
                        {"name":"token_program"},
                        {"name":"associated_token_program"},
                        {"name":"system_program"}],
            "args":[]},
           {"name":"refund_unjoined",
            "accounts":[{"name":"challenge","writable":true},
                        {"name":"creator","writable":true,"signer":true},
                        {"name":"escrow","writable":true},
                        {"name":"creator_token","writable":true},
                        {"name":"token_program"},
                        {"name":"associated_token_program"}],
            "args":[]}],
         "accounts":[{"name":"Challenge"},{"name":"Reputation"}],
         "types":[]}
    """.trimIndent()
}

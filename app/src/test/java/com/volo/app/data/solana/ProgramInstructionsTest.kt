package com.volo.app.data.solana

import com.volo.app.data.wallet.Base58
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
        )
        val ix = ProgramInstructions.createChallenge(idl, params)
        val expected = IdlParser.discriminator("create_challenge") +
            le64(5_000_000L) +
            le32(5) + "com.x".toByteArray() +
            le32(1800) +
            le32(86400)
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
        )
        val ix = ProgramInstructions.createChallenge(idl, params)
        assertEquals(
            idl.instruction("create_challenge")!!.accounts.map { it.name },
            ix.accounts.map { it.name },
        )
        // challenge PDA derived from seeds [challenge, creator, opponent]
        val creatorBytes = Base58.decode(params.creator)!!
        val opponentBytes = Base58.decode(params.opponent)!!
        val pda = Pda.findProgramAddress(
            listOf("challenge".toByteArray(), creatorBytes, opponentBytes),
            Base58.decode(ProgramInstructions.programIdFrom(idl))!!,
        )
        assertEquals(Base58.encode(pda.address), ix.accounts[0].pubkey)
        assertEquals(params.creator, ix.accounts[1].pubkey)
        assertTrue(ix.accounts[0].isWritable)
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
        )
        val ix = ProgramInstructions.settleChallenge(idl, params)
        assertArrayEquals(
            IdlParser.discriminator("settle_challenge") + byteArrayOf(1, 2),
            ix.data,
        )
        assertEquals(
            idl.instruction("settle_challenge")!!.accounts.map { it.name },
            ix.accounts.map { it.name },
        )
    }

    @Test
    fun joinChallenge_usesOpponentAsSigner() {
        val params = JoinChallengeParams(
            challenge = "1111111111111111111111111111111A",
            opponent = "1111111111111111111111111111111B",
            opponentToken = "1111111111111111111111111111111C",
        )
        val ix = ProgramInstructions.joinChallenge(idl, params)
        assertArrayEquals(IdlParser.discriminator("join_challenge"), ix.data)
        val opp = ix.accounts.first { it.name == "opponent" }
        assertTrue(opp.isSigner)
        assertTrue(opp.isWritable)
    }

    private val SAMPLE_IDL = """
        {"address":"11111111111111111111111111111111",
         "instructions":[
           {"name":"create_challenge",
            "accounts":[
              {"name":"challenge","writable":true,"signer":true},
              {"name":"creator","writable":true,"signer":true},
              {"name":"opponent"},
              {"name":"mint"},
              {"name":"creator_token","writable":true},
              {"name":"escrow","writable":true},
              {"name":"token_program"},
              {"name":"associated_token_program"},
              {"name":"system_program"}],
            "args":[{"name":"stake_amount","type":"u64"},{"name":"target_app","type":"string"},
                    {"name":"limit_seconds","type":"u32"},{"name":"window_seconds","type":"u32"}]},
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
                        {"name":"oracle","signer":true},
                        {"name":"escrow","writable":true},
                        {"name":"creator_token","writable":true},
                        {"name":"opponent_token","writable":true},
                        {"name":"token_program"},
                        {"name":"associated_token_program"}],
            "args":[{"name":"creator_result","type":"u8"},{"name":"opponent_result","type":"u8"}]},
           {"name":"refund_unjoined",
            "accounts":[{"name":"challenge","writable":true},
                        {"name":"creator","writable":true,"signer":true},
                        {"name":"escrow","writable":true},
                        {"name":"creator_token","writable":true},
                        {"name":"token_program"},
                        {"name":"associated_token_program"}],
            "args":[]}],
         "accounts":[{"name":"Challenge"}],
         "types":[]}
    """.trimIndent()
}

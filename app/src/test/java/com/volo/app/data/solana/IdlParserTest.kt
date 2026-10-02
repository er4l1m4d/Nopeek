package com.volo.app.data.solana

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IdlParserTest {

    private fun loadIdlJson(): String {
        val candidates = listOf(
            "src/main/assets/idl.json",
            "app/src/main/assets/idl.json",
            "../contracts/program/idl.json",
        )
        val file = candidates.map(::File).firstOrNull { it.exists() }
        assertNotNull("idl.json not found in $candidates", file)
        return file!!.readText()
    }

    private val idl by lazy { IdlParser.parse(loadIdlJson()) }

    @Test
    fun createChallengeDiscriminator_matchesKnownVector() {
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0xF4.toByte(), 0x2F, 0x01, 0x01, 0x0F, 0xAD.toByte(), 0xEF.toByte()),
            IdlParser.discriminator("create_challenge"),
        )
    }

    @Test
    fun computedDiscriminators_matchStoredIdlValues() {
        for (instruction in idl.instructions) {
            assertArrayEquals(
                "discriminator mismatch for ${instruction.name}",
                instruction.discriminator,
                IdlParser.discriminator(instruction.name),
            )
        }
    }

    @Test
    fun accountChallengeDiscriminator_matchesStoredIdlValue() {
        val challenge = idl.accounts["Challenge"]
        assertNotNull("Challenge account missing from IDL", challenge)
        assertArrayEquals(
            challenge!!.discriminator,
            IdlParser.accountDiscriminator("Challenge"),
        )
    }

    @Test
    fun instructions_parsedByName() {
        assertEquals(
            listOf("create_challenge", "join_challenge", "settle_challenge", "refund_unjoined"),
            idl.instructions.map { it.name },
        )
        assertNotNull(idl.instruction("settle_challenge"))
    }

    @Test
    fun createChallenge_argsInOrder() {
        val create = idl.instruction("create_challenge")
        assertNotNull(create)
        assertEquals(
            listOf("stake_amount", "target_app", "limit_seconds", "window_seconds"),
            create!!.args.map { it.name },
        )
        assertEquals(listOf("u64", "string", "u32", "u32"), create.args.map { it.type })
    }

    @Test
    fun createChallenge_accountsInOrder() {
        val create = idl.instruction("create_challenge")
        assertEquals(
            listOf(
                "challenge", "creator", "opponent", "mint", "creator_token", "escrow",
                "token_program", "associated_token_program", "system_program",
            ),
            create!!.accounts.map { it.name },
        )
        assertTrue(create.accounts.first().isWritable && create.accounts.first().isSigner)
    }

    @Test
    fun challengeStruct_fieldsParsedFromTypes() {
        val fields = idl.structFields("Challenge")
        assertEquals(
            listOf(
                "creator", "opponent", "token_mint", "stake_amount", "target_app",
                "limit_seconds", "window_seconds", "start_time", "end_time",
                "status", "result", "bump",
            ),
            fields.map { it.name },
        )
        assertEquals("publicKey", fields.first().type)
        assertEquals("u64", fields.first { it.name == "stake_amount" }.type)
        assertEquals("string", fields.first { it.name == "target_app" }.type)
        assertEquals("i64", fields.first { it.name == "start_time" }.type)
    }
}

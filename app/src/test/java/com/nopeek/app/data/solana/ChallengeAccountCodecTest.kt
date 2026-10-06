package com.nopeek.app.data.solana

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ChallengeAccountCodecTest {

    private val creator = ByteArray(32) { 0x11 }
    private val opponent = ByteArray(32) { 0x22 }
    private val mint = ByteArray(32) { 0x33 }

    /** Manual spec encoding: disc + 3 pubkeys + u64 + (u32+utf8) + u32 + u32 + i64 + i64 + 3x u8. */
    private fun encode(
        stakeAmount: Long,
        targetApp: String,
        limit: Int,
        window: Int,
        start: Long,
        end: Long,
        status: Int,
        result: Int,
        bump: Int,
    ): ByteArray {
        val app = targetApp.toByteArray(Charsets.UTF_8)
        return byteArrayOf() +
            IdlParser.accountDiscriminator("Challenge") +
            creator + opponent + mint +
            ByteArray(8) { i -> (stakeAmount ushr (8 * i)).toByte() } +
            ByteArray(4) { i -> (app.size ushr (8 * i)).toByte() } + app +
            ByteArray(4) { i -> (limit ushr (8 * i)).toByte() } +
            ByteArray(4) { i -> (window ushr (8 * i)).toByte() } +
            ByteArray(8) { i -> (start ushr (8 * i)).toByte() } +
            ByteArray(8) { i -> (end ushr (8 * i)).toByte() } +
            byteArrayOf(status.toByte(), result.toByte(), bump.toByte())
    }

    @Test
    fun decode_readsAllFieldsLittleEndian() {
        val bytes = encode(
            stakeAmount = 5_000_000L,
            targetApp = "com.instagram.android",
            limit = 1800,
            window = 86400,
            start = 1_700_000_000L,
            end = 1_700_086_400L,
            status = 1,
            result = 0,
            bump = 254,
        )
        val decoded = ChallengeAccountCodec.decode(bytes)!!
        assertArrayEquals(creator, decoded.creator)
        assertArrayEquals(opponent, decoded.opponent)
        assertArrayEquals(mint, decoded.tokenMint)
        assertEquals(5_000_000L, decoded.stakeAmount)
        assertEquals("com.instagram.android", decoded.targetApp)
        assertEquals(1800, decoded.limitSeconds)
        assertEquals(86400, decoded.windowSeconds)
        assertEquals(1_700_000_000L, decoded.startTime)
        assertEquals(1_700_086_400L, decoded.endTime)
        assertEquals(1, decoded.status)
        assertEquals(0, decoded.result)
        assertEquals(254, decoded.bump)
    }

    @Test
    fun decode_rejectsWrongDiscriminator() {
        val bytes = encode(1, "a", 1, 1, 0, 0, 0, 0, 255)
        bytes[0] = (bytes[0] + 1).toByte()
        assertEquals(null, ChallengeAccountCodec.decode(bytes))
    }

    @Test
    fun decode_rejectsTruncatedData() {
        val bytes = encode(1, "abc", 1, 1, 0, 0, 0, 0, 255)
        assertEquals(null, ChallengeAccountCodec.decode(bytes.copyOf(bytes.size - 4)))
    }

    @Test
    fun decode_statusConstants_matchProgram() {
        // 0 Created, 1 Active, 2 Settled, 3 Refunded, 4 BothFailLocked
        for (status in 0..4) {
            val d = ChallengeAccountCodec.decode(encode(1, "x", 1, 1, 0, 0, status, 0, 255))!!
            assertEquals(status, d.status)
        }
    }
}

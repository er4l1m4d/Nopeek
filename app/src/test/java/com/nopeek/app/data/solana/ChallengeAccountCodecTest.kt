package com.nopeek.app.data.solana

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChallengeAccountCodecTest {

    private val creator = ByteArray(32) { 0x11 }
    private val opponent = ByteArray(32) { 0x22 }
    private val mint = ByteArray(32) { 0x33 }
    private val creatorHash = ByteArray(32) { 0x44 }
    private val opponentHash = ByteArray(32) { 0x55 }

    /**
     * Manual spec encoding of the v2 account (contracts/program/src/lib.rs):
     * disc(8) + 3 pubkeys + u64 stake + (u32+utf8) app + u32 + u32 + i64 + i64
     * + status + result + bump + u32 nonce + [u8;32] + [u8;32] + i64 settled_at.
     * The caller may append trailing padding (the account's space is fixed at 251,
     * so a shorter app string leaves zero padding after the serialized struct).
     */
    private fun encode(
        stakeAmount: Long = 5_000_000L,
        targetApp: String = "com.instagram.android",
        limit: Int = 1800,
        window: Int = 86400,
        start: Long = 1_700_000_000L,
        end: Long = 1_700_086_400L,
        status: Int = 1,
        result: Int = 0,
        bump: Int = 254,
        nonce: Int = 0,
        creatorHash: ByteArray = ByteArray(32),
        opponentHash: ByteArray = ByteArray(32),
        settledAt: Long = 0L,
        padTo: Int = 0,
    ): ByteArray {
        val app = targetApp.toByteArray(Charsets.UTF_8)
        val body = byteArrayOf() +
            creator + opponent + mint +
            ByteArray(8) { i -> (stakeAmount ushr (8 * i)).toByte() } +
            ByteArray(4) { i -> (app.size ushr (8 * i)).toByte() } + app +
            ByteArray(4) { i -> (limit ushr (8 * i)).toByte() } +
            ByteArray(4) { i -> (window ushr (8 * i)).toByte() } +
            ByteArray(8) { i -> (start ushr (8 * i)).toByte() } +
            ByteArray(8) { i -> (end ushr (8 * i)).toByte() } +
            byteArrayOf(status.toByte(), result.toByte(), bump.toByte()) +
            ByteArray(4) { i -> (nonce ushr (8 * i)).toByte() } +
            creatorHash + opponentHash +
            ByteArray(8) { i -> (settledAt ushr (8 * i)).toByte() }
        val bytes = IdlParser.accountDiscriminator("Challenge") + body
        return if (padTo > bytes.size) bytes + ByteArray(padTo - bytes.size) else bytes
    }

    @Test
    fun decodesV2Layout() {
        val bytes = encode(
            nonce = 1,
            creatorHash = creatorHash,
            opponentHash = opponentHash,
            settledAt = 1_700_100_000L,
            status = 5,
            result = 2,
            padTo = 251,
        )
        val decoded = ChallengeAccountCodec.decodeV2Layout(bytes)!!
        assertArrayEquals(creator, decoded.creator)
        assertArrayEquals(opponent, decoded.opponent)
        assertArrayEquals(mint, decoded.tokenMint)
        assertEquals(5_000_000L, decoded.stakeAmount)
        assertEquals("com.instagram.android", decoded.targetApp)
        assertEquals(1800, decoded.limitSeconds)
        assertEquals(86400, decoded.windowSeconds)
        assertEquals(1_700_000_000L, decoded.startTime)
        assertEquals(1_700_086_400L, decoded.endTime)
        assertEquals(5, decoded.status)
        assertEquals(2, decoded.result)
        assertEquals(254, decoded.bump)
        // The v2-only fields, at the offsets the program's struct comment pins:
        // nonce is little-endian (1 -> 01 00 00 00), the hashes are raw 32-byte copies.
        assertEquals(1, decoded.nonce)
        assertArrayEquals(creatorHash, decoded.creatorEvidenceHash)
        assertArrayEquals(opponentHash, decoded.opponentEvidenceHash)
        assertEquals(1_700_100_000L, decoded.settledAt)
    }

    @Test
    fun decodeV2Layout_acceptsTrailingPadding() {
        // The account is allocated Challenge::LEN (251) regardless of the app string's
        // real length; a short app name leaves zeros after the serialized struct.
        val bytes = encode(targetApp = "app", padTo = 251)
        val decoded = ChallengeAccountCodec.decodeV2Layout(bytes)!!
        assertEquals("app", decoded.targetApp)
        assertEquals(0, decoded.nonce)
    }

    @Test
    fun decodeV2Layout_rejectsWrongDiscriminator() {
        val bytes = encode(padTo = 251)
        bytes[0] = (bytes[0] + 1).toByte()
        assertNull(ChallengeAccountCodec.decodeV2Layout(bytes))
    }

    @Test
    fun decodeV2Layout_rejectsTruncatedData() {
        val bytes = encode(creatorHash = creatorHash, opponentHash = opponentHash)
        assertNull(ChallengeAccountCodec.decodeV2Layout(bytes.copyOf(bytes.size - 4)))
    }

    @Test
    fun decodeV2Layout_statusConstants_matchProgram() {
        // 0 Created, 1 Active, 2 Settled, 3 Refunded, 4 BothFailLocked, 5 LockedResolved
        for (status in 0..5) {
            val d = ChallengeAccountCodec.decodeV2Layout(encode(status = status, padTo = 251))!!
            assertEquals(status, d.status)
        }
    }
}

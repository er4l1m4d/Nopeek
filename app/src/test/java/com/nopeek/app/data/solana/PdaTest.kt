package com.nopeek.app.data.solana

import com.nopeek.app.data.wallet.Base58
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdaTest {

    private val programId = Base58.decode("11111111111111111111111111111111")!!
    private val creator = ByteArray(32) { 1 }
    private val opponent = ByteArray(32) { 2 }
    private val seeds = listOf("challenge".toByteArray(), creator, opponent)

    @Test
    fun rfc8032PublicKey_isOnCurve() {
        // RFC 8032 test vector 7.1 public key (secret 9d61b19d...).
        val pub = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        assertTrue(Pda.isOnCurve(pub))
    }

    @Test
    fun randomBytes_typicallyOffCurveOrRejectedConsistently() {
        val notAPoint = ByteArray(32) { 0xFF.toByte() }
        assertFalse(Pda.isOnCurve(notAPoint))
        // y >= p must be rejected (all-0xFF > 2^255-19 when interpreted LE? top bit set => rejected)
        assertTrue(Pda.isOnCurve(hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")))
    }

    @Test
    fun findProgramAddress_isDeterministic() {
        val a = Pda.findProgramAddress(seeds, programId)
        val b = Pda.findProgramAddress(seeds.map { it.copyOf() }, programId)
        assertArrayEquals(a.address, b.address)
        assertEquals(a.bump, b.bump)
    }

    @Test
    fun findProgramAddress_returnsOffCurveAddressAtValidBump() {
        val pda = Pda.findProgramAddress(seeds, programId)
        assertEquals(32, pda.address.size)
        assertFalse("derived address must be off-curve", Pda.isOnCurve(pda.address))
        // The preimage with the returned bump must hash to the address (spec: seeds + bump +
        // programId + "ProgramDerivedAddress", each seed length-prefixed).
        assertArrayEquals(pda.address, Pda.tryDerive(seeds, programId, pda.bump))
    }

    @Test
    fun findProgramAddress_allHigherBumps_areOnCurve() {
        // The loop walks bump 255 -> 0, so every higher bump must have produced an
        // on-curve (invalid) point; otherwise the chosen bump would be wrong.
        val pda = Pda.findProgramAddress(seeds, programId)
        for (bump in pda.bump + 1..255) {
            val candidate = Pda.tryDerive(seeds, programId, bump)
            assertTrue("bump $bump should have been rejected (on-curve)", Pda.isOnCurve(candidate))
        }
    }

    @Test
    fun seedLengthLimits_areEnforced() {
        // Spec: each seed is prefixed with its u8 length; max seed length is 32.
        try {
            Pda.findProgramAddress(listOf(ByteArray(33)), programId)
            throw AssertionError("expected IllegalArgumentException for 33-byte seed")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun challengePdaIncludesNonceAsLittleEndianBytes() {
        // v2: the challenge PDA is [b"challenge", creator, opponent, nonce.to_le_bytes()].
        // Same pair + different nonce MUST give different addresses — that is the whole
        // point: under v1 a second duel between the same pair died with
        // AccountAlreadyInitialized because the address was already taken.
        val p0 = ProgramInstructions.challengePda(creatorB58, opponentB58, nonce = 0, programId = programIdB58)
        val p1 = ProgramInstructions.challengePda(creatorB58, opponentB58, nonce = 1, programId = programIdB58)
        assertFalse("nonce must change the address (v2 re-duel)", p0.address.contentEquals(p1.address))

        // The encoding is little-endian: nonce 1 is the seed byte sequence 01 00 00 00,
        // not 00 00 00 01. Pinned against a hand-derived address with that exact seed.
        val handDerived = Pda.findProgramAddress(
            listOf("challenge".toByteArray(), creator, opponent, byteArrayOf(1, 0, 0, 0)),
            programId,
        )
        assertArrayEquals(handDerived.address, p1.address)
        assertEquals(handDerived.bump, p1.bump)
    }

    @Test
    fun reputationPdaIsStablePerWalletAndIndependentOfPair() {
        val a1 = Pda.reputationPda(walletB58, programIdB58)
        val a2 = Pda.reputationPda(walletB58, programIdB58)
        assertArrayEquals(a1.address, a2.address)

        val other = Pda.reputationPda(otherWalletB58, programIdB58)
        assertFalse("reputation is per wallet, not per pair", a1.address.contentEquals(other.address))

        // Seed spelling: [b"reputation", wallet] — pinned against the hand-derived form so
        // a rename on one side cannot drift from the program's REPUTATION_SEED.
        val handDerived = Pda.findProgramAddress(
            listOf("reputation".toByteArray(), Base58.decode(walletB58)!!),
            programId,
        )
        assertArrayEquals(handDerived.address, a1.address)
    }

    private val programIdB58 = "11111111111111111111111111111111"
    private val creatorB58 = Base58.encode(creator)
    private val opponentB58 = Base58.encode(opponent)
    private val walletB58 = Base58.encode(ByteArray(32) { 7 })
    private val otherWalletB58 = Base58.encode(ByteArray(32) { 8 })

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

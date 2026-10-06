package com.nopeek.app.data.challenge

import com.nopeek.app.data.wallet.Base58
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChallengeValidationTest {

    private fun draft(
        target: String? = "com.instagram.android",
        limitMinutes: Int? = 30,
        stake: Long = 5_000_000L,
        opponent: String = "11111111111111111111111111111111",
        windowSeconds: Long? = 86_400L
    ) = ChallengeDraft(target, limitMinutes, stake, opponent, windowSeconds)

    // --- Base58 decode ---

    @Test
    fun base58_decode_roundtrip() {
        val bytes = ByteArray(64) { it.toByte() }
        val encoded = Base58.encode(bytes)
        assertArrayEquals(bytes, Base58.decode(encoded))
    }

    @Test
    fun base58_decode_invalid_chars_returns_null() {
        assertNull(Base58.decode("0OIl"))
        assertNull(Base58.decode("abc def"))
        assertNull(Base58.decode(""))
    }

    // --- Opponent parsing ---

    @Test
    fun opponent_valid_32byte_base58_accepted() {
        val pubkey = "11111111111111111111111111111111" // 32 zero bytes
        assertEquals(pubkey, parseOpponent(pubkey))
    }

    @Test
    fun opponent_wrong_length_rejected() {
        val short = Base58.encode(ByteArray(31) { 1 })
        assertNull(parseOpponent(short))
    }

    @Test
    fun opponent_garbage_rejected() {
        assertNull(parseOpponent("not a wallet!"))
        assertNull(parseOpponent("   "))
    }

    @Test
    fun opponent_skr_domain_resolves_deterministically() {
        val resolved = parseOpponent("alice.skr")
        assertNotNull(resolved)
        assertEquals(resolved, parseOpponent("alice.skr"))
        assertNotEquals("alice.skr", resolved)
        assertNotNull(Base58.decode(resolved!!))
        assertEquals(32, Base58.decode(resolved)!!.size)
    }

    @Test
    fun opponent_skr_without_name_rejected() {
        assertNull(parseOpponent(".skr"))
    }

    // --- Full draft validation ---

    @Test
    fun valid_draft_has_no_errors() {
        assertEquals(emptyList<ChallengeInputError>(), validateChallengeInput(draft()))
    }

    @Test
    fun missing_target_app_flagged() {
        val errors = validateChallengeInput(draft(target = null))
        assertEquals(listOf(ChallengeInputError.APP_REQUIRED), errors)
    }

    @Test
    fun missing_limit_flagged() {
        val errors = validateChallengeInput(draft(limitMinutes = null))
        assertEquals(listOf(ChallengeInputError.LIMIT_REQUIRED), errors)
    }

    @Test
    fun zero_limit_flagged() {
        val errors = validateChallengeInput(draft(limitMinutes = 0))
        assertEquals(listOf(ChallengeInputError.LIMIT_POSITIVE), errors)
    }

    @Test
    fun zero_stake_flagged() {
        val errors = validateChallengeInput(draft(stake = 0L))
        assertEquals(listOf(ChallengeInputError.STAKE_POSITIVE), errors)
    }

    @Test
    fun stake_over_balance_flagged() {
        val errors = validateChallengeInput(draft(), walletBalanceRaw = 1_000_000L)
        assertEquals(listOf(ChallengeInputError.STAKE_EXCEEDS_BALANCE), errors)
    }

    @Test
    fun stake_within_balance_ok() {
        val errors = validateChallengeInput(draft(stake = 4_000_000L), walletBalanceRaw = 4_000_000L)
        assertEquals(emptyList<ChallengeInputError>(), errors)
    }

    @Test
    fun invalid_opponent_flagged() {
        val errors = validateChallengeInput(draft(opponent = "xyz!"))
        assertEquals(listOf(ChallengeInputError.OPPONENT_INVALID), errors)
    }

    @Test
    fun missing_window_flagged() {
        val errors = validateChallengeInput(draft(windowSeconds = null))
        assertEquals(listOf(ChallengeInputError.WINDOW_REQUIRED), errors)
    }

    @Test
    fun multiple_errors_reported_together() {
        val errors = validateChallengeInput(
            draft(target = null, stake = 0L, opponent = "bad!", windowSeconds = null)
        )
        assertEquals(4, errors.size)
    }
}

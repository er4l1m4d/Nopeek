package com.volo.app.data.challenge

import com.volo.app.data.identity.MockSeekerProvider
import com.volo.app.data.wallet.Base58
import java.security.MessageDigest

data class ChallengeDraft(
    val targetPackage: String?,
    val limitMinutes: Int?,
    val stakeAmountRaw: Long,
    val opponentInput: String,
    val windowSeconds: Long?
)

enum class ChallengeInputError {
    APP_REQUIRED,
    LIMIT_REQUIRED,
    LIMIT_POSITIVE,
    STAKE_POSITIVE,
    STAKE_EXCEEDS_BALANCE,
    OPPONENT_INVALID,
    WINDOW_REQUIRED
}

fun ChallengeInputError.message(): String = when (this) {
    ChallengeInputError.APP_REQUIRED -> "Choose a target app."
    ChallengeInputError.LIMIT_REQUIRED -> "Set a daily limit."
    ChallengeInputError.LIMIT_POSITIVE -> "Daily limit must be greater than zero."
    ChallengeInputError.STAKE_POSITIVE -> "Stake must be greater than zero."
    ChallengeInputError.STAKE_EXCEEDS_BALANCE -> "Stake exceeds your wallet balance."
    ChallengeInputError.OPPONENT_INVALID -> "Enter a valid wallet address or .skr handle."
    ChallengeInputError.WINDOW_REQUIRED -> "Choose a duel window."
}

/**
 * Opponent input is either a raw base58 Solana pubkey (32 bytes) or a `.skr` handle.
 * Known demo handles resolve through MockSeekerProvider (plan 10.1); unknown handles
 * fall back to the deterministic SHA-256 -> base58 mock from Phase 2 validation.
 */
fun parseOpponent(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.endsWith(".skr", ignoreCase = true)) {
        MockSeekerProvider.resolveDomain(trimmed)?.let { return it }
        val name = trimmed.dropLast(".skr".length)
        if (name.isEmpty()) return null
        return mockResolveSkr(trimmed)
    }
    val bytes = Base58.decode(trimmed)
    return if (bytes != null && bytes.size == 32) trimmed else null
}

fun mockResolveSkr(handle: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(handle.lowercase().toByteArray(Charsets.UTF_8))
    return Base58.encode(hash)
}

fun validateChallengeInput(
    draft: ChallengeDraft,
    walletBalanceRaw: Long? = null
): List<ChallengeInputError> {
    val errors = mutableListOf<ChallengeInputError>()

    if (draft.targetPackage.isNullOrBlank()) errors.add(ChallengeInputError.APP_REQUIRED)

    val limit = draft.limitMinutes
    when {
        limit == null -> errors.add(ChallengeInputError.LIMIT_REQUIRED)
        limit <= 0 -> errors.add(ChallengeInputError.LIMIT_POSITIVE)
    }

    if (draft.stakeAmountRaw <= 0L) {
        errors.add(ChallengeInputError.STAKE_POSITIVE)
    } else if (walletBalanceRaw != null && draft.stakeAmountRaw > walletBalanceRaw) {
        errors.add(ChallengeInputError.STAKE_EXCEEDS_BALANCE)
    }

    if (parseOpponent(draft.opponentInput) == null) {
        errors.add(ChallengeInputError.OPPONENT_INVALID)
    }

    val window = draft.windowSeconds
    if (window == null || window <= 0L) errors.add(ChallengeInputError.WINDOW_REQUIRED)

    return errors
}

package com.volo.app.data

/**
 * Local domain model for a challenge. `challengePubkey` is null until the
 * on-chain create transaction lands (Phase 7.2); `localStatus` tracks UI state
 * independent of on-chain status.
 */
data class Challenge(
    val id: String,
    val challengePubkey: String? = null,
    val creatorWallet: String? = null,
    val opponentWallet: String,
    val targetApp: String,
    val limitSeconds: Int,
    val stakeAmountRaw: Long,
    val windowSeconds: Long,
    val startTime: Long,
    val endTime: Long,
    val localStatus: String = "CREATED"
)

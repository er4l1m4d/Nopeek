package com.nopeek.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nopeek.app.data.Challenge

@Entity(
    tableName = "challenges",
    indices = [Index(value = ["challengePubkey"], unique = true)]
)
data class ChallengeEntity(
    @PrimaryKey val id: String,
    val challengePubkey: String?,
    val creatorWallet: String?,
    val opponentWallet: String,
    val targetApp: String,
    val limitSeconds: Int,
    val stakeAmountRaw: Long,
    val windowSeconds: Long,
    val startTime: Long,
    val endTime: Long,
    val localStatus: String,
    /** v2: the duel nonce in the challenge PDA's seed; 0 for v1-created rows. */
    val nonce: Int = 0,
)

fun Challenge.toEntity(): ChallengeEntity = ChallengeEntity(
    id = id,
    challengePubkey = challengePubkey,
    creatorWallet = creatorWallet,
    opponentWallet = opponentWallet,
    targetApp = targetApp,
    limitSeconds = limitSeconds,
    stakeAmountRaw = stakeAmountRaw,
    windowSeconds = windowSeconds,
    startTime = startTime,
    endTime = endTime,
    localStatus = localStatus,
    nonce = nonce,
)

fun ChallengeEntity.toModel(): Challenge = Challenge(
    id = id,
    challengePubkey = challengePubkey,
    creatorWallet = creatorWallet,
    opponentWallet = opponentWallet,
    targetApp = targetApp,
    limitSeconds = limitSeconds,
    stakeAmountRaw = stakeAmountRaw,
    windowSeconds = windowSeconds,
    startTime = startTime,
    endTime = endTime,
    localStatus = localStatus,
)

package com.nopeek.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(challenge: ChallengeEntity)

    @Query("UPDATE challenges SET localStatus = :status WHERE challengePubkey = :pubkey")
    suspend fun updateStatus(pubkey: String, status: String)

    @Query("SELECT * FROM challenges WHERE localStatus IN ('CREATED', 'ACTIVE') ORDER BY startTime DESC")
    suspend fun getActiveChallenges(): List<ChallengeEntity>

    // Screen query also shows tamper-flagged and both-fail-locked rows; the polling
    // query deliberately excludes PENDING_VERIFICATION / BOTH_FAIL_LOCKED so the
    // flag or lock is not overwritten by a chain status.
    @Query("SELECT * FROM challenges WHERE localStatus IN ('CREATED', 'ACTIVE', 'PENDING_VERIFICATION', 'BOTH_FAIL_LOCKED') ORDER BY startTime DESC")
    fun observeActive(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges ORDER BY startTime DESC")
    suspend fun getAll(): List<ChallengeEntity>
}

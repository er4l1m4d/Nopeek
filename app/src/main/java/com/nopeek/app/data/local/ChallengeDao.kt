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

    // The eager recovery path's query. PENDING_VERIFICATION is included ON PURPOSE here
    // (ReportRecovery re-reports a locally-suspected window so the oracle's hardware check
    // can clear it) while the chain poller above deliberately excludes it - that flag must
    // not be overwritten by a chain status, but it also must not be invisible to recovery.
    @Query("SELECT * FROM challenges WHERE localStatus IN ('CREATED', 'ACTIVE', 'PENDING_VERIFICATION') ORDER BY startTime DESC")
    suspend fun getRecoverableChallenges(): List<ChallengeEntity>

    // Screen query also shows tamper-flagged and both-fail-locked rows; the polling
    // query deliberately excludes PENDING_VERIFICATION / BOTH_FAIL_LOCKED so the
    // flag or lock is not overwritten by a chain status.
    @Query("SELECT * FROM challenges WHERE localStatus IN ('CREATED', 'ACTIVE', 'PENDING_VERIFICATION', 'BOTH_FAIL_LOCKED') ORDER BY startTime DESC")
    fun observeActive(): Flow<List<ChallengeEntity>>

    // A duel is addressable by its on-chain pubkey once signed, and by its row id before
    // that (the demo seed is created before any wallet signs, so its challengePubkey is
    // null and NavGraph navigates with the id). One query serves both spellings.
    @Query("SELECT * FROM challenges WHERE challengePubkey = :pubkey OR id = :pubkey LIMIT 1")
    fun observeByPubkey(pubkey: String): Flow<ChallengeEntity?>

    @Query("SELECT * FROM challenges ORDER BY startTime DESC")
    suspend fun getAll(): List<ChallengeEntity>
}

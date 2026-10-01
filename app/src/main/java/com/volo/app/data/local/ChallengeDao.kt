package com.volo.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChallengeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(challenge: ChallengeEntity)

    @Query("UPDATE challenges SET localStatus = :status WHERE challengePubkey = :pubkey")
    suspend fun updateStatus(pubkey: String, status: String)

    @Query("SELECT * FROM challenges WHERE localStatus IN ('CREATED', 'ACTIVE') ORDER BY startTime DESC")
    suspend fun getActiveChallenges(): List<ChallengeEntity>

    @Query("SELECT * FROM challenges ORDER BY startTime DESC")
    suspend fun getAll(): List<ChallengeEntity>
}

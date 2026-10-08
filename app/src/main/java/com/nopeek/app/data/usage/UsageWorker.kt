package com.nopeek.app.data.usage

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nopeek.app.data.UsageReport
import com.nopeek.app.data.VerificationStatus
import com.nopeek.app.data.verify.CommitmentVerifier
import com.nopeek.app.data.verify.LocalRuleCommitmentVerifier
import java.util.concurrent.TimeUnit

object UsageSyncParamsReader {

    const val KEY_CHALLENGE = "challenge_pubkey"
    const val KEY_WALLET = "wallet_pubkey"
    const val KEY_TARGET_PACKAGE = "target_package"
    const val KEY_WINDOW_START = "window_start"
    const val KEY_WINDOW_END = "window_end"
    const val KEY_LIMIT_SECONDS = "limit_seconds"
    const val KEY_GRACE_SECONDS = "grace_seconds"

    fun from(map: Map<String, Any?>): UsageSyncParams? {
        val challenge = map[KEY_CHALLENGE] as? String ?: return null
        val wallet = map[KEY_WALLET] as? String ?: return null
        val target = map[KEY_TARGET_PACKAGE] as? String ?: return null
        val windowStart = (map[KEY_WINDOW_START] as? Long) ?: return null
        val windowEnd = (map[KEY_WINDOW_END] as? Long) ?: return null
        val limit = (map[KEY_LIMIT_SECONDS] as? Int) ?: return null
        val grace = (map[KEY_GRACE_SECONDS] as? Int) ?: 60
        return UsageSyncParams(
            challengePubkey = challenge,
            walletPubkey = wallet,
            targetPackage = target,
            windowStart = windowStart,
            windowEnd = windowEnd,
            limitSeconds = limit,
            graceSeconds = grace
        )
    }

    fun toData(params: UsageSyncParams): Data = Data.Builder()
        .putString(KEY_CHALLENGE, params.challengePubkey)
        .putString(KEY_WALLET, params.walletPubkey)
        .putString(KEY_TARGET_PACKAGE, params.targetPackage)
        .putLong(KEY_WINDOW_START, params.windowStart)
        .putLong(KEY_WINDOW_END, params.windowEnd)
        .putInt(KEY_LIMIT_SECONDS, params.limitSeconds)
        .putInt(KEY_GRACE_SECONDS, params.graceSeconds)
        .build()
}

internal class UsageWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    private val verifier: CommitmentVerifier = LocalRuleCommitmentVerifier()

    private val collector = UsageEventCollector(applicationContext)

    override fun doWork(): Result {
        val parsed = UsageSyncParamsReader.from(inputData.keyValueMap)
            ?: return Result.failure()

        // One read of the event stream, three figures from it. Used-seconds alone is a
        // number the app made up; paired with the device-wide active span it becomes a
        // claim the oracle can disprove, and the digest pins the exact event set the
        // numbers came from so the same bytes are what gets signed and hashed.
        val eventSet = collector.collect(parsed.windowStart, parsed.windowEnd)
        val usedSeconds = collector.usedSecondsFor(eventSet, parsed.targetPackage)
        val clock = readClockPair()

        val signals = detectTamperSignals(
            permissionRevoked = !UsagePermissionHelper.isGranted(applicationContext),
            clockJumped = clock.backwardJump,
            reportTime = System.currentTimeMillis(),
            windowEnd = parsed.windowEnd,
            graceSeconds = parsed.graceSeconds,
            clockJumpedForward = clock.forwardJump,
        )

        val report = UsageReport(
            challengePubkey = parsed.challengePubkey,
            walletPubkey = parsed.walletPubkey,
            usedSeconds = usedSeconds,
            windowStart = parsed.windowStart,
            windowEnd = parsed.windowEnd,
            tamperSignals = signals,
            submittedAt = System.currentTimeMillis(),
            deviceActiveSeconds = eventSet.deviceActiveSeconds,
            eventCount = eventSet.eventCount,
            eventSetDigestHex = eventSet.eventSetDigestHex,
        )

        // Plan 8.1 Step 3: tamper signals present -> mark the challenge for
        // verification. Decided through CommitmentVerifier rather than by reading
        // the signals directly, so there is exactly one place in the app that
        // turns evidence into a verdict — Task 15 swaps the binding, not this call
        // site. Behavior-identical: CommitmentVerifierTest pins verify()==UNVERIFIED
        // to flagForVerification for every combination of the three signals.
        if (verifier.verify(report, parsed.limitSeconds) == VerificationStatus.UNVERIFIED) {
            try {
                kotlinx.coroutines.runBlocking {
                    com.nopeek.app.data.local.AppDatabase.getInstance(applicationContext)
                        .challengeDao()
                        .updateStatus(parsed.challengePubkey, "PENDING_VERIFICATION")
                }
            } catch (_: Exception) {
                // Room unavailable in this run — the next periodic cycle retries.
            }
        }

        postReport(report)
        return Result.success()
    }

    /**
     * Wall clock and monotonic clock, read together and compared with the previous read.
     *
     * Both directions are reported separately because they are different tampering acts:
     * backward is the classic "reset the clock to end the window", forward is what makes a
     * back-dated session or an early settlement possible. The pair is stored so the next
     * run can measure each clock's own gain; monotonic time is the yardstick the user
     * cannot move.
     */
    private fun readClockPair(): ClockReading {
        val prefs: SharedPreferences =
            applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wallNow = System.currentTimeMillis()
        val elapsedNow = android.os.SystemClock.elapsedRealtime()
        val lastWall = prefs.getLong(PREF_LAST_WALL_CLOCK, wallNow)
        val lastElapsed = prefs.getLong(PREF_LAST_ELAPSED_REALTIME, elapsedNow)
        prefs.edit()
            .putLong(PREF_LAST_WALL_CLOCK, wallNow)
            .putLong(PREF_LAST_ELAPSED_REALTIME, elapsedNow)
            .apply()

        return ClockReading(
            backwardJump = lastWall - wallNow > CLOCK_JUMP_THRESHOLD_MS,
            forwardJump = isClockJumpedForward(
                lastWallMs = lastWall,
                lastElapsedRealtimeMs = lastElapsed,
                wallNowMs = wallNow,
                nowElapsedRealtimeMs = elapsedNow,
                allowanceMs = CLOCK_JUMP_THRESHOLD_MS,
            ),
        )
    }

    private class ClockReading(val backwardJump: Boolean, val forwardJump: Boolean)

    companion object {
        private const val PREFS_NAME = "nopeek_usage"
        private const val PREF_LAST_WALL_CLOCK = "last_wall_clock"
        private const val PREF_LAST_ELAPSED_REALTIME = "last_elapsed_realtime"
        private const val CLOCK_JUMP_THRESHOLD_MS = 5 * 60 * 1000L

        /**
         * Plan 3.2 Step 4: periodic 15-minute sync while the network is connected.
         * Per ledger Row 2, challenge params travel as WorkManager input data until Room lands (4.2).
         */
        fun scheduleUsageSync(context: Context, params: UsageSyncParams) {
            val request = PeriodicWorkRequest.Builder(
                UsageWorker::class.java,
                15, TimeUnit.MINUTES
            )
                .setInputData(UsageSyncParamsReader.toData(params))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "nopeek_usage_sync_${params.challengePubkey}",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelUsageSync(context: Context, challengePubkey: String) {
            WorkManager.getInstance(context)
                .cancelUniqueWork("nopeek_usage_sync_$challengePubkey")
        }
    }
}

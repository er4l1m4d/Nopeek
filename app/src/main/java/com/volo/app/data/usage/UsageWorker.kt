package com.volo.app.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
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
import com.volo.app.data.UsageReport
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

    override fun doWork(): Result {
        val parsed = UsageSyncParamsReader.from(inputData.keyValueMap)
            ?: return Result.failure()

        val events = queryUsageEvents(parsed.windowStart, parsed.windowEnd)
        val usedSeconds = aggregateUsage(
            events = events,
            windowStart = parsed.windowStart,
            windowEnd = parsed.windowEnd,
            targetPackage = parsed.targetPackage
        )

        val signals = detectTamperSignals(
            permissionRevoked = !UsagePermissionHelper.isGranted(applicationContext),
            clockJumped = detectClockChange(applicationContext),
            reportTime = System.currentTimeMillis(),
            windowEnd = parsed.windowEnd,
            graceSeconds = parsed.graceSeconds
        )

        val report = UsageReport(
            challengePubkey = parsed.challengePubkey,
            walletPubkey = parsed.walletPubkey,
            usedSeconds = usedSeconds,
            windowStart = parsed.windowStart,
            windowEnd = parsed.windowEnd,
            tamperSignals = signals,
            submittedAt = System.currentTimeMillis()
        )

        postReport(report)
        return Result.success()
    }

    private fun queryUsageEvents(windowStart: Long, windowEnd: Long): List<UsageEvent> {
        val statsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val results = mutableListOf<UsageEvent>()
        val iterator = statsManager.queryEvents(windowStart, windowEnd)
        val event = UsageEvents.Event()
        while (iterator.hasNextEvent()) {
            iterator.getNextEvent(event)
            when (event.eventType) {
                UsageEventType.MOVE_TO_FOREGROUND -> results.add(
                    UsageEvent(event.packageName, event.eventType, event.timeStamp)
                )
                UsageEventType.MOVE_TO_BACKGROUND -> results.add(
                    UsageEvent(event.packageName, event.eventType, event.timeStamp)
                )
            }
        }
        return results
    }

    private fun detectClockChange(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(PREF_LAST_WALL_CLOCK, now)
        prefs.edit().putLong(PREF_LAST_WALL_CLOCK, now).apply()
        return last - now > CLOCK_JUMP_THRESHOLD_MS
    }

    companion object {
        private const val PREFS_NAME = "volo_usage"
        private const val PREF_LAST_WALL_CLOCK = "last_wall_clock"
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
                "volo_usage_sync_${params.challengePubkey}",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelUsageSync(context: Context, challengePubkey: String) {
            WorkManager.getInstance(context)
                .cancelUniqueWork("volo_usage_sync_$challengePubkey")
        }
    }
}

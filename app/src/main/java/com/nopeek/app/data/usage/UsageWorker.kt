package com.nopeek.app.data.usage

import android.content.Context
import android.content.SharedPreferences
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nopeek.app.data.UsageReport
import com.nopeek.app.data.VerificationStatus
import com.nopeek.app.data.attest.ReportOutcome
import com.nopeek.app.data.verify.selectVerifier
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

    private val collector = UsageEventCollector(applicationContext)

    override fun doWork(): Result {
        val parsed = UsageSyncParamsReader.from(inputData.keyValueMap)
            ?: return Result.failure()

        // One read of the event stream, three figures from it. Used-seconds alone is a
        // number the app made up; paired with the device-wide active span it becomes a
        // claim the oracle can disprove. The digest commits the exact window's event set
        // these came from, and the same report object is what gets canonicalised, signed
        // and submitted below — measuring once and signing once is what keeps the verdict
        // the app shows and the evidence the chain records describing one window.
        val eventSet = collector.collect(parsed.windowStart, parsed.windowEnd)
        val usedSeconds = collector.usedSecondsFor(eventSet, parsed.targetPackage)
        val clock = readClockPair(parsed.windowStart, parsed.windowEnd)

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

        // Plan 8.1 Step 3: tamper signals -> mark the challenge for verification.
        // Task 15: the verifier is NOT hard-bound in the worker any more; the
        // outcome of submission picks the authority and stamps the report with
        // the oracle's verdict (when the oracle answered). The verdict decision
        // itself — verify()==UNVERIFIED -> PENDING_VERIFICATION — is unchanged;
        // only which source it consults differs.
        // Task 13: the signed, attested submission replaces the legacy body that
        // carried only the app's own number. The oracle now refuses a submission
        // with no evidence at all (EVIDENCE_MISSING), so this call is the
        // difference between a report that settles and a report that is filed and
        // ignored.
        val outcome = ReportScheduler.submitReport(
            applicationContext, report, parsed.targetPackage, parsed.graceSeconds,
        )
        // The oracle's answer, when present, is the authority; otherwise the local
        // rule is the offline fallback. selectVerifier stamps verified/rejectReason
        // and returns the verifier to consult, so this is the ONE branch in the
        // worker that reasons about "who decides this".
        val (chosenVerifier, decidedReport) = selectVerifier(outcome, report)

        // PENDING_VERIFICATION is keyed to UNVERIFIED, which is what both verifiers
        // agree to when the evidence is not fully accepted: tamper signals, a
        // transport failure (no verdict yet), or a rejected submission.
        if (chosenVerifier.verify(decidedReport, parsed.limitSeconds) == VerificationStatus.UNVERIFIED) {
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

        // A refusal is a verdict: the oracle answered, and re-running cannot
        // un-answer it. Only UNREACHABLE (transport failure) is retried —
        // reporting a transport failure as success would strand the window with
        // no report and no retry.
        return when (outcome) {
            is ReportOutcome.TransportFailed -> Result.retry()
            else -> Result.success()
        }
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
    private fun readClockPair(windowStartMs: Long, windowEndMs: Long): ClockReading {
        val prefs: SharedPreferences =
            applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wallNow = System.currentTimeMillis()
        val elapsedNow = android.os.SystemClock.elapsedRealtime()
        // 0 means "no previous observation", which is why the defaults are not
        // wallNow/elapsedNow: seeding them from now would make the first run look like a
        // clean comparison of two identical instants and quietly pass.
        val lastWall = prefs.getLong(PREF_LAST_WALL_CLOCK, 0L)
        val lastElapsed = prefs.getLong(PREF_LAST_ELAPSED_REALTIME, 0L)
        prefs.edit()
            .putLong(PREF_LAST_WALL_CLOCK, wallNow)
            .putLong(PREF_LAST_ELAPSED_REALTIME, elapsedNow)
            .apply()

        if (lastWall == 0L || lastElapsed == 0L) {
            // Nothing to compare against yet. This is not evidence of innocence either,
            // so it is recorded in ATTESTATION_DEPLOY_NOTES as a known limitation rather
            // than flagged — flagging would forfeit every honest first report.
            return ClockReading(backwardJump = false, forwardJump = false)
        }

        return ClockReading(
            backwardJump = lastWall - wallNow > CLOCK_JUMP_THRESHOLD_MS,
            forwardJump = isClockJumpedForward(
                lastWallMs = lastWall,
                lastElapsedRealtimeMs = lastElapsed,
                wallNowMs = wallNow,
                nowElapsedRealtimeMs = elapsedNow,
                allowanceMs = forwardJumpAllowanceMs(windowStartMs, windowEndMs),
            ),
        )
    }

    private class ClockReading(val backwardJump: Boolean, val forwardJump: Boolean)

    companion object {
        private const val PREFS_NAME = "nopeek_usage"
        private const val PREF_LAST_WALL_CLOCK = "last_wall_clock"
        private const val PREF_LAST_ELAPSED_REALTIME = "last_elapsed_realtime"
        private const val CLOCK_JUMP_THRESHOLD_MS = 5 * 60 * 1000L
        // Scheduling used to live here as a 15-minute periodic job behind a connectivity
        // constraint, with no caller anywhere in the app. Both halves were wrong and ReportScheduler
        // owns it now: a window's report is due at one instant and valid until windowEnd + grace,
        // so a recurring job fires at the wrong times, and a deferred job can make an honest user
        // late for reasons they cannot see. See ReportScheduler's class comment.
    }
}

package com.nopeek.app.data.usage

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.nopeek.app.data.TamperSignals
import com.nopeek.app.data.UsageReport
import com.nopeek.app.data.attest.EvidenceReporter
import com.nopeek.app.data.attest.KeyStoreEnroller
import com.nopeek.app.data.attest.OracleEvidenceTransport
import com.nopeek.app.data.attest.ReportOutcome
import java.util.concurrent.TimeUnit

/**
 * Gets exactly one signed report to the oracle per window, twice if the first chance is lost
 * (plan Task 13).
 *
 * Two chances, because on Android one is not enough. The scheduled path fires a one-shot
 * worker at `windowEnd`; the eager path runs when the app next resumes. MIUI is the reason
 * both exist — force-stopping the app, enabling battery saver, or excluding Nopeek from
 * battery optimization stops WorkManager from running at all, and a user who did nothing
 * wrong but whose phone killed the background job would otherwise lose their stake to a
 * scheduler. The eager path is what makes "the app was closed" survivable; Review Focus 5
 * tests exactly that.
 *
 * The path this replaces ran a 15-minute periodic job that required connectivity — and was
 * never scheduled by anything, since `scheduleUsageSync` had no callers. Periodic was the
 * wrong shape regardless: a report becomes due at one instant and stays valid until
 * `windowEnd + grace`, so a recurring job either fires before there is anything to report or
 * keeps firing after settlement. And a connectivity constraint lets WorkManager defer past
 * the grace window for reasons the user cannot see or fix — a late report is UNVERIFIED, so
 * the scheduling policy itself could forfeit an honest stake.
 */
object ReportScheduler {

    /** Whether this window's report may be produced yet, and whether it still may be at all. */
    enum class DueState { WAITING, DUE, LATE }

    /**
     * One worker name per challenge. `enqueueUniqueWork` replaces whatever already holds the
     * name, so a name that identified only the app would let a new duel cancel a live one —
     * the older duel's report would simply never be produced, and nothing would say so.
     */
    fun workName(challengePubkey: String) = "nopeek_report_$challengePubkey"

    /**
     * `windowEnd` itself is submittable, and so is the last millisecond of grace. Both edges
     * are pinned by tests because the two failure modes are opposite and equally quiet: too
     * strict and an honest user is told they were late; too loose and a report the oracle will
     * refuse gets submitted anyway, burning the window's single nonce.
     */
    fun dueState(params: UsageSyncParams, nowMs: Long): DueState = when {
        nowMs < params.windowEnd -> DueState.WAITING
        nowMs > params.windowEnd + params.graceSeconds * 1000L -> DueState.LATE
        else -> DueState.DUE
    }

    /**
     * The scheduled request: fires at window end, carries its parameters, asks for nothing.
     *
     * No constraints, deliberately. A job with no network at the deadline fails its submission
     * and comes back [ReportOutcome.TransportFailed] — retryable, honest, and visible. The same
     * job behind a connectivity constraint is silently deferred by WorkManager until possibly
     * after grace, which turns a network blip into a lost stake.
     */
    fun buildRequest(params: UsageSyncParams, nowMs: Long): OneTimeWorkRequest {
        // The eager path can reach this after the deadline, when the arithmetic is negative.
        // Clamped rather than passed through: a negative initial delay has been an
        // IllegalArgumentException in WorkManager, and a crash here means no report at all.
        val delayMs = (params.windowEnd - nowMs).coerceAtLeast(0L)
        return OneTimeWorkRequest.Builder(UsageWorker::class.java)
            .setInputData(UsageSyncParamsReader.toData(params))
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
    }

    fun schedule(context: Context, params: UsageSyncParams) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(params.challengePubkey),
            ExistingWorkPolicy.REPLACE,
            buildRequest(params, System.currentTimeMillis()),
        )
    }

    fun cancel(context: Context, challengePubkey: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(challengePubkey))
    }

    /**
     * The whole decision, with the submission injected. Returns without calling [submit] when
     * the window is not due or is past grace: asking for a nonce is a one-way step, so doing
     * it early or pointlessly spends the one value that makes this window reportable.
     */
    fun reportNowIfDue(
        params: UsageSyncParams,
        nowMs: Long,
        submit: (UsageSyncParams) -> ReportOutcome,
    ): ReportOutcome = when (dueState(params, nowMs)) {
        DueState.WAITING -> ReportOutcome.NotYetDue
        DueState.LATE -> ReportOutcome.Rejected("REPORT_LATE")
        DueState.DUE -> submit(params)
    }

    /** The production entry point: measure this window, then submit what was measured. */
    fun reportNowIfDue(context: Context, params: UsageSyncParams): ReportOutcome =
        reportNowIfDue(params, System.currentTimeMillis()) { submit(context, it) }

    /**
     * Measures the window once and submits that measurement.
     *
     * One read of the event stream matters: the worker already measures in order to render a
     * local verdict, and a second read here could produce a different `usedSeconds` or digest
     * from the first — the app would then show the user one number while the hardware attested
     * and the chain recorded another. [submitReport] is therefore the seam the worker calls.
     */
    fun submit(context: Context, params: UsageSyncParams): ReportOutcome =
        submitReport(context, measure(context, params), params.targetPackage, params.graceSeconds)

    /** Signs and submits an already-measured report. The worker's path through the protocol. */
    fun submitReport(
        context: Context,
        report: UsageReport,
        targetPackage: String,
        graceSeconds: Int,
    ): ReportOutcome {
        val enroller = KeyStoreEnroller(context)
        return EvidenceReporter(
            transport = OracleEvidenceTransport(),
            // The package the attestation carries, which is check 11's comparison value. Taken
            // from the running context rather than hardcoded: check 11 compares the attested
            // package against this string, and Task 2 caught a substring match letting
            // com.nopeek.app.test satisfy a test for com.nopeek.app.
            packageName = context.packageName,
            mintKey = { challengeB64 -> enroller.mintForChallenge(challengeB64) },
            // The keystore's own signing; the bytes it returns are DER and EvidenceReporter
            // converts them. KeyStoreEnroller.sign documents why the algorithm name is spelled
            // "SHA256withECDSA" rather than composed from KeyProperties.DIGEST_SHA256.
            sign = { alias, bytes -> enroller.sign(alias, bytes) },
            graceSeconds = graceSeconds,
        ).submit(report, targetPackage)
    }

    /**
     * The single read of UsageStatsManager for a window.
     *
     * The tamper signals here are deliberately narrower than the ones [UsageWorker] records.
     * The worker keeps a persisted wall-clock/monotonic pair across runs and can therefore
     * detect a clock that moved; this recovery path may be the first code to run after a
     * force-stop, with no earlier observation to compare against, and inventing one would
     * produce a signal that means nothing. Reporting fewer signals is the honest direction:
     * the oracle's verdict comes from the attested figures and the other thirteen checks, not
     * from the absence of a flag this path cannot establish.
     */
    fun measure(context: Context, params: UsageSyncParams): UsageReport {
        val collector = UsageEventCollector(context)
        val eventSet = collector.collect(params.windowStart, params.windowEnd)
        return UsageReport(
            challengePubkey = params.challengePubkey,
            walletPubkey = params.walletPubkey,
            usedSeconds = collector.usedSecondsFor(eventSet, params.targetPackage),
            windowStart = params.windowStart,
            windowEnd = params.windowEnd,
            tamperSignals = TamperSignals(
                permissionRevoked = !UsagePermissionHelper.isGranted(context),
            ),
            deviceActiveSeconds = eventSet.deviceActiveSeconds,
            eventCount = eventSet.eventCount,
            eventSetDigestHex = eventSet.eventSetDigestHex,
        )
    }
}

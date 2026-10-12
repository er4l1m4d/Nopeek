package com.nopeek.app.data.usage

import com.nopeek.app.data.attest.ReportOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 13: the scheduler's decisions, as pure functions.
 *
 * The three properties that decide whether a user's report exists at all are timing and
 * identity, and neither needs Android: when a window is still submittable, when grace has
 * expired, and whether two challenges can collide on one worker name. What does need Android —
 * handing a request to WorkManager — is a two-line call over the decisions proven here, and
 * the deadline behaviour it produces is exercised on the device in the MIUI checklist step.
 *
 * The timing rule is the one that cost the most thought. The path this replaces ran a
 * periodic 15-minute job constrained to connectivity, which means a report becomes due at
 * window end and may be submitted any time until windowEnd + grace. A periodic job with a
 * network constraint can be deferred by WorkManager past that grace window for reasons that
 * have nothing to do with the user, and a late report is UNVERIFIED — the honest user loses
 * the pot to a scheduling policy. So: one shot, timed to the deadline, no constraints.
 */
class ReportSchedulerTest {

    private val windowStart = 1_760_000_000_000L
    private val windowEnd = windowStart + 1_800_000L
    private val graceSeconds = 60

    private fun params(now: Long = windowStart) = UsageSyncParams(
        challengePubkey = "DJftv46AvjrbkbUCHR74ntWMXb5r7eHxHo3SXZkox2NM",
        walletPubkey = "6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj",
        targetPackage = "com.instagram.android",
        windowStart = windowStart,
        windowEnd = windowEnd,
        limitSeconds = 300,
        graceSeconds = graceSeconds,
        reportTime = now,
    )

    /**
     * The submitter the scheduler is allowed to invoke. It takes the window and nothing else:
     * deciding *when* to report is the scheduler's job, measuring and signing is the
     * reporter's, and letting the scheduler build a UsageReport here would put measurement
     * behind a timing gate where no test could see it.
     */
    private fun recorded(
        calls: MutableList<String>,
        outcome: ReportOutcome,
    ): (UsageSyncParams) -> ReportOutcome = { _ -> calls += "submit"; outcome }

    @Test
    fun aWindowIsNotSubmittableBeforeItCloses() {
        val calls = mutableListOf<String>()
        val outcome = ReportScheduler.reportNowIfDue(params(), windowEnd - 1, recorded(calls, ReportOutcome.Accepted("x")))

        assertEquals(ReportOutcome.NotYetDue, outcome)
        // The network is untouched. Fetching a nonce here would consume the window's single
        // use before there is anything to report, and the retry could never get another.
        assertTrue("no transport call may happen before window end, got $calls", calls.isEmpty())
    }

    @Test
    fun aWindowIsSubmittableInsideGrace() {
        val calls = mutableListOf<String>()
        val outcome = ReportScheduler.reportNowIfDue(params(), windowEnd + 1, recorded(calls, ReportOutcome.Accepted("abc")))

        assertEquals(ReportOutcome.Accepted("abc"), outcome)
        assertEquals(listOf("submit"), calls)
    }

    @Test
    fun aWindowGivesUpAfterGraceExpiryWithoutReachingTheNetwork() {
        val calls = mutableListOf<String>()
        val outcome = ReportScheduler.reportNowIfDue(
            params(), windowEnd + graceSeconds * 1000L + 1, recorded(calls, ReportOutcome.Accepted("x")),
        )

        assertEquals(ReportOutcome.Rejected("REPORT_LATE"), outcome)
        assertTrue(
            "a report that cannot be accepted must not consume a nonce or hit the oracle, got $calls",
            calls.isEmpty(),
        )
    }

    @Test
    fun theGraceBoundaryItselfIsStillSubmittable() {
        // windowEnd + grace, exactly. Off-by-one here is a user told they were late when the
        // oracle would have accepted them, which is the fail-closed direction and the one
        // nobody notices because the app and the oracle agree on nothing until you test it.
        val calls = mutableListOf<String>()
        val outcome = ReportScheduler.reportNowIfDue(
            params(), windowEnd + graceSeconds * 1000L, recorded(calls, ReportOutcome.Accepted("ok")),
        )

        assertEquals(ReportOutcome.Accepted("ok"), outcome)
    }

    @Test
    fun dueStateReportsTheThreePhasesOfAWindow() {
        assertEquals(ReportScheduler.DueState.WAITING, ReportScheduler.dueState(params(), windowEnd - 1))
        assertEquals(ReportScheduler.DueState.DUE, ReportScheduler.dueState(params(), windowEnd))
        assertEquals(ReportScheduler.DueState.DUE, ReportScheduler.dueState(params(), windowEnd + 1_000))
        assertEquals(
            ReportScheduler.DueState.LATE,
            ReportScheduler.dueState(params(), windowEnd + graceSeconds * 1000L + 1),
        )
    }

    @Test
    fun workNameIsUniquePerChallenge() {
        val first = ReportScheduler.workName(params().challengePubkey)
        val second = ReportScheduler.workName("4t3f9rVXNbwFFLNmwbLfmFRQfcPrqGRmzXfVwMFwBH2D")

        assertEquals("nopeek_report_" + params().challengePubkey, first)
        assertNotEquals(
            "two duels for one wallet must not share a unique-work name; enqueueUniqueWork " +
                "with a shared name replaces the earlier one, silently cancelling a live duel",
            first, second,
        )
        assertTrue(
            "the name must carry the challenge, not merely identify the app",
            first.contains(params().challengePubkey),
        )
    }

    @Test
    fun theReportRequestIsOneShotTimedToTheWindowAndCarriesNoConstraints() {
        val request = ReportScheduler.buildRequest(params(), windowEnd - 60_000)

        // Not periodic by construction: buildRequest's return type is OneTimeWorkRequest, so
        // no periodic job can come out of it. What can break is the timing and the waiting.
        val spec = request.workSpec
        assertEquals(
            "must fire at window end, when the report first becomes valid",
            60_000L, spec.initialDelay,
        )
        assertEquals(
            "a connectivity constraint lets WorkManager defer past the grace window, which " +
                "makes an honest user late for reasons they cannot see or fix",
            androidx.work.Constraints.NONE, spec.constraints,
        )
        assertEquals(
            "the worker needs the challenge to name its own work",
            params().challengePubkey,
            spec.input.keyValueMap[UsageSyncParamsReader.KEY_CHALLENGE],
        )
    }

    @Test
    fun aWindowAlreadyInThePastSchedulesImmediatelyRatherThanNegatively() {
        // The eager path (app reopened after a force-stop) computes a delay from a window that
        // closed while the app was dead. A negative initialDelay is not "run now" in every
        // WorkManager version — it has been an IllegalArgumentException — and a crash here
        // means the user never gets to report at all.
        val request = ReportScheduler.buildRequest(params(), windowEnd + 30_000)

        assertEquals(0L, request.workSpec.initialDelay)
    }
}

package com.nopeek.app.data.usage

import com.nopeek.app.data.TamperSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 12: the collector's math, tested as pure functions.
 *
 * `UsageEventCollector.collect()` needs a Context and a real UsageStatsManager, so it
 * is covered by the on-device check in ATTESTATION_DEPLOY_NOTES.md. Everything that
 * decides a verdict — the digest, the whole-device active span, the per-package
 * seconds, the forward-clock test — is free of Android and proven here.
 */
class UsageEventCollectorTest {

    private fun ev(pkg: String, type: Int, ts: Long) = UsageEvent(pkg, type, ts)
    private fun fg(pkg: String, ts: Long) = ev(pkg, UsageEventType.MOVE_TO_FOREGROUND, ts)
    private fun bg(pkg: String, ts: Long) = ev(pkg, UsageEventType.MOVE_TO_BACKGROUND, ts)

    private val windowStart = 0L
    private val windowEnd = 200_000L

    private val baseline = listOf(
        fg("com.a", 0L),
        fg("com.b", 50_000L),
        bg("com.a", 100_000L),
        bg("com.b", 150_000L),
    )

    @Test
    fun digestIsOrderAndDuplicateIndependent() {
        val canonical = buildEventSet(baseline, windowStart, windowEnd)

        // Reordered: same set of triples, so the digest must not move.
        val shuffled = listOf(
            bg("com.b", 150_000L),
            fg("com.b", 50_000L),
            bg("com.a", 100_000L),
            fg("com.a", 0L),
        )
        assertEquals(
            canonical.eventSetDigestHex,
            buildEventSet(shuffled, windowStart, windowEnd).eventSetDigestHex,
        )

        // Duplicated lines: de-duplicated before hashing, so still identical. A
        // device that replayed the same event twice must not produce different
        // evidence than one that reported it once.
        val duplicated = baseline + baseline[0] + baseline[2]
        assertEquals(
            canonical.eventSetDigestHex,
            buildEventSet(duplicated, windowStart, windowEnd).eventSetDigestHex,
        )

        // One changed timestamp is a different event set. This is the assertion that
        // gives the digest its meaning: without it, a constant or empty digest would
        // pass every case above.
        val tampered = listOf(
            fg("com.a", 1L),
            fg("com.b", 50_000L),
            bg("com.a", 100_000L),
            bg("com.b", 150_000L),
        )
        assertNotEquals(
            canonical.eventSetDigestHex,
            buildEventSet(tampered, windowStart, windowEnd).eventSetDigestHex,
        )

        // Lowercase 64-char hex, i.e. a real SHA-256 and not a shorter hash.
        assertTrue(canonical.eventSetDigestHex.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(4, canonical.eventCount)
    }

    @Test
    fun deviceActiveSecondsIgnoresOverlappingSessions() {
        // com.a is foreground 0-100s and com.b is foreground 50-150s. The device was
        // active for 150s, NOT 200s: the 50-100s overlap is one span of device
        // activity, not two. Summing per-package seconds would report 200 and let a
        // device with two overlapping sessions appear "more active" than it was.
        val set = buildEventSet(baseline, windowStart, windowEnd)
        assertEquals(150L, set.deviceActiveSeconds)
    }

    @Test
    fun deviceActiveSecondsChargesOpenSessionToWindowEnd() {
        // Foreground at 150s, never closed, window ends at 200s -> 50s active.
        val set = buildEventSet(
            listOf(fg("com.a", 0L), bg("com.a", 20_000L), fg("com.b", 150_000L)),
            windowStart,
            windowEnd,
        )
        assertEquals(70L, set.deviceActiveSeconds)
    }

    @Test
    fun usedSecondsForTargetsOnePackageOnly() {
        val set = buildEventSet(baseline, windowStart, windowEnd)
        assertEquals(100, usedSecondsWithin(set, "com.a"))
        assertEquals(100, usedSecondsWithin(set, "com.b"))
        // A package that never appeared is zero, and the whole-device figure is
        // neither of those — the invariant the oracle checks is used <= active.
        assertEquals(0, usedSecondsWithin(set, "com.never"))
    }

    @Test
    fun sessionStartedBeforeTheWindowIsStillChargedInsideIt() {
        // com.a went foreground an hour before the window opened and paused 30 minutes
        // into it. The app WAS foreground for those 30 minutes, so a report of 0 s is a
        // lie — and "never leave the app, straddle the boundary" is how you win a duel
        // without any evidence of use. The collector therefore reads a lookbehind before
        // windowStart and clamps, rather than asking the platform only about the window.
        val ws = 3_600_000L
        val we = 7_200_000L
        val set = buildEventSet(
            listOf(fg("com.a", 0L), bg("com.a", ws + 1_800_000L)),
            ws,
            we,
        )
        assertEquals(1800, usedSecondsWithin(set, "com.a"))
        assertEquals(1800L, set.deviceActiveSeconds)

        // The lookbehind is for span arithmetic only. The committed event set stays
        // window-scoped — spec 4.2 hashes the triples "for the whole window" — so the
        // pre-window foreground event is context, not evidence, and is not counted.
        assertEquals(1, set.eventCount)
    }

    @Test
    fun forwardClockJumpIsDetected() {
        // Wall clock gained 120s while only 10s of monotonic time passed: someone
        // moved the clock forward, which is how a device would try to end a window
        // early. Allowance is 5s.
        assertTrue(
            isClockJumpedForward(
                lastWallMs = 1_000_000L,
                lastElapsedRealtimeMs = 500_000L,
                wallNowMs = 1_000_000L + 120_000L,
                nowElapsedRealtimeMs = 500_000L + 10_000L,
                allowanceMs = 5_000L,
            ),
        )

        // Honest elapsed time: wall and monotonic advanced together.
        assertFalse(
            isClockJumpedForward(
                lastWallMs = 1_000_000L,
                lastElapsedRealtimeMs = 500_000L,
                wallNowMs = 1_000_000L + 10_000L,
                nowElapsedRealtimeMs = 500_000L + 10_000L,
                allowanceMs = 5_000L,
            ),
        )

        // Wall went backwards is not a FORWARD jump; the existing backward detector
        // owns that case.
        assertFalse(
            isClockJumpedForward(
                lastWallMs = 1_000_000L,
                lastElapsedRealtimeMs = 500_000L,
                wallNowMs = 900_000L,
                nowElapsedRealtimeMs = 510_000L,
                allowanceMs = 5_000L,
            ),
        )
    }

    @Test
    fun monotonicClockGoingBackwardsIsReportedAsUnverifiable() {
        // `elapsedRealtime` is milliseconds since boot, so a negative gain can only mean
        // the device rebooted between the two observations - and the stored pair is now
        // measured against a different boot epoch. Returning false here would mean a
        // power-button reboot mid-duel turns the forward-jump check off, which is the
        // same shape as the fail-open bugs found in the verifier review: a check that
        // cannot evaluate must reject, not clear.
        assertTrue(
            isClockJumpedForward(
                lastWallMs = 1_000_000L,
                lastElapsedRealtimeMs = 5_000_000L,
                wallNowMs = 1_010_000L,
                nowElapsedRealtimeMs = 40_000L,
                allowanceMs = 5_000L,
            ),
        )
    }

    @Test
    fun allowanceIsSizedToTheWindowAndCapsBelowTheCheatsItProtectsAgainst() {
        // Task 15's demo window is 120 s against a 60 s limit. Reusing the 5-minute
        // backward-jump threshold here would hand out 299 s of free forward drift per
        // observation pair - more than the window and more than the limit combined.
        assertEquals(12_000L, forwardJumpAllowanceMs(0L, 120_000L))
        // Long windows cap rather than scale, so a 60-minute duel does not buy 6 free
        // minutes of clock.
        assertEquals(60_000L, forwardJumpAllowanceMs(0L, 3_600_000L))
        // And the floor keeps a sub-minute window from allowing nothing at all, which
        // would forfeit an honest stake over one NTP correction.
        assertEquals(5_000L, forwardJumpAllowanceMs(0L, 20_000L))

        // Consequence at the demo's own size: 4 minutes of forward drift inside a
        // 120 s window is now caught, where it previously sat under the threshold.
        assertTrue(
            isClockJumpedForward(
                lastWallMs = 0L,
                lastElapsedRealtimeMs = 0L,
                wallNowMs = 240_000L,
                nowElapsedRealtimeMs = 10_000L,
                allowanceMs = forwardJumpAllowanceMs(0L, 120_000L),
            ),
        )
    }

    @Test
    fun forwardJumpSignalReachesTheVerdict() {
        // The signal marks the report compromised and is named on the wire. Naming is
        // what matters: `determine_result.ts` blocks on any non-empty tamper list, so
        // the oracle fails this closed without having to learn the new name - and any
        // flag added on the device side later blocks too.
        val signals = TamperSignals(clockJumpedForward = true)
        assertTrue(signals.isCompromised())
        assertEquals(listOf("CLOCK_JUMPED_FORWARD"), tamperSignalNames(signals))

        val detected = detectTamperSignals(
            permissionRevoked = false,
            clockJumped = false,
            reportTime = windowEnd,
            windowEnd = windowEnd,
            graceSeconds = 60,
            clockJumpedForward = true,
        )
        assertTrue(detected.clockJumpedForward)

        // And it reaches the verdict through the production path, not a re-implementation
        // of the rule here: a report under the limit but carrying the forward-jump
        // signal resolves UNVERIFIED, which forfeits the stake.
        val verdict = com.nopeek.app.data.verify.LocalRuleCommitmentVerifier()
            .verify(
                com.nopeek.app.data.UsageReport(
                    usedSeconds = 10,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    tamperSignals = detected,
                ),
                limitSeconds = 300,
            )
        assertEquals(com.nopeek.app.data.VerificationStatus.UNVERIFIED, verdict)
    }
}

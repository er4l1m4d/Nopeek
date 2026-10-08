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
        assertTrue(usedSecondsWithin(set, "com.a") <= set.deviceActiveSeconds.toInt())
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

        // The signal reaches the verdict: it marks the report compromised and is
        // named on the wire, so the oracle's "any tamper signal -> UNVERIFIED" rule
        // fails it closed without the oracle needing to know the name.
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

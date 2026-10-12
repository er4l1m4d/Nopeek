package com.nopeek.app.data.usage

import com.nopeek.app.data.local.ChallengeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 13 Step 3, the half that does not need the phone: which windows the eager path may
 * report.
 *
 * The eager hook exists because MIUI kills background work. When the app next resumes, every
 * window that closed while it was dead still has a report owed — but only inside grace, and
 * only for the wallet this device holds a key for.
 *
 * That last rule is the one worth reading twice. A duel row carries BOTH wallets, and a naive
 * "recover everything that is due" would submit for the opponent using this device's enrolled
 * key. The oracle would refuse it — the enrolled key does not match their wallet — but the
 * submission consumes the opponent's single-use nonce first, so their honest report can no
 * longer be filed. Recovering wrongly does not merely fail to help a stranger; it takes their
 * one chance away. Hence a test, before the hook exists.
 */
class ReportRecoveryTest {

    private val localWallet = "6k9TDQtquf2pusu9LHMVACFRtGfCCTsvzfvuifcRWmmj"
    private val opponentWallet = "4t3f9rVXNbwFFLNmwbLfmFRQfcPrqGRmzXfVwMFwBH2D"
    private val graceSeconds = 60

    private val windowStart = 1_760_000_000_000L
    private val windowEnd = windowStart + 1_800_000L

    private fun entity(
        status: String = "ACTIVE",
        creator: String? = localWallet,
        opponent: String = opponentWallet,
        pubkey: String? = "DJftv46AvjrbkbUCHR74ntWMXb5r7eHxHo3SXZkox2NM",
    ) = ChallengeEntity(
        id = "row-1",
        challengePubkey = pubkey,
        creatorWallet = creator,
        opponentWallet = opponent,
        targetApp = "com.instagram.android",
        limitSeconds = 300,
        stakeAmountRaw = 5_000_000L,
        windowSeconds = 1_800L,
        startTime = windowStart,
        endTime = windowEnd,
        localStatus = status,
    )

    private fun recover(now: Long, vararg entities: ChallengeEntity) =
        ReportRecovery.windowsToRecover(entities.toList(), localWallet, now, graceSeconds)

    @Test
    fun aWindowThatClosedWhileTheAppWasDeadIsRecovered() {
        val params = recover(windowEnd + 1_000, entity())

        assertEquals(1, params.size)
        assertEquals(windowStart, params.single().windowStart)
        assertEquals(windowEnd, params.single().windowEnd)
        assertEquals(localWallet, params.single().walletPubkey)
        assertEquals("com.instagram.android", params.single().targetPackage)
        assertEquals(300, params.single().limitSeconds)
        assertEquals(graceSeconds, params.single().graceSeconds)
    }

    @Test
    fun aWindowStillOpenIsLeftToItsScheduledReport() {
        // The one-shot worker owns a live window. Re-reporting from onResume would mint a key
        // and consume a nonce for a measurement taken before the window ended.
        assertTrue(recover(windowEnd - 1, entity()).isEmpty())
    }

    @Test
    fun aWindowPastGraceIsGivenUpOnRatherThanRetriedForever() {
        assertTrue(recover(windowEnd + graceSeconds * 1000L + 1, entity()).isEmpty())
    }

    @Test
    fun theLastMillisecondOfGraceIsStillRecovered() {
        assertEquals(1, recover(windowEnd + graceSeconds * 1000L, entity()).size)
    }

    @Test
    fun theOpponentWindowIsNeverTouched() {
        // See the class comment: submitting for them burns the one nonce they need.
        val asOpponentOnly = entity(creator = null, opponent = opponentWallet)
        val mineIsOpponentOnTheirRow = entity(creator = opponentWallet, opponent = localWallet)

        assertTrue(recover(windowEnd + 1_000, asOpponentOnly).isEmpty())
        assertEquals(
            "the local wallet appears on this row as the opponent, so it is recoverable, but " +
                "the params must name the LOCAL wallet rather than the creator",
            localWallet,
            recover(windowEnd + 1_000, mineIsOpponentOnTheirRow).single().walletPubkey,
        )
    }

    @Test
    fun aChallengeThatNeverWentOnChainIsSkipped() {
        // No pubkey means no challenge row on the oracle side, so there are no terms to verify
        // against and submission would fail CHALLENGE_UNKNOWN after consuming a nonce.
        assertTrue(recover(windowEnd + 1_000, entity(pubkey = null)).isEmpty())
        assertTrue(recover(windowEnd + 1_000, entity(pubkey = "   ")).isEmpty())
    }

    @Test
    fun aSettledOrLockedChallengeIsNotResubmitted() {
        for (status in listOf("BOTH_FAIL_LOCKED", "SETTLED", "COMPLETED", "CANCELLED")) {
            assertTrue(
                "$status is finished; a report against it re-opens a settled duel",
                recover(windowEnd + 1_000, entity(status = status)).isEmpty(),
            )
        }
    }

    @Test
    fun aTamperFlaggedWindowIsStillRecovered() {
        // PENDING_VERIFICATION is the app's own local suspicion, not the oracle's verdict. The
        // whole point of the attested report is that the device's guess gets checked, so this
        // status must stay recoverable — dropping it would let a false local alarm decide money.
        assertEquals(1, recover(windowEnd + 1_000, entity(status = "PENDING_VERIFICATION")).size)
    }

    @Test
    fun recoveryOrdersTheMostUrgentWindowFirst() {
        // Two overdue windows on one device: the one whose grace expires soonest must be
        // submitted first, because a slow first submission can make the second one late.
        val sooner = entity(pubkey = "SoonerChallengePubkeyAAAAAAAAAAAAAAAAAAAAAAAAAAA").copy(
            endTime = windowStart + 60_000,
        )
        val later = entity(pubkey = "LaterChallengePubkeyAAAAAAAAAAAAAAAAAAAAAAAAAA").copy(
            endTime = windowStart + 120_000,
        )

        val params = ReportRecovery.windowsToRecover(
            listOf(later, sooner), localWallet, windowStart + 120_000, graceSeconds,
        )

        assertEquals(2, params.size)
        assertEquals("the window closing first is reported first", windowStart + 60_000, params.first().windowEnd)
    }

    @Test
    fun anEmptyLedgerRecoversNothing() {
        assertTrue(recover(windowEnd + 1_000).isEmpty())
    }
}

package com.nopeek.app.data.usage

import com.nopeek.app.data.local.ChallengeEntity

/**
 * Which windows the eager path may report when the app resumes (plan Task 13 Step 3).
 *
 * Kept as a pure function over rows, a wallet and a clock — rather than as a lifecycle
 * callback that reaches into Room — for two reasons. The decision is the part that can be
 * wrong in a way that costs someone money, and a JVM test can enumerate every status and both
 * sides of a duel in a second, whereas a lifecycle hook can only be checked by driving the app
 * by hand, which is how the last several format assumptions in this project survived.
 *
 * The hook itself is one line over this, and it waits on a capability the app does not have
 * yet: nothing in `data/` currently knows which wallet is *this device's*. A duel row carries
 * both parties, so guessing is not available — see [windowsToRecover].
 */
object ReportRecovery {

    /**
     * Statuses still owed a report.
     *
     * `PENDING_VERIFICATION` is included deliberately: it is the app's own local suspicion
     * (a tamper flag raised by [UsageWorker]), not the oracle's verdict. The entire premise of
     * an attested report is that the device's guess gets checked by hardware, so treating a
     * false local alarm as terminal would let the app decide money by failing to report.
     *
     * Anything settled or locked is finished, and a late report against it would arrive after
     * the escrow has already moved.
     */
    private val REPORTABLE = setOf("CREATED", "ACTIVE", "PENDING_VERIFICATION")

    /**
     * The windows this device may still report, most urgent first.
     *
     * `localWalletPubkey` is required rather than inferred. A row names two wallets, and
     * reporting for the opponent would consume *their* single-use nonce with a submission the
     * oracle refuses (their enrolled key is not this device's). That is not a failed attempt on
     * their behalf, it is their one chance spent — so a row is only recoverable when this
     * device's wallet is one of its two parties, and the params always name that wallet.
     *
     * Ordered by `endTime` ascending: when several windows are overdue, the one whose grace
     * expires first goes first, because submissions are sequential and a slow earlier report
     * can make a later one late.
     */
    fun windowsToRecover(
        entities: List<ChallengeEntity>,
        localWalletPubkey: String?,
        nowMs: Long,
        graceSeconds: Int = DEFAULT_GRACE_SECONDS,
    ): List<UsageSyncParams> {
        if (localWalletPubkey.isNullOrBlank()) return emptyList()

        return entities
            .asSequence()
            .filter { it.localStatus in REPORTABLE }
            // Blank as well as null: a whitespace pubkey would pass a null check and then fail
            // the oracle's base58 validation after the nonce has been issued.
            .filter { !it.challengePubkey.isNullOrBlank() }
            // Membership, not position: the local wallet may be either party, and the params
            // always name it rather than the creator, because the enrolled key on this device
            // belongs to whoever is reading their own report.
            .filter { it.creatorWallet == localWalletPubkey || it.opponentWallet == localWalletPubkey }
            .map { entity ->
                UsageSyncParams(
                    challengePubkey = entity.challengePubkey!!,
                    walletPubkey = localWalletPubkey,
                    targetPackage = entity.targetApp,
                    windowStart = entity.startTime,
                    windowEnd = entity.endTime,
                    limitSeconds = entity.limitSeconds,
                    graceSeconds = graceSeconds,
                    reportTime = nowMs,
                )
            }
            .filter { ReportScheduler.dueState(it, nowMs) == ReportScheduler.DueState.DUE }
            .sortedBy { it.windowEnd }
            .toList()
    }

    private const val DEFAULT_GRACE_SECONDS = 60
}

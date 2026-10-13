package com.nopeek.app.data.local

import android.content.Context

class NopeekPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nopeek_prefs", Context.MODE_PRIVATE)

    var seenOnboarding: Boolean
        get() = prefs.getBoolean("seen_onboarding_v1", false)
        set(value) = prefs.edit().putBoolean("seen_onboarding_v1", value).apply()

    var knownUser: Boolean
        get() = prefs.getBoolean("known_user_v1", false)
        set(value) = prefs.edit().putBoolean("known_user_v1", value).apply()

    val routedBefore: Boolean
        get() = seenOnboarding || knownUser

    /**
     * The wallet pubkey this device reports for — the one fact the eager recovery path
     * needs and, until now, nothing persisted. A duel row names TWO wallets, and guessing
     * the local one would submit for the opponent and spend their single-use nonce. The
     * value is written when a wallet connects and survives force-stop, which is exactly
     * the process state the recovery path runs in.
     */
    var localWalletPubkey: String?
        get() = prefs.getString("local_wallet_pubkey_v1", null)
        set(value) = prefs.edit().putString("local_wallet_pubkey_v1", value).apply()
}

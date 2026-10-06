package com.nopeek.app.data.wallet

/**
 * Pure, Android-free wallet state model. Kept separate from WalletManager so the mapping
 * logic can be unit-tested on the JVM without initializing Uri-dependent classes.
 */
sealed interface WalletState {
    data object Disconnected : WalletState
    data object Connecting : WalletState
    data class Connected(val address: String, val label: String?) : WalletState
    data class Error(val message: String) : WalletState
}

/** Library-independent view of an association attempt, so the mapping is unit-testable. */
sealed interface AssociationOutcome {
    data class Authorized(val accounts: List<AccountInfo>) : AssociationOutcome
    data object NoWallet : AssociationOutcome
    data object Cancelled : AssociationOutcome
    data class Failed(val message: String) : AssociationOutcome
}

data class AccountInfo(val publicKey: ByteArray, val label: String?)

/** Library-independent view of a signTransaction attempt (Phase 7.2). */
sealed interface SignOutcome {
    data class Signed(val transaction: ByteArray) : SignOutcome
    data object NoWallet : SignOutcome
    data object Cancelled : SignOutcome
    data class Failed(val message: String) : SignOutcome
}

fun signFailureOutcome(message: String): SignOutcome =
    if (message.lowercase().let { it.contains("reject") || it.contains("cancel") }) {
        SignOutcome.Cancelled
    } else {
        SignOutcome.Failed(message)
    }

fun resolveOutcome(outcome: AssociationOutcome): WalletState = when (outcome) {
    is AssociationOutcome.Authorized ->
        if (outcome.accounts.isEmpty()) {
            WalletState.Error("Wallet returned no accounts. Try reconnecting.")
        } else {
            val account = outcome.accounts.first()
            WalletState.Connected(Base58.encode(account.publicKey), account.label)
        }
    AssociationOutcome.NoWallet ->
        WalletState.Error("No Solana wallet found. Install Phantom or Solflare to connect.")
    AssociationOutcome.Cancelled -> WalletState.Disconnected
    is AssociationOutcome.Failed -> WalletState.Error(outcome.message)
}

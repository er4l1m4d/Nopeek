package com.volo.app.data.wallet

import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.volo.app.data.identity.MockSeekerProvider
import com.volo.app.data.identity.SeekerIdentityProvider
import kotlinx.coroutines.CancellationException

/**
 * Thin process-wide wrapper around the Mobile Wallet Adapter.
 * Sign-in uses the wallet's own UI; this only tracks the resulting state.
 */
internal class WalletManager {

    /**
     * Plan 10.1 Step 3: MockSeekerProvider by default (no hardware); replaceable
     * via DI once a real Seeker identity provider lands (docs/seeker-future-integration.md).
     */
    var identityProvider: SeekerIdentityProvider = MockSeekerProvider

    var state: WalletState = WalletState.Disconnected
        private set

    val walletAddress: String?
        get() = (state as? WalletState.Connected)?.address

    private val adapter = MobileWalletAdapter(
        ConnectionIdentity(
            identityUri = Uri.parse("https://volo.app"),
            iconUri = Uri.parse("https://volo.app/icon.png"),
            identityName = "Volo"
        )
    )

    suspend fun connect(sender: ActivityResultSender) {
        if (state is WalletState.Connecting) return
        state = WalletState.Connecting
        state = try {
            resolveOutcome(mapResult(adapter.connect(sender)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WalletState.Error(e.message ?: "Wallet connection failed.")
        }
    }

    suspend fun disconnect(sender: ActivityResultSender) {
        if (state !is WalletState.Connected) return
        try {
            adapter.disconnect(sender)
        } finally {
            state = WalletState.Disconnected
        }
    }

    /** Phase 7.2 Step 4: have the wallet sign a serialized transaction. */
    suspend fun signTransaction(sender: ActivityResultSender, transaction: ByteArray): SignOutcome {
        if (state !is WalletState.Connected) {
            return SignOutcome.Failed("Connect a wallet before signing.")
        }
        return try {
            // transact's lambda is `AdapterOperations` receiver + authResult param.
            val result = adapter.transact<ByteArray?>(sender) {
                signTransactions(arrayOf(transaction)).signedPayloads.firstOrNull()
            }
            when (result) {
                is TransactionResult.NoWalletFound -> SignOutcome.NoWallet
                is TransactionResult.Failure -> signFailureOutcome(result.message)
                is TransactionResult.Success ->
                    result.payload?.let { SignOutcome.Signed(it) }
                        ?: SignOutcome.Failed("Wallet returned no signed transaction.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SignOutcome.Failed(e.message ?: "Signing failed.")
        }
    }

    private fun mapResult(result: TransactionResult<Unit>): AssociationOutcome = when (result) {
        is TransactionResult.NoWalletFound -> AssociationOutcome.NoWallet
        is TransactionResult.Failure -> {
            val msg = result.message.lowercase()
            if (msg.contains("reject") || msg.contains("cancel")) {
                AssociationOutcome.Cancelled
            } else {
                AssociationOutcome.Failed(result.message)
            }
        }
        is TransactionResult.Success -> {
            val auth = runCatching { result.authResult }.getOrNull()
            AssociationOutcome.Authorized(
                if (auth != null) {
                    listOf(AccountInfo(publicKey = auth.publicKey, label = auth.accountLabel))
                } else {
                    emptyList()
                }
            )
        }
    }
}

internal val walletManager = WalletManager()

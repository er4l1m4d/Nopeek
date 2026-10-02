package com.volo.app.data.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Base58Test {

    @Test
    fun emptyInput_returnsEmpty() {
        assertEquals("", Base58.encode(byteArrayOf()))
    }

    @Test
    fun singleZeroByte_encodesAsOne() {
        assertEquals("1", Base58.encode(byteArrayOf(0)))
    }

    @Test
    fun leadingZeros_arePreserved() {
        assertEquals("111", Base58.encode(byteArrayOf(0, 0, 0)))
    }

    @Test
    fun helloWorld_matchesKnownVector() {
        val bytes = "Hello World!".toByteArray(Charsets.UTF_8)
        assertEquals("2NEpo7TZRRrLZSi2U", Base58.encode(bytes))
    }

    @Test
    fun systemProgramAddress_allZeros() {
        val zeros = ByteArray(32)
        assertEquals("11111111111111111111111111111111", Base58.encode(zeros))
    }
}

class WalletOutcomeTest {

    @Test
    fun authorized_mapsToConnectedWithBase58Address() {
        val state = resolveOutcome(
            AssociationOutcome.Authorized(listOf(AccountInfo(ByteArray(32), "Phantom")))
        )
        assertEquals(
            WalletState.Connected("11111111111111111111111111111111", "Phantom"),
            state
        )
    }

    @Test
    fun authorizedWithoutAccounts_mapsToError() {
        val state = resolveOutcome(AssociationOutcome.Authorized(emptyList()))
        assertTrue(state is WalletState.Error)
        assertTrue((state as WalletState.Error).message.contains("no accounts", ignoreCase = true))
    }

    @Test
    fun noWallet_mapsToActionableError() {
        val state = resolveOutcome(AssociationOutcome.NoWallet)
        assertTrue(state is WalletState.Error)
        assertTrue((state as WalletState.Error).message.contains("wallet", ignoreCase = true))
    }

    @Test
    fun cancelled_mapsBackToDisconnectedSilently() {
        assertEquals(WalletState.Disconnected, resolveOutcome(AssociationOutcome.Cancelled))
    }

    @Test
    fun failure_mapsToErrorWithMessage() {
        val state = resolveOutcome(AssociationOutcome.Failed("boom"))
        assertEquals(WalletState.Error("boom"), state)
    }
}

class SignOutcomeTest {

    @Test
    fun rejection_mapsToCancelled() {
        assertTrue(signFailureOutcome("User rejected the request") is SignOutcome.Cancelled)
        assertTrue(signFailureOutcome("Operation cancelled") is SignOutcome.Cancelled)
    }

    @Test
    fun otherFailures_keepMessage() {
        val outcome = signFailureOutcome("boom")
        assertTrue(outcome is SignOutcome.Failed)
        assertEquals("boom", (outcome as SignOutcome.Failed).message)
    }
}

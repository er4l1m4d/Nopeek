package com.nopeek.app.data.identity

import com.nopeek.app.data.wallet.Base58
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockSeekerProviderTest {

    private val provider = MockSeekerProvider

    @Test
    fun verifyGenesisToken_isAlwaysUnverified_withoutHardware() {
        assertTrue(provider.verifyGenesisToken("6o9DpPwoNMyfJx2Bw6rFByrZ2Z3r5qF7hJ8eD3aK9nB") is IdentityVerificationResult.Unverified)
        assertTrue(provider.verifyGenesisToken("anything") is IdentityVerificationResult.Unverified)
    }

    @Test
    fun resolveDomain_returnsDemoAddresses() {
        assertEquals("6o9DpPwoNMyfJx2Bw6rFByrZ2Z3r5qF7hJ8eD3aK9nB", provider.resolveDomain("alice.skr"))
        assertEquals("9fG8jK2hL4mN7pQ1rT6sW3xY8bV5cD2eF9gH7jK4ABC", provider.resolveDomain("bob.skr"))
    }

    @Test
    fun resolveDomain_isCaseInsensitive_andUnknownIsNull() {
        assertEquals(provider.resolveDomain("alice.skr"), provider.resolveDomain("Alice.SKR"))
        assertNull(provider.resolveDomain("nobody.skr"))
        assertNull(provider.resolveDomain("plainaddress"))
    }

    @Test
    fun demoAddresses_areValid32ByteBase58() {
        for (handle in listOf("alice.skr", "bob.skr")) {
            val address = provider.resolveDomain(handle)
            assertEquals(32, Base58.decode(address!!)!!.size)
        }
    }

    @Test
    fun handleFor_reverseMapsKnownAddresses() {
        assertEquals("alice.skr", provider.handleFor("6o9DpPwoNMyfJx2Bw6rFByrZ2Z3r5qF7hJ8eD3aK9nB"))
        assertNull(provider.handleFor("unknown-wallet-address"))
    }

    @Test
    fun opponentSubtitle_badgesKnownHandles() {
        assertEquals(
            "Opponent: alice.skr [Unverified] · 20m limit",
            com.nopeek.app.ui.screens.opponentSubtitle("6o9DpPwoNMyfJx2Bw6rFByrZ2Z3r5qF7hJ8eD3aK9nB", 1200),
        )
        assertEquals(
            "vs 6o9D…9nBx · 20m limit",
            com.nopeek.app.ui.screens.opponentSubtitle("6o9DpPwoNMyfJx2Bw6rFByrZ2Z3r5qF7hJ8eD3aK9nBx", 1200),
        )
    }

    @Test
    fun parseOpponent_usesProviderForKnownHandles_andShaFallback() {
        assertEquals(
            "6o9DpPwoNMyfJx2Bw6rFByrZ2Z3r5qF7hJ8eD3aK9nB",
            com.nopeek.app.data.challenge.parseOpponent("alice.skr"),
        )
        assertEquals(
            com.nopeek.app.data.challenge.mockResolveSkr("carol.skr"),
            com.nopeek.app.data.challenge.parseOpponent("carol.skr"),
        )
    }
}

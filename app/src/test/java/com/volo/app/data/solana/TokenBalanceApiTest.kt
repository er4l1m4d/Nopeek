package com.volo.app.data.solana

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBalanceApiTest {

    @Test
    fun parseBalance_readsLamportsFromResultValue() {
        val result = JSONObject("""{"context":{"slot":1},"value":1234567890}""")
        assertEquals(1_234_567_890L, parseBalanceLamports(result))
    }

    @Test
    fun parseBalance_nonNumericValue_isZero() {
        assertEquals(0L, parseBalanceLamports(JSONObject("""{"value":null}""")))
    }

    @Test
    fun parseTokenBalance_readsRawAmountAndDecimals() {
        val result = JSONObject(
            """{"context":{"slot":1},"value":{"amount":"5000000","decimals":6,"uiAmountString":"5"}}"""
        )
        val parsed = parseTokenAccountBalance(result)
        assertEquals(5_000_000L, parsed.first)
        assertEquals(6, parsed.second)
    }

    @Test
    fun parseTokenBalance_missingValue_isZero() {
        val parsed = parseTokenAccountBalance(JSONObject("""{"value":null}"""))
        assertEquals(0L, parsed.first)
        assertEquals(0, parsed.second)
    }

    @Test
    fun formatUiAmount_trimsAndPads() {
        assertEquals("1.5", formatUiAmount(1_500_000_000L, 9))
        assertEquals("1", formatUiAmount(1_000_000L, 6))
        assertEquals("0", formatUiAmount(0L, 6))
        assertEquals("1.234567", formatUiAmount(1_234_567L, 6))
        assertEquals("9.99", formatUiAmount(9_990_000_000L, 9))
        assertEquals("42", formatUiAmount(42L, 0))
    }

    @Test
    fun openDuelsGate_requiresAtLeastTenSkr() {
        assertTrue(openDuelsGate(10_000_000_000L, 9)) // exactly 10 SKR
        assertTrue(openDuelsGate(25_000_000_000L, 9))
        assertFalse(openDuelsGate(9_999_999_999L, 9))
        assertFalse(openDuelsGate(0L, 9))
        assertTrue(openDuelsGate(1_000_000_000_000L, 6)) // decimals-agnostic threshold
    }

    @Test
    fun gateLabel_matchesPlanWording() {
        assertEquals("Open Duels: Available", openDuelsLabel(open = true))
        assertEquals("Unlock by staking 10 SKR", openDuelsLabel(open = false))
    }
}

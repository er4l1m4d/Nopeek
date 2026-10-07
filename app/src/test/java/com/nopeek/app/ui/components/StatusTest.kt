package com.nopeek.app.ui.components

import com.nopeek.app.ui.DESIGN_SYSTEM_TEXT
import com.nopeek.app.ui.contrastOf
import com.nopeek.app.ui.theme.NopeekDarkColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The old version of this test asserted `StatusVariant(label, accent, background)` pairs, which is
 * exactly what made every status pill invisible (Pitch text on a Pitch screen, 1:1). It pinned the
 * bug and stayed green. These tests assert the pair the component actually renders.
 */
class StatusTest {

    private val lifecycleStatuses = listOf(
        "ACTIVE",
        "CREATED",
        "PENDING_VERIFICATION",
        "BOTH_FAIL_LOCKED",
        "SETTLED",
        "REFUNDED",
    )

    private val allVariants: List<StatusVariant> =
        lifecycleStatuses.map { lifecycleVariant(it) } +
            listOf(lifecycleVariant("SOMETHING_NEW")) +
            UsageState.entries.map { usageVariant(it) }

    @Test
    fun everyVariantClearsTheDesignSystemBarOnItsOwnPair() {
        allVariants.forEach { variant ->
            val ratio = contrastOf(variant.content, variant.container)
            assertTrue(
                "'${variant.label}' text on its own container must be >= $DESIGN_SYSTEM_TEXT, was $ratio",
                ratio >= DESIGN_SYSTEM_TEXT,
            )
        }
    }

    @Test
    fun lifecycleLabelsUseHumanWordsNotStatusCodes() {
        assertEquals("Live", lifecycleVariant("ACTIVE").label)
        assertEquals("Waiting", lifecycleVariant("CREATED").label)
        assertEquals("Checking", lifecycleVariant("PENDING_VERIFICATION").label)
        assertEquals("Locked", lifecycleVariant("BOTH_FAIL_LOCKED").label)
        assertEquals("Settled", lifecycleVariant("SETTLED").label)
        assertEquals("Refunded", lifecycleVariant("REFUNDED").label)
        assertEquals("SOMETHING_NEW", lifecycleVariant("SOMETHING_NEW").label) // passthrough
    }

    @Test
    fun usageLabelsReadAsPlainEnglish() {
        assertEquals("Safe", usageVariant(UsageState.PASSING).label)
        assertEquals("At risk", usageVariant(UsageState.AT_RISK).label)
        assertEquals("Over the limit", usageVariant(UsageState.FAILED).label)
        assertEquals("Unverified", usageVariant(UsageState.UNVERIFIED).label)
    }

    /** The design system: warning "always with a triangle glyph and a word", danger "always with a word". */
    @Test
    fun riskCarryingStatesShipAGlyphSoMeaningIsNotColourOnly() {
        assertNotNull(usageVariant(UsageState.PASSING).glyph)
        assertNotNull(usageVariant(UsageState.AT_RISK).glyph)
        assertNotNull(usageVariant(UsageState.FAILED).glyph)
        assertNotNull(lifecycleVariant("BOTH_FAIL_LOCKED").glyph)
    }

    @Test
    fun terminalStatesStayQuietSoTheyNeverOutshoutALiveDuel() {
        val scheme = NopeekDarkColors
        assertEquals(scheme.surfaceHigh, lifecycleVariant("SETTLED").container)
        assertEquals(scheme.onSurface, lifecycleVariant("SETTLED").content)
        assertNull(lifecycleVariant("SETTLED").glyph)
        assertEquals(scheme.surfaceHigh, usageVariant(UsageState.UNVERIFIED).container)
    }

    @Test
    fun liveAndRiskStatesTakeLoudFillsWithPitchForegrounds() {
        val scheme = NopeekDarkColors
        listOf(
            usageVariant(UsageState.PASSING) to scheme.you,
            usageVariant(UsageState.AT_RISK) to scheme.warning,
            usageVariant(UsageState.FAILED) to scheme.danger,
            lifecycleVariant("ACTIVE") to scheme.you,
            lifecycleVariant("BOTH_FAIL_LOCKED") to scheme.danger,
        ).forEach { (variant, expectedContainer) ->
            assertEquals(expectedContainer, variant.container)
            assertEquals(scheme.background, variant.content)
        }
    }

    @Test
    fun noVariantIsColorOnly() {
        allVariants.forEach { assertTrue(it.label.isNotBlank()) }
    }
}

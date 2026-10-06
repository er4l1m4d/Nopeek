package com.nopeek.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class NopeekTokensTest {

    @Test
    fun colorsMatchSpec() {
        assertEquals(NopeekColors.bg, NopeekRef.Pitch)
        assertEquals(NopeekColors.surface, NopeekRef.Pitch)
        assertEquals(NopeekColors.surfaceRaised, NopeekRef.Turf)
        assertEquals(NopeekColors.surfaceHigh, NopeekRef.Turf2)
        assertEquals(NopeekColors.line, NopeekRef.Line)
        assertEquals(NopeekColors.text, NopeekRef.Chalk)
        assertEquals(NopeekColors.textMuted, NopeekRef.Mute)
        assertEquals(NopeekColors.copper, NopeekRef.Amber)
        assertEquals(NopeekColors.copperDeep, NopeekRef.Amber)
        assertEquals(NopeekColors.safe, NopeekRef.Volt)
        assertEquals(NopeekColors.risk, NopeekRef.Amber)
        assertEquals(NopeekColors.fail, NopeekRef.Coral)
    }

    @Test
    fun spacingMatchesSpec() {
        assertEquals(NopeekDimens.spaceXs, NopeekSpace.S4)
        assertEquals(NopeekDimens.spaceSm, NopeekSpace.S8)
        assertEquals(NopeekDimens.spaceMd, NopeekSpace.S12)
        assertEquals(NopeekDimens.spaceLg, NopeekSpace.S16)
        assertEquals(NopeekDimens.spaceXl, NopeekSpace.S20)
        assertEquals(NopeekDimens.spaceXxl, NopeekSpace.S24)
        assertEquals(NopeekDimens.spaceXxxl, NopeekSpace.S32)
    }

    @Test
    fun bodyTextContrastOnBackground() {
        assertTrue(
            "text on bg contrast must be >= 4.5",
            contrast(NopeekColors.text, NopeekColors.bg) >= 4.5,
        )
        assertTrue(
            "textMuted on bg contrast must be >= 4.5",
            contrast(NopeekColors.textMuted, NopeekColors.bg) >= 4.5,
        )
    }

    @Test
    fun darkTextContrastOnChipContainers() {
        listOf(NopeekColors.safe, NopeekColors.risk, NopeekColors.fail, NopeekColors.copper).forEach { container ->
            assertTrue(
                "bg on container $container contrast must be >= 4.5",
                contrast(NopeekColors.bg, container) >= 4.5,
            )
        }
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val s = value.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        val (hi, lo) = if (l1 > l2) l1 to l2 else l2 to l1
        return (hi + 0.05) / (lo + 0.05)
    }
}
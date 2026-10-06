package com.volo.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class VoloTokensTest {

    @Test
    fun colorsMatchSpec() {
        assertEquals(VoloColors.bg, VoloRef.Pitch)
        assertEquals(VoloColors.surface, VoloRef.Pitch)
        assertEquals(VoloColors.surfaceRaised, VoloRef.Turf)
        assertEquals(VoloColors.surfaceHigh, VoloRef.Turf2)
        assertEquals(VoloColors.line, VoloRef.Line)
        assertEquals(VoloColors.text, VoloRef.Chalk)
        assertEquals(VoloColors.textMuted, VoloRef.Mute)
        assertEquals(VoloColors.copper, VoloRef.Amber)
        assertEquals(VoloColors.copperDeep, VoloRef.Amber)
        assertEquals(VoloColors.safe, VoloRef.Volt)
        assertEquals(VoloColors.risk, VoloRef.Amber)
        assertEquals(VoloColors.fail, VoloRef.Coral)
    }

    @Test
    fun spacingMatchesSpec() {
        assertEquals(VoloDimens.spaceXs, VoloSpace.S4)
        assertEquals(VoloDimens.spaceSm, VoloSpace.S8)
        assertEquals(VoloDimens.spaceMd, VoloSpace.S12)
        assertEquals(VoloDimens.spaceLg, VoloSpace.S16)
        assertEquals(VoloDimens.spaceXl, VoloSpace.S20)
        assertEquals(VoloDimens.spaceXxl, VoloSpace.S24)
        assertEquals(VoloDimens.spaceXxxl, VoloSpace.S32)
    }

    @Test
    fun bodyTextContrastOnBackground() {
        assertTrue(
            "text on bg contrast must be >= 4.5",
            contrast(VoloColors.text, VoloColors.bg) >= 4.5,
        )
        assertTrue(
            "textMuted on bg contrast must be >= 4.5",
            contrast(VoloColors.textMuted, VoloColors.bg) >= 4.5,
        )
    }

    @Test
    fun darkTextContrastOnChipContainers() {
        listOf(VoloColors.safe, VoloColors.risk, VoloColors.fail, VoloColors.copper).forEach { container ->
            assertTrue(
                "bg on container $container contrast must be >= 4.5",
                contrast(VoloColors.bg, container) >= 4.5,
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
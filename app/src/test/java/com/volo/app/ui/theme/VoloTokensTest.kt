package com.volo.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class VoloTokensTest {

    @Test
    fun colorsMatchSpec() {
        assertEquals(Color(0xFF10191D), VoloColors.bg)
        assertEquals(Color(0xFF141E22), VoloColors.surface)
        assertEquals(Color(0xFF1A272B), VoloColors.surfaceRaised)
        assertEquals(Color(0xFF213035), VoloColors.surfaceHigh)
        assertEquals(Color(0xFF26363B), VoloColors.line)
        assertEquals(Color(0xFFF2F4EE), VoloColors.text)
        assertEquals(Color(0xFFA4B0AF), VoloColors.textMuted)
        assertEquals(Color(0xFFD17A54), VoloColors.copper)
        assertEquals(Color(0xFFB4603C), VoloColors.copperDeep)
        assertEquals(Color(0xFFB9D5C5), VoloColors.safe)
        assertEquals(Color(0xFFE0A268), VoloColors.risk)
        assertEquals(Color(0xFFE08373), VoloColors.fail)
    }

    @Test
    fun spacingMatchesSpec() {
        assertEquals(4.dp, VoloDimens.spaceXs)
        assertEquals(8.dp, VoloDimens.spaceSm)
        assertEquals(12.dp, VoloDimens.spaceMd)
        assertEquals(16.dp, VoloDimens.spaceLg)
        assertEquals(20.dp, VoloDimens.spaceXl)
        assertEquals(24.dp, VoloDimens.spaceXxl)
        assertEquals(32.dp, VoloDimens.spaceXxxl)
    }

    @Test
    fun radiiMatchSpec() {
        assertEquals(12.dp, VoloDimens.radiusControl)
        assertEquals(18.dp, VoloDimens.radiusCard)
        assertEquals(24.dp, VoloDimens.radiusSheet)
        assertEquals(999.dp, VoloDimens.radiusPill)
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

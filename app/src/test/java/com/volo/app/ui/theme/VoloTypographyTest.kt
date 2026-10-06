package com.volo.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoloTypographyTest {

    @Test
    fun everyStyleMatchesSpec() {
        val rows = listOf(
            Triple("DisplayHero", VoloText.DisplayHero, 88),
            Triple("DisplayClock", VoloText.DisplayClock, 60),
            Triple("DisplayTitle", VoloText.DisplayTitle, 48),
            Triple("DisplayFigure", VoloText.DisplayFigure, 36),
            Triple("DisplayLabel", VoloText.DisplayLabel, 28),
            Triple("TitleMd", VoloText.TitleMd, 18),
            Triple("TitleSm", VoloText.TitleSm, 16),
            Triple("BodyLg", VoloText.BodyLg, 16),
            Triple("BodyMd", VoloText.BodyMd, 14),
            Triple("LabelLg", VoloText.LabelLg, 14),
            Triple("LabelSm", VoloText.LabelSm, 12),
        )
        rows.forEach { (name, style, expectedSize) ->
            assertEquals("$name size", expectedSize.toFloat(), style.fontSize.value, 0.5f)
            assertTrue("$name has lineHeight", style.lineHeight.value > 0)
        }
    }

    @Test
    fun displayStylesUseBlackWeight() {
        assertEquals(FontWeight.W900, VoloText.DisplayHero.fontWeight)
        assertEquals(FontWeight.W900, VoloText.DisplayTitle.fontWeight)
    }

    @Test
    fun bodyStylesUseMediumWeight() {
        assertEquals(FontWeight.W500, VoloText.BodyLg.fontWeight)
        assertEquals(FontWeight.W500, VoloText.BodyMd.fontWeight)
    }

    @Test
    fun voloTypographyMapsDisplaySlot() {
        assertEquals(VoloType.display, VoloTypography.displayLarge)
    }
}
package com.nopeek.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NopeekTypographyTest {

    @Test
    fun everyStyleMatchesSpec() {
        val rows = listOf(
            Triple("DisplayHero", NopeekText.DisplayHero, 88),
            Triple("DisplayClock", NopeekText.DisplayClock, 60),
            Triple("DisplayTitle", NopeekText.DisplayTitle, 48),
            Triple("DisplayFigure", NopeekText.DisplayFigure, 36),
            Triple("DisplayLabel", NopeekText.DisplayLabel, 28),
            Triple("TitleMd", NopeekText.TitleMd, 18),
            Triple("TitleSm", NopeekText.TitleSm, 16),
            Triple("BodyLg", NopeekText.BodyLg, 16),
            Triple("BodyMd", NopeekText.BodyMd, 14),
            Triple("LabelLg", NopeekText.LabelLg, 14),
            Triple("LabelSm", NopeekText.LabelSm, 12),
        )
        rows.forEach { (name, style, expectedSize) ->
            assertEquals("$name size", expectedSize.toFloat(), style.fontSize.value, 0.5f)
            assertTrue("$name has lineHeight", style.lineHeight.value > 0)
        }
    }

    @Test
    fun displayStylesUseBlackWeight() {
        assertEquals(FontWeight.W900, NopeekText.DisplayHero.fontWeight)
        assertEquals(FontWeight.W900, NopeekText.DisplayTitle.fontWeight)
    }

    @Test
    fun bodyStylesUseMediumWeight() {
        assertEquals(FontWeight.W500, NopeekText.BodyLg.fontWeight)
        assertEquals(FontWeight.W500, NopeekText.BodyMd.fontWeight)
    }

    @Test
    fun nopeekTypographyMapsDisplaySlot() {
        assertEquals(NopeekType.display, NopeekTypography.displayLarge)
    }
}
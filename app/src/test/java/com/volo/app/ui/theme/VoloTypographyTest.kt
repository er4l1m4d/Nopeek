package com.volo.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

private data class Row(
    val name: String,
    val style: TextStyle,
    val size: Int,
    val line: Int,
    val weight: FontWeight?,
    val mono: Boolean,
)

class VoloTypographyTest {

    @Test
    fun everyStyleMatchesSpec() {
        val rows = listOf(
            Row("display", VoloType.display, 34, 38, FontWeight.Black, false),
            Row("headline", VoloType.headline, 28, 32, FontWeight.Black, false),
            Row("title", VoloType.title, 20, 26, FontWeight.Bold, false),
            Row("titleSmall", VoloType.titleSmall, 16, 22, FontWeight.Bold, false),
            Row("body", VoloType.body, 15, 22, FontWeight.Medium, false),
            Row("bodySmall", VoloType.bodySmall, 13, 18, FontWeight.Medium, false),
            Row("label", VoloType.label, 12, 16, FontWeight.Bold, false),
            Row("monoLarge", VoloType.monoLarge, 30, 34, FontWeight.Bold, true),
            Row("mono", VoloType.mono, 15, 20, FontWeight.Medium, true),
            Row("monoSmall", VoloType.monoSmall, 12, 16, FontWeight.Medium, true),
        )
        rows.forEach { r ->
            assertEquals("${r.name} size", r.size.toFloat(), r.style.fontSize.value, 0f)
            assertEquals("${r.name} line", r.line.toFloat(), r.style.lineHeight.value, 0f)
            assertEquals("${r.name} weight", r.weight, r.style.fontWeight)
            assertEquals("${r.name} family", if (r.mono) Mono else Satoshi, r.style.fontFamily)
        }
    }

    @Test
    fun labelHasOneSpLetterSpacing() {
        assertEquals(1.sp, VoloType.label.letterSpacing)
    }

    @Test
    fun voloTypographyMapsDisplaySlot() {
        assertEquals(VoloType.display, VoloTypography.displayLarge)
    }

    @Test
    fun voloTypographyMapsHeadlineSlot() {
        assertEquals(VoloType.headline, VoloTypography.headlineLarge)
    }
}

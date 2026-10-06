package com.volo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.volo.app.R

val BigShouldersDisplay = FontFamily(
    Font(R.font.big_shoulders_display_extrabold, FontWeight.ExtraBold),
    Font(R.font.big_shoulders_display_black, FontWeight.Black),
)

val SchibstedGrotesk = FontFamily(
    Font(R.font.schibsted_grotesk_medium, FontWeight.Medium),
    Font(R.font.schibsted_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.schibsted_grotesk_bold, FontWeight.Bold),
)

/** Display styles are not part of Material's Typography, so they live here. Use VoloText.DisplayClock etc. */
object VoloText {
    val DisplayMoment = TextStyle(fontFamily = BigShouldersDisplay, fontWeight = FontWeight.W900, fontSize = 168.sp, lineHeight = 151.sp)
    val DisplayHero = TextStyle(fontFamily = BigShouldersDisplay, fontWeight = FontWeight.W900, fontSize = 88.sp, lineHeight = 72.sp)
    val DisplayClock = TextStyle(fontFamily = BigShouldersDisplay, fontWeight = FontWeight.W900, fontSize = 60.sp, lineHeight = 54.sp)
    val DisplayTitle = TextStyle(fontFamily = BigShouldersDisplay, fontWeight = FontWeight.W900, fontSize = 48.sp, lineHeight = 44.sp)
    val DisplayFigure = TextStyle(fontFamily = BigShouldersDisplay, fontWeight = FontWeight.W900, fontSize = 36.sp, lineHeight = 34.sp)
    val DisplayLabel = TextStyle(fontFamily = BigShouldersDisplay, fontWeight = FontWeight.W900, fontSize = 28.sp, lineHeight = 28.sp)
    val TitleMd = TextStyle(fontFamily = SchibstedGrotesk, fontWeight = FontWeight.W700, fontSize = 18.sp, lineHeight = 24.sp)
    val TitleSm = TextStyle(fontFamily = SchibstedGrotesk, fontWeight = FontWeight.W700, fontSize = 16.sp, lineHeight = 22.sp)
    val BodyLg = TextStyle(fontFamily = SchibstedGrotesk, fontWeight = FontWeight.W500, fontSize = 16.sp, lineHeight = 24.sp)
    val BodyMd = TextStyle(fontFamily = SchibstedGrotesk, fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 20.sp)
    val LabelLg = TextStyle(fontFamily = SchibstedGrotesk, fontWeight = FontWeight.W700, fontSize = 14.sp, lineHeight = 20.sp)
    val LabelSm = TextStyle(fontFamily = SchibstedGrotesk, fontWeight = FontWeight.W600, fontSize = 12.sp, lineHeight = 16.sp)
}

/** VoloType aliases — legacy names kept as typealiases to the new VoloText style. */
object VoloType {
    val headline = VoloText.DisplayHero
    val title = VoloText.TitleMd
    val titleSmall = VoloText.TitleSm
    val body = VoloText.BodyLg
    val bodySmall = VoloText.BodyMd
    val label = VoloText.LabelLg
    val labelSmall = VoloText.LabelSm
    val display = VoloText.DisplayTitle
    val mono = VoloText.BodyMd
    val monoLarge = VoloText.BodyLg
    val monoSmall = VoloText.LabelSm
}

/** Material slots that Volo's Schibsted styles map onto, so stock components pick the right defaults. */
val VoloTypography: Typography by lazy {
    // Lazy, not eager: VoloText's styles read the file's font families
    // during VoloText.<clinit>, while VoloTypography reads VoloText's styles,
    // so whichever class initializes first must see the other ready.
    Typography(
        displayLarge = VoloText.DisplayTitle,
        displayMedium = VoloText.DisplayFigure,
        displaySmall = VoloText.DisplayLabel,
        titleLarge = VoloText.TitleMd,
        titleMedium = VoloText.TitleSm,
        bodyLarge = VoloText.BodyLg,
        bodyMedium = VoloText.BodyMd,
        labelLarge = VoloText.LabelLg,
        labelSmall = VoloText.LabelSm,
    )
}

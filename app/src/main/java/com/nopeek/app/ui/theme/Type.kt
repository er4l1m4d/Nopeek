package com.nopeek.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nopeek.app.R

val BigShouldersDisplay = FontFamily(
    Font(R.font.big_shoulders_display_extrabold, FontWeight.ExtraBold),
    Font(R.font.big_shoulders_display_black, FontWeight.Black),
)

val SchibstedGrotesk = FontFamily(
    Font(R.font.schibsted_grotesk_medium, FontWeight.Medium),
    Font(R.font.schibsted_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.schibsted_grotesk_bold, FontWeight.Bold),
)

/** Display styles are not part of Material's Typography, so they live here. Use NopeekText.DisplayClock etc. */
object NopeekText {
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

/** NopeekType aliases — legacy names kept as typealiases to the new NopeekText style. */
object NopeekType {
    val headline = NopeekText.DisplayHero
    val title = NopeekText.TitleMd
    val titleSmall = NopeekText.TitleSm
    val body = NopeekText.BodyLg
    val bodySmall = NopeekText.BodyMd
    val label = NopeekText.LabelLg
    val labelSmall = NopeekText.LabelSm
    val display = NopeekText.DisplayTitle
    val mono = NopeekText.BodyMd
    val monoLarge = NopeekText.BodyLg
    val monoSmall = NopeekText.LabelSm
}

/** Material slots that Nopeek's Schibsted styles map onto, so stock components pick the right defaults. */
val NopeekTypography: Typography by lazy {
    // Lazy, not eager: NopeekText's styles read the file's font families
    // during NopeekText.<clinit>, while NopeekTypography reads NopeekText's styles,
    // so whichever class initializes first must see the other ready.
    Typography(
        displayLarge = NopeekText.DisplayTitle,
        displayMedium = NopeekText.DisplayFigure,
        displaySmall = NopeekText.DisplayLabel,
        titleLarge = NopeekText.TitleMd,
        titleMedium = NopeekText.TitleSm,
        bodyLarge = NopeekText.BodyLg,
        bodyMedium = NopeekText.BodyMd,
        labelLarge = NopeekText.LabelLg,
        labelSmall = NopeekText.LabelSm,
    )
}

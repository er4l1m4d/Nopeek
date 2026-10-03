package com.volo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.volo.app.R

val Satoshi: FontFamily = FontFamily(
    Font(R.font.satoshi_variable, weight = FontWeight.W400),
    Font(R.font.satoshi_variable, weight = FontWeight.W500),
    Font(R.font.satoshi_variable, weight = FontWeight.W700),
    Font(R.font.satoshi_variable, weight = FontWeight.W900),
)

val Mono: FontFamily = FontFamily(
    Font(R.font.jetbrainsmono_regular, weight = FontWeight.W400),
    Font(R.font.jetbrainsmono_medium, weight = FontWeight.W500),
    Font(R.font.jetbrainsmono_bold, weight = FontWeight.W700),
)

object VoloType {
    val display: TextStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 38.sp,
    )
    val headline: TextStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 32.sp,
    )
    val title: TextStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )
    val titleSmall: TextStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    )
    val body: TextStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
    val bodySmall: TextStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val label: TextStyle = TextStyle(
        fontFamily = Satoshi,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp,
    )
    val monoLarge: TextStyle = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
    )
    val mono: TextStyle = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )
    val monoSmall: TextStyle = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}

// Lazily built: VoloType's styles read Satoshi/Mono during VoloType.<clinit>,
// while VoloTypography reads VoloType's styles during TypeKt.<clinit>. A plain
// eager val makes the result depend on which class initializes first — if
// VoloType is touched first, TypeKt.<clinit> runs nested inside
// VoloType.<clinit> and observes its not-yet-assigned style fields, freezing
// nulls into every Material slot (same JVM sees correct VoloType but null
// VoloTypography.displayLarge). `by lazy` defers the read until first access,
// when both classes are fully initialized, so all orders are correct.
val VoloTypography: Typography by lazy {
    Typography(
        displayLarge = VoloType.display,
        displayMedium = VoloType.display,
        displaySmall = VoloType.display,
        headlineLarge = VoloType.headline,
        headlineMedium = VoloType.headline,
        headlineSmall = VoloType.headline,
        titleLarge = VoloType.title,
        titleMedium = VoloType.titleSmall,
        titleSmall = VoloType.body,
        bodyLarge = VoloType.body,
        bodyMedium = VoloType.bodySmall,
        bodySmall = VoloType.bodySmall,
        labelLarge = VoloType.label,
        labelMedium = VoloType.label,
        labelSmall = VoloType.label,
    )
}

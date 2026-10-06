package com.nopeek.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Nopeek is dark-first. A light scheme is a future decision, not a gap. */
private val NopeekScheme = darkColorScheme(
    primary = NopeekRef.Volt, onPrimary = NopeekRef.Pitch,
    secondary = NopeekRef.Sky, onSecondary = NopeekRef.Pitch,
    tertiary = NopeekRef.Amber, onTertiary = NopeekRef.Pitch,
    error = NopeekRef.Coral, onError = NopeekRef.Pitch,
    background = NopeekRef.Pitch, onBackground = NopeekRef.Chalk,
    surface = NopeekRef.Pitch, onSurface = NopeekRef.Chalk,
    surfaceVariant = NopeekRef.Turf2, onSurfaceVariant = NopeekRef.Mute,
    surfaceContainer = NopeekRef.Turf, surfaceContainerHigh = NopeekRef.Turf2,
    outline = NopeekRef.Line, outlineVariant = NopeekRef.Line,
)

@Composable
fun NopeekTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNopeekColors provides NopeekDarkColors) {
        MaterialTheme(colorScheme = NopeekScheme, typography = NopeekTypography, shapes = NopeekShapes, content = content)
    }
}

/** Read extras with NopeekColors.safe / .opponent / .warning / .danger / .win */
object Nopeek {
    val colors: NopeekColorScheme @Composable get() = LocalNopeekColors.current
}

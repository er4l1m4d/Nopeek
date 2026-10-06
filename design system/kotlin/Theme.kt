package com.volo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Volo is dark-first. A light scheme is a future decision, not a gap. */
private val VoloScheme = darkColorScheme(
    primary = VoloRef.Volt, onPrimary = VoloRef.Pitch,
    secondary = VoloRef.Sky, onSecondary = VoloRef.Pitch,
    tertiary = VoloRef.Amber, onTertiary = VoloRef.Pitch,
    error = VoloRef.Coral, onError = VoloRef.Pitch,
    background = VoloRef.Pitch, onBackground = VoloRef.Chalk,
    surface = VoloRef.Pitch, onSurface = VoloRef.Chalk,
    surfaceVariant = VoloRef.Turf2, onSurfaceVariant = VoloRef.Mute,
    surfaceContainer = VoloRef.Turf, surfaceContainerHigh = VoloRef.Turf2,
    outline = VoloRef.Line, outlineVariant = VoloRef.Line,
)

@Composable
fun VoloTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalVoloColors provides VoloDarkColors) {
        MaterialTheme(colorScheme = VoloScheme, typography = VoloTypography, shapes = VoloShapes, content = content)
    }
}

/** Read extras with Volo.colors.you / .opponent / .warning / .danger / .win */
object Volo {
    val colors: VoloColors @Composable get() = LocalVoloColors.current
}

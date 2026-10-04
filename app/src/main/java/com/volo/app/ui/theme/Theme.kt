package com.volo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VoloDarkScheme = darkColorScheme(
    primary = VoloColors.copper,
    onPrimary = VoloColors.bg,
    primaryContainer = VoloColors.copperDeep,
    onPrimaryContainer = VoloColors.text,
    secondary = VoloColors.textMuted,
    onSecondary = VoloColors.bg,
    secondaryContainer = VoloColors.surfaceRaised,
    onSecondaryContainer = VoloColors.text,
    tertiary = VoloColors.safe,
    onTertiary = VoloColors.bg,
    tertiaryContainer = VoloColors.surfaceHigh,
    onTertiaryContainer = VoloColors.textMuted,
    error = VoloColors.fail,
    onError = VoloColors.bg,
    errorContainer = VoloColors.fail,
    onErrorContainer = VoloColors.bg,
    background = VoloColors.bg,
    onBackground = VoloColors.text,
    surface = VoloColors.surface,
    onSurface = VoloColors.text,
    surfaceVariant = VoloColors.surfaceRaised,
    onSurfaceVariant = VoloColors.textMuted,
    surfaceTint = VoloColors.copper,
    inverseSurface = VoloColors.text,
    inverseOnSurface = VoloColors.bg,
    outline = VoloColors.line,
    outlineVariant = VoloColors.surfaceHigh,
    scrim = VoloColors.bg,
    surfaceBright = VoloColors.surfaceHigh,
    surfaceDim = VoloColors.bg,
    surfaceContainer = VoloColors.surfaceRaised,
    surfaceContainerHigh = VoloColors.surfaceRaised,
    surfaceContainerHighest = VoloColors.surfaceHigh,
    surfaceContainerLow = VoloColors.surface,
    surfaceContainerLowest = VoloColors.bg,
)

@Composable
fun VoloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VoloDarkScheme, typography = VoloTypography, content = content)
}

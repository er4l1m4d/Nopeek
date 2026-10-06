package com.nopeek.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Tier 1 - reference tokens. Named by what they are. Never use these inside components. */
object NopeekRef {
    val Pitch = Color(0xFF10261B)
    val Turf = Color(0xFF173626)
    val Turf2 = Color(0xFF215039)
    val Line = Color(0xFF2E5942)
    val Chalk = Color(0xFFF1F0E6)
    val Mute = Color(0xFF93AE9E)
    val Volt = Color(0xFFD4FF00)
    val Sky = Color(0xFF8FD3FF)
    val Amber = Color(0xFFF5B335)
    val Coral = Color(0xFFFF6A4D)
    val Moss = Color(0xFF455413)
    val Pitch70 = Color(0xB310261B) // 70% Pitch - muted text on the full-bleed lose surface
}

/** Tier 2 - system tokens. Named by role. Components read only these. */
@Immutable
data class NopeekColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val you: Color,
    val opponent: Color,
    val warning: Color,
    val danger: Color,
    val onWin: Color,
    val onWinMuted: Color,
    val win: Color,
    val onLose: Color,
    val onLoseMuted: Color,
    val lose: Color,
    val onWarning: Color,
    val copper: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
)

val NopeekDarkColors = NopeekColorScheme(
    background = NopeekRef.Pitch,
    surface = NopeekRef.Pitch,
    surfaceRaised = NopeekRef.Turf,
    surfaceHigh = NopeekRef.Turf2,
    outline = NopeekRef.Line,
    onSurface = NopeekRef.Chalk,
    onSurfaceMuted = NopeekRef.Mute,
    primary = NopeekRef.Volt,
    onPrimary = NopeekRef.Pitch,
    you = NopeekRef.Volt,
    opponent = NopeekRef.Sky,
    warning = NopeekRef.Amber,
    danger = NopeekRef.Coral,
    onWin = NopeekRef.Pitch,
    onWinMuted = NopeekRef.Moss,
    win = NopeekRef.Volt,
    onLose = NopeekRef.Pitch,
    onLoseMuted = NopeekRef.Pitch70,
    lose = NopeekRef.Coral,
    onWarning = NopeekRef.Pitch,
    copper = NopeekRef.Amber,
    surfaceContainer = NopeekRef.Turf,
    surfaceContainerHigh = NopeekRef.Turf2,
)

val LocalNopeekColors = staticCompositionLocalOf { NopeekDarkColors }

/** Compatibility object: old-style non-composable color access. Uses NopeekDarkColors values. */
object NopeekColors {
    val bg = NopeekDarkColors.background
    val surface = NopeekDarkColors.surface
    val surfaceRaised = NopeekDarkColors.surfaceRaised
    val surfaceHigh = NopeekDarkColors.surfaceHigh
    val line = NopeekDarkColors.outline
    val text = NopeekDarkColors.onSurface
    val textMuted = NopeekDarkColors.onSurfaceMuted
    val copper = NopeekDarkColors.copper
    val copperDeep = NopeekDarkColors.warning
    val safe = NopeekDarkColors.you
    val risk = NopeekDarkColors.warning
    val fail = NopeekDarkColors.danger
}

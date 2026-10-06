package com.volo.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Tier 1 - reference tokens. Named by what they are. Never use these inside components. */
object VoloRef {
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
}

/** Tier 2 - system tokens. Named by role. Components read only these. */
@Immutable
data class VoloColorScheme(
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
    val copper: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
)

val VoloDarkColors = VoloColorScheme(
    background = VoloRef.Pitch,
    surface = VoloRef.Pitch,
    surfaceRaised = VoloRef.Turf,
    surfaceHigh = VoloRef.Turf2,
    outline = VoloRef.Line,
    onSurface = VoloRef.Chalk,
    onSurfaceMuted = VoloRef.Mute,
    primary = VoloRef.Volt,
    onPrimary = VoloRef.Pitch,
    you = VoloRef.Volt,
    opponent = VoloRef.Sky,
    warning = VoloRef.Amber,
    danger = VoloRef.Coral,
    onWin = VoloRef.Pitch,
    onWinMuted = VoloRef.Moss,
    win = VoloRef.Volt,
    copper = VoloRef.Amber,
    surfaceContainer = VoloRef.Turf,
    surfaceContainerHigh = VoloRef.Turf2,
)

val LocalVoloColors = staticCompositionLocalOf { VoloDarkColors }

/** Compatibility object: old-style non-composable color access. Uses VoloDarkColors values. */
object VoloColors {
    val bg = VoloDarkColors.background
    val surface = VoloDarkColors.surface
    val surfaceRaised = VoloDarkColors.surfaceRaised
    val surfaceHigh = VoloDarkColors.surfaceHigh
    val line = VoloDarkColors.outline
    val text = VoloDarkColors.onSurface
    val textMuted = VoloDarkColors.onSurfaceMuted
    val copper = VoloDarkColors.copper
    val copperDeep = VoloDarkColors.warning
    val safe = VoloDarkColors.you
    val risk = VoloDarkColors.warning
    val fail = VoloDarkColors.danger
}

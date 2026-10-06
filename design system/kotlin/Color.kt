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
    val Pitch70 = Color(0xB310261B) // 70% Pitch - muted text on the full-bleed lose surface
}

/** Tier 2 - system tokens. Named by role. Components read only these. */
@Immutable
data class VoloColors(
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
)

val VoloDarkColors = VoloColors(
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
    onLose = VoloRef.Pitch,
    onLoseMuted = VoloRef.Pitch70,
    lose = VoloRef.Coral,
    onWarning = VoloRef.Pitch,
)

val LocalVoloColors = staticCompositionLocalOf { VoloDarkColors }

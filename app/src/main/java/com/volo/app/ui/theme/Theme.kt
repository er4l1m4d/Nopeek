package com.volo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Paper = Color(0xFFF4F5F1)
val SurfaceWhite = Color.White
val SurfaceMuted = Color(0xFFE9ECE7)
val Ink = Color(0xFF172126)
val Muted = Color(0xFF607076)
val Line = Color(0xFFD7DEDA)
val Copper = Color(0xFFB7613F)
val CopperDeep = Color(0xFF87432F)
val Celadon = Color(0xFFC9DCCF)
val Clay = Color(0xFFA34F46)

val Duel = Color(0xFF10191D)
val DuelSurface = Color(0xFF1A272B)
val DuelLine = Color(0xFF334247)
val DuelText = Color(0xFFF2F4EE)
val DuelMuted = Color(0xFFA4B0AF)
val DuelCopper = Color(0xFFD17A54)
val DuelSafe = Color(0xFFB9D5C5)
val DuelRisk = Color(0xFFE0A268)
val DuelFail = Color(0xFFE08373)

private val VoloColors = lightColorScheme(
    primary = Copper,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    outline = Line
)

@Composable
fun VoloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VoloColors, content = content)
}

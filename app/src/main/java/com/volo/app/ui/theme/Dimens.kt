package com.volo.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object VoloSpace {
    val S4 = 4.dp
    val S8 = 8.dp
    val S12 = 12.dp
    val S16 = 16.dp
    val S20 = 20.dp
    val S24 = 24.dp
    val S32 = 32.dp
    val S48 = 48.dp
}

object VoloSize {
    val Touch = 48.dp
    val AppBar = 56.dp
    val BottomNav = 80.dp
    val Fab = 56.dp
    val ButtonStd = 48.dp
    val ButtonHero = 56.dp
    val AppTile = 48.dp
    val AppTileCompact = 40.dp
    val Margin = 16.dp
    val Gutter = 8.dp
    val GestureClearance = 24.dp
    val FabAboveNav = 16.dp
    val ListBottomPad = 88.dp
    val TrackShared = 12.dp
    val TrackMini = 6.dp
    val MarkerShared = 26.dp
    val MarkerMini = 12.dp
}

object VoloShape {
    val Sm = RoundedCornerShape(6.dp)
    val Md = RoundedCornerShape(14.dp)
    val Full = CircleShape
}

val VoloShapes = Shapes(
    small = VoloShape.Sm,
    medium = VoloShape.Md,
    large = VoloShape.Md,
)

object VoloMotion {
    const val EnterMs = 1300
    const val FillMs = 1600
    const val DrainMs = 2200
    const val CountUpMs = 2000
    // Easing: CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
}

object VoloDimens {
    val spaceXs = VoloSpace.S4
    val spaceSm = VoloSpace.S8
    val spaceMd = VoloSpace.S12
    val spaceLg = VoloSpace.S16
    val spaceXl = VoloSpace.S20
    val spaceXxl = VoloSpace.S24
    val spaceXxxl = VoloSpace.S32
    val radiusControl = VoloShape.Sm
    val radiusCard = VoloShape.Md
    val radiusSheet = VoloShape.Md
    val radiusPill = VoloShape.Full
}

package com.nopeek.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object NopeekSpace {
    val S4 = 4.dp
    val S8 = 8.dp
    val S12 = 12.dp
    val S16 = 16.dp
    val S20 = 20.dp
    val S24 = 24.dp
    val S32 = 32.dp
    val S48 = 48.dp
}

object NopeekSize {
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

object NopeekShape {
    val Sm = RoundedCornerShape(6.dp)
    val Md = RoundedCornerShape(14.dp)
    val Full = CircleShape
}

val NopeekShapes = Shapes(
    small = NopeekShape.Sm,
    medium = NopeekShape.Md,
    large = NopeekShape.Md,
)

object NopeekMotion {
    const val EnterMs = 1300
    const val FillMs = 1600
    const val DrainMs = 2200
    const val CountUpMs = 2000
    // Easing: CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
}

object NopeekDimens {
    val spaceXs = NopeekSpace.S4
    val spaceSm = NopeekSpace.S8
    val spaceMd = NopeekSpace.S12
    val spaceLg = NopeekSpace.S16
    val spaceXl = NopeekSpace.S20
    val spaceXxl = NopeekSpace.S24
    val spaceXxxl = NopeekSpace.S32
    val radiusControl = NopeekShape.Sm
    val radiusCard = NopeekShape.Md
    val radiusSheet = NopeekShape.Md
    val radiusPill = NopeekShape.Full
}

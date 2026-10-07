package com.nopeek.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nopeek.app.ui.icons.Phosphor

import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekShape
import com.nopeek.app.ui.theme.NopeekSize
import com.nopeek.app.ui.theme.NopeekSpace
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme

enum class PotState {
    FUNDED,
    WAITING,
    PAID_OUT,
}

@Composable
fun FixtureRow(
    title: String,
    subtitle: String,
    status: StatusVariant,
    statusLeading: ImageVector?,
    youFraction: Float,
    opponentFraction: Float,
    amount: String,
    timeLeft: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    appIcon: Painter? = null,
    appFallback: String? = "?",
    showTape: Boolean = true,
) {
    val colors = Nopeek.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = NopeekSpace.S16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NopeekSpace.S12),
    ) {
        AppTile(
            icon = appIcon,
            fallback = appFallback,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = NopeekText.TitleSm,
                color = colors.onSurface,
            )
            Text(
                text = subtitle,
                style = NopeekText.BodyMd,
                color = colors.onSurfaceMuted,
            )
            StatusLabel(
                variant = status,
                modifier = Modifier.padding(top = 6.dp),
                leading = statusLeading ?: status.glyph,
            )
            if (showTape) {
                MiniTape(
                    youFraction = youFraction,
                    opponentFraction = opponentFraction,
                )
            }
        }
        Column(
            modifier = Modifier.width(76.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = amount,
                style = NopeekText.DisplayFigure,
                color = colors.onSurface,
            )
            Text(
                text = timeLeft,
                style = NopeekText.BodyMd,
                color = colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
fun AppTile(
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    fallback: String? = "?",
    compact: Boolean = false,
) {
    val colors = Nopeek.colors
    val tile = if (compact) NopeekSize.AppTileCompact else NopeekSize.AppTile
    Box(
        modifier = modifier
            .size(tile)
            .clip(NopeekShape.Md)
            .background(colors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(tile),
            )
        } else {
            Text(
                text = fallback ?: "?",
                style = NopeekText.TitleSm,
                color = colors.onSurface,
            )
        }
    }
}

@Composable
fun MiniTape(
    youFraction: Float,
    opponentFraction: Float,
    modifier: Modifier = Modifier,
) {
    val colors = Nopeek.colors
    val you = animatedFraction(fractionOf(youFraction, 1f), durationMs = 0)
    val opp = animatedFraction(fractionOf(opponentFraction, 1f), durationMs = 0)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(top = NopeekSpace.S12),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 3.dp)
                .height(NopeekSize.TrackMini)
                .clip(NopeekShape.Full)
                .background(colors.surfaceHigh),
        )
        Box(
            modifier = Modifier
                .offset(x = maxWidth * you - NopeekSize.MarkerMini / 2)
                .size(NopeekSize.MarkerMini)
                .clip(NopeekShape.Full)
                .background(colors.you),
        )
        Box(
            modifier = Modifier
                .offset(x = maxWidth * opp - NopeekSize.MarkerMini / 2)
                .size(NopeekSize.MarkerMini)
                .clip(NopeekShape.Full)
                .background(colors.opponent),
        )
    }
}

@Composable
fun Scoreboard(
    youClock: String,
    youStatus: StatusVariant,
    youLeading: ImageVector?,
    opponentName: String,
    opponentClock: String,
    opponentStatus: StatusVariant,
    opponentLeading: ImageVector?,
    talkBack: String,
    modifier: Modifier = Modifier,
) {
    val colors = Nopeek.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(NopeekShape.Md)
            .background(colors.surfaceRaised)
            .padding(horizontal = NopeekSpace.S16, vertical = NopeekSpace.S20)
            .semantics { contentDescription = talkBack },
        horizontalArrangement = Arrangement.spacedBy(NopeekSpace.S8),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "You",
                style = NopeekText.BodyMd,
                color = colors.onSurfaceMuted,
            )
            Text(
                text = youClock,
                style = NopeekText.DisplayClock,
                color = colors.you,
            )
            StatusLabel(
                variant = youStatus,
                leading = youLeading ?: youStatus.glyph,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = opponentName,
                style = NopeekText.BodyMd,
                color = colors.onSurfaceMuted,
            )
            Text(
                text = opponentClock,
                style = NopeekText.DisplayClock,
                color = colors.opponent,
            )
            StatusLabel(
                variant = opponentStatus,
                leading = opponentLeading ?: opponentStatus.glyph,
            )
        }
    }
}

@Composable
fun SharedTape(
    youFraction: Float,
    opponentFraction: Float,
    limitLabel: String,
    leadText: String,
    behind: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Nopeek.colors
    val leadColor = if (behind) colors.warning else colors.you
    val you = animatedFraction(fractionOf(youFraction, 1f), durationMs = 0)
    val opp = animatedFraction(fractionOf(opponentFraction, 1f), durationMs = 0)
    val left = minOf(you, opp)
    val right = maxOf(you, opp)
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = leadText,
                style = NopeekText.DisplayLabel,
                color = leadColor,
            )
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val segX = maxWidth * left
            val segW = maxWidth * (right - left)
            Canvas(
                modifier = Modifier
                    .offset(x = segX)
                    .width(segW)
                    .height(10.dp),
            ) {
                val stroke = 2.dp.toPx()
                val w = size.width
                val h = size.height
                drawLine(
                    color = leadColor,
                    start = Offset(0f, stroke / 2f),
                    end = Offset(w, stroke / 2f),
                    strokeWidth = stroke,
                )
                drawLine(
                    color = leadColor,
                    start = Offset(stroke / 2f, 0f),
                    end = Offset(stroke / 2f, h),
                    strokeWidth = stroke,
                )
                drawLine(
                    color = leadColor,
                    start = Offset(w - stroke / 2f, 0f),
                    end = Offset(w - stroke / 2f, h),
                    strokeWidth = stroke,
                )
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(top = NopeekSpace.S8),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = 14.dp)
                    .height(NopeekSize.TrackShared)
                    .clip(NopeekShape.Full)
                    .background(colors.surfaceHigh),
            )
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * left, y = 14.dp)
                    .width(maxWidth * (right - left))
                    .height(NopeekSize.TrackShared)
                    .clip(NopeekShape.Full)
                    .background(leadColor),
            )
            MarkerDot(
                x = maxWidth * you - NopeekSize.MarkerShared / 2,
                color = colors.you,
            )
            MarkerDot(
                x = maxWidth * opp - NopeekSize.MarkerShared / 2,
                color = colors.opponent,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NopeekSpace.S8),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "0",
                style = NopeekText.LabelSm,
                color = colors.onSurfaceMuted,
            )
            Text(
                text = limitLabel,
                style = NopeekText.LabelSm,
                color = colors.danger,
            )
        }
    }
}

@Composable
private fun MarkerDot(
    x: Dp,
    color: Color,
) {
    val colors = Nopeek.colors
    Box(
        modifier = Modifier
            .offset(x = x, y = 7.dp)
            .size(NopeekSize.MarkerShared)
            .clip(NopeekShape.Full)
            .background(color)
            .border(4.dp, colors.background, NopeekShape.Full),
    )
}

@Composable
fun Pot(
    state: PotState,
    modifier: Modifier = Modifier,
) {
    val colors = Nopeek.colors
    val progress = animatedFraction(1f, durationMs = 1600)
    Canvas(
        modifier = modifier.size(56.dp, 70.dp),
    ) {
        val sx = size.width / 80f
        val sy = size.height / 100f
        withTransform({ scale(sx, sy) }) {
            val glass = Path().apply {
                moveTo(10f, 14f)
                lineTo(70f, 14f)
                lineTo(64f, 90f)
                quadraticTo(63f, 97f, 56f, 98f)
                lineTo(24f, 98f)
                quadraticTo(17f, 97f, 16f, 90f)
                lineTo(10f, 14f)
                close()
            }
            when (state) {
                PotState.FUNDED -> {
                    wave(glass, fillTop(progress, 100f, 30f), colors.opponent)
                    wave(glass, fillTop(progress, 100f, 55f), colors.you)
                }
                PotState.WAITING -> {
                    wave(glass, fillTop(progress, 100f, 55f), colors.you)
                }
                PotState.PAID_OUT -> Unit
            }
            drawPath(
                path = glass,
                color = colors.onSurface.copy(alpha = 0.4f), // glass outline, deliberately translucent
                style = Stroke(width = 4f),
            )
        }
    }
}

private fun DrawScope.wave(
    glass: Path,
    top: Float,
    color: Color,
) {
    val wavePath = Path().apply {
        moveTo(-20f, top)
        var x = -20f
        var up = false
        while (x < 100f) {
            quadraticTo(x + 10f, top + if (up) -4f else 4f, x + 20f, top)
            x += 20f
            up = !up
        }
        lineTo(100f, 100f)
        lineTo(-20f, 100f)
        close()
    }
    clipPath(glass, ClipOp.Intersect) {
        drawPath(path = wavePath, color = color)
    }
}

private fun fillTop(
    progress: Float,
    empty: Float,
    full: Float,
): Float = empty + (full - empty) * progress

/**
 * Label/value line. The defaults assume a dark surface: on a loud full-bleed fill (win, lose,
 * warning) the caller must pass the matching foreground tokens — `labelColor = colors.onWinMuted,
 * valueColor = colors.onWin` — because Chalk on volt measures 1.01:1. See WonScreen/LostScreen.
 */
@Composable
fun ReceiptRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
    labelColor: Color? = null,
    valueColor: Color? = null,
) {
    val colors = Nopeek.colors
    val resolvedLabel = labelColor ?: colors.onSurfaceMuted
    val resolvedValue = valueColor ?: colors.onSurface
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = NopeekSize.Touch)
    val clickable = if (onClick != null) {
        base
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
    } else {
        base
    }
    Row(
        modifier = clickable,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NopeekSpace.S12),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = NopeekText.BodyMd,
            color = resolvedLabel,
        )
        Text(
            text = value,
            style = NopeekText.LabelLg,
            color = resolvedValue,
        )
        if (chevron) {
            Icon(
                imageVector = Phosphor.ArrowRight,
                contentDescription = null,
                tint = resolvedLabel,
            )
        }
    }
}

@Composable
fun InviteBanner(
    title: String,
    subtitle: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Nopeek.colors
    Surface(
        modifier = modifier,
        shape = NopeekShape.Md,
        color = Color.Transparent,
        contentColor = colors.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, colors.outline),
    ) {
        Column(
            modifier = Modifier.padding(NopeekSpace.S16),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NopeekSpace.S12),
            ) {
                AppTile(
                    fallback = "X",
                    compact = true,
                )
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        style = NopeekText.TitleSm,
                        color = colors.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = NopeekText.BodyMd,
                        color = colors.onSurfaceMuted,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = NopeekSpace.S12),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    label = "Decline",
                    onClick = onDecline,
                )
                PrimaryButton(
                    label = "Accept",
                    onClick = onAccept,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun FixtureRowPreview() {
    NopeekTheme {
        Column(
            modifier = Modifier.padding(NopeekSpace.S16),
        ) {
            FixtureRow(
                title = "vs alex.skr",
                subtitle = "TikTok, 30 min a day",
                status = usageVariant(UsageState.PASSING),
                statusLeading = Phosphor.CheckCircle,
                youFraction = 0.42f,
                opponentFraction = 0.62f,
                amount = "$10",
                timeLeft = "11 min left",
                onClick = {},
                appFallback = "TT",
                showTape = false,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun ScoreboardPreview() {
    NopeekTheme {
        Column(
            modifier = Modifier.padding(NopeekSpace.S16),
        ) {
            Scoreboard(
                youClock = "18:42",
                youStatus = usageVariant(UsageState.PASSING),
                youLeading = Phosphor.CheckCircle,
                opponentName = "alex.skr",
                opponentClock = "27:10",
                opponentStatus = usageVariant(UsageState.AT_RISK),
                opponentLeading = Phosphor.WarningCircle,
                talkBack = "You, 18 minutes 42 seconds, safe. alex.skr, 27 minutes 10 seconds, at risk.",
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun TapePotReceiptPreview() {
    NopeekTheme {
        Column(
            modifier = Modifier.padding(NopeekSpace.S16),
            verticalArrangement = Arrangement.spacedBy(NopeekSpace.S16),
        ) {
            SharedTape(
                youFraction = 0.622f,
                opponentFraction = 0.906f,
                limitLabel = "30 min limit",
                leadText = "8:28 ahead",
                behind = false,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(NopeekSpace.S16),
            ) {
                Pot(state = PotState.FUNDED)
                Pot(state = PotState.WAITING)
                Pot(state = PotState.PAID_OUT)
            }
            ReceiptRow(
                label = "Your stake",
                value = "−$5.00",
            )
        }
    }
}

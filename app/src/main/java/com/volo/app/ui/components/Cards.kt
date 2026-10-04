package com.volo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

// Trailing glyph is the vendored Phosphor ArrowRight (Regular); the T5
// Material stand-in is gone — no Material icon import remains in this file.

@Composable
fun DuelCard(
    status: StatusVariant,
    title: String,
    opponent: String,
    usageText: String,
    stakeText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(VoloDimens.radiusCard),
        color = VoloColors.surfaceRaised,
        contentColor = VoloColors.text,
        border = BorderStroke(1.dp, VoloColors.line),
    ) {
        Row(
            modifier = Modifier.padding(VoloDimens.spaceXl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
                ) {
                    // Wraps StatusChip (not a bespoke chip) so the leading
                    // Phosphor icon arriving in Task 7 appears automatically.
                    StatusChip(variant = status)
                    Text(
                        text = "ACTIVE DUEL",
                        style = VoloType.label,
                        color = VoloColors.textMuted,
                    )
                }
                Text(
                    text = title,
                    style = VoloType.title,
                    color = VoloColors.text,
                )
                Text(
                    text = opponent,
                    style = VoloType.bodySmall,
                    color = VoloColors.textMuted,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
                ) {
                    Text(
                        text = usageText,
                        style = VoloType.mono,
                        color = VoloColors.text,
                    )
                    Text(
                        text = stakeText,
                        style = VoloType.mono,
                        color = VoloColors.textMuted,
                    )
                }
            }
            Icon(
                imageVector = Phosphor.ArrowRight,
                contentDescription = null,
                tint = VoloColors.copper,
            )
        }
    }
}

@Composable
fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(VoloDimens.radiusCard),
        color = VoloColors.surface,
        contentColor = VoloColors.text,
        border = BorderStroke(1.dp, VoloColors.line),
    ) {
        Column(
            modifier = Modifier.padding(VoloDimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs),
        ) {
            Text(
                text = value,
                style = VoloType.title,
                color = VoloColors.text,
            )
            Text(
                text = label,
                style = VoloType.bodySmall,
                color = VoloColors.textMuted,
            )
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: @Composable () -> Unit = {},
) {
    // Extra optional trailing slot only: the brief's exact (title, subtitle,
    // modifier, onClick) parameters stay in order, so existing callers are
    // unaffected. The slot carries the brief's "leading Phosphor icon slot" —
    // Task 8+ screens pass the vendored Phosphor icon once Task 7 lands.
    @Composable
    fun Content() {
        Row(
            modifier = Modifier.padding(VoloDimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
        ) {
            leading()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs),
            ) {
                Text(
                    text = title,
                    style = VoloType.titleSmall,
                    color = VoloColors.text,
                )
                Text(
                    text = subtitle,
                    style = VoloType.bodySmall,
                    color = VoloColors.textMuted,
                )
            }
        }
    }

    val shape = RoundedCornerShape(VoloDimens.radiusCard)
    val container = VoloColors.safe.copy(alpha = 0.15f)
    val border = BorderStroke(1.dp, VoloColors.line)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = container,
            contentColor = VoloColors.text,
            border = border,
        ) {
            Content()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = container,
            contentColor = VoloColors.text,
            border = border,
        ) {
            Content()
        }
    }
}

@Composable
fun RowCard(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    @Composable
    fun Content() {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(VoloDimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs),
            ) {
                Text(
                    text = title,
                    style = VoloType.titleSmall,
                    color = VoloColors.text,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = VoloType.bodySmall,
                        color = VoloColors.textMuted,
                    )
                }
            }
            trailing()
        }
    }

    val shape = RoundedCornerShape(VoloDimens.radiusCard)
    val border = BorderStroke(1.dp, VoloColors.line)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = VoloColors.surface,
            contentColor = VoloColors.text,
            border = border,
        ) {
            Content()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = VoloColors.surface,
            contentColor = VoloColors.text,
            border = border,
        ) {
            Content()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun DuelCardPreview() {
    VoloTheme {
        DuelCard(
            status = lifecycleVariant("ACTIVE"),
            title = "Instagram",
            opponent = "vs maya.sol",
            usageText = "18m / 30m",
            stakeText = "5.00 USDC",
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun MetricCardPreview() {
    VoloTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                value = "18m",
                label = "of 30m used",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                value = "5 days",
                label = "current streak",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun InfoCardPreview() {
    VoloTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoCard(
                title = "Usage access granted",
                subtitle = "Screen-time data is flowing for this duel.",
                modifier = Modifier.fillMaxWidth(),
                leading = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(VoloColors.copper.copy(alpha = 0.25f)),
                    )
                },
            )
            InfoCard(
                title = "Tap for details",
                subtitle = "Clickable variant with an onClick handler.",
                modifier = Modifier.fillMaxWidth(),
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun RowCardPreview() {
    VoloTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowCard(
                title = "Instagram duel",
                subtitle = "vs maya.sol · 5.00 USDC",
                modifier = Modifier.fillMaxWidth(),
                onClick = {},
                trailing = {
                    StatusChip(variant = usageVariant(UsageState.PASSING))
                },
            )
            RowCard(
                title = "Wallet settings",
                subtitle = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

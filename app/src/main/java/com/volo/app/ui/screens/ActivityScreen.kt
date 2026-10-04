package com.volo.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.components.DailyBarChart
import com.volo.app.ui.components.SectionHeader
import com.volo.app.ui.components.UsageBarItem
import com.volo.app.ui.components.UsageBreakdownBars
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

private data class ActivityEvent(
    val label: String,
    val time: String,
    val tx: String?,
    val icon: ImageVector,
)

private val DemoEvents = listOf(
    ActivityEvent(
        label = "Duel created",
        time = "Today, 8:42 PM",
        tx = null,
        icon = Phosphor.Plus,
    ),
    ActivityEvent(
        label = "Joined duel vs maya.sol",
        time = "Today, 7:18 PM",
        tx = null,
        icon = Phosphor.Flag,
    ),
    ActivityEvent(
        label = "Usage report submitted",
        time = "Today, 6:59 PM",
        tx = "tx 5z9m…3fK2",
        icon = Phosphor.CheckCircle,
    ),
    ActivityEvent(
        label = "Usage access granted",
        time = "Today, 6:30 PM",
        tx = null,
        icon = Phosphor.LockKey,
    ),
    ActivityEvent(
        label = "Settlement approved",
        time = "Sep 21, 4:02 PM",
        tx = "tx 8Qdx…9pL4",
        icon = Phosphor.Trophy,
    ),
    ActivityEvent(
        label = "Stake locked · 5.00 USDC",
        time = "Sep 21, 3:58 PM",
        tx = "tx 2Hwq…7tR1",
        icon = Phosphor.Wallet,
    ),
)

private val DemoBreakdown = listOf(
    UsageBarItem(label = "Instagram", duration = "18m", fraction = 0.6f),
    UsageBarItem(label = "YouTube", duration = "9m", fraction = 0.3f),
    UsageBarItem(label = "X", duration = "3m", fraction = 0.1f),
)

/**
 * Activity timeline on the dark design system (demo data only — no
 * usage/event wiring). Date header, [DailyBarChart] card,
 * [UsageBreakdownBars] card, then chronological event rows in the
 * [RowCard] visual style (surface card, Phosphor event icon, plain
 * Satoshi label, mono timestamp, optional mono tx fragment).
 */
@Composable
fun ActivityScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(VoloColors.bg),
        contentPadding = PaddingValues(
            horizontal = VoloDimens.spaceXl,
            vertical = VoloDimens.spaceXxl,
        ),
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXl),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs)) {
                Text(
                    text = "Tuesday, September 29",
                    style = VoloType.monoSmall,
                    color = VoloColors.textMuted,
                )
                Text(
                    text = "Activity",
                    style = VoloType.headline,
                    color = VoloColors.text,
                )
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(VoloDimens.radiusCard),
                color = VoloColors.surface,
                contentColor = VoloColors.text,
                border = BorderStroke(1.dp, VoloColors.line),
            ) {
                Column(
                    modifier = Modifier.padding(VoloDimens.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
                ) {
                    SectionHeader(
                        eyebrow = "THIS WEEK",
                        title = "Screen time",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DailyBarChart(
                        values = listOf(12f, 28f, 18f, 30f, 9f, 22f, 15f),
                        labels = listOf("M", "T", "W", "T", "F", "S", "S"),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(VoloDimens.radiusCard),
                color = VoloColors.surface,
                contentColor = VoloColors.text,
                border = BorderStroke(1.dp, VoloColors.line),
            ) {
                Column(
                    modifier = Modifier.padding(VoloDimens.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
                ) {
                    SectionHeader(
                        eyebrow = "BY APP",
                        title = "Usage breakdown",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UsageBreakdownBars(
                        items = DemoBreakdown,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            SectionHeader(
                eyebrow = "HISTORY",
                title = "Events",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(DemoEvents) { event ->
            ActivityEventRow(
                event = event,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActivityEventRow(
    event: ActivityEvent,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(VoloDimens.radiusCard),
        color = VoloColors.surface,
        contentColor = VoloColors.text,
        border = BorderStroke(1.dp, VoloColors.line),
    ) {
        Row(
            modifier = Modifier.padding(VoloDimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(VoloColors.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = event.icon,
                    contentDescription = event.label,
                    tint = VoloColors.copper,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs),
            ) {
                Text(
                    text = event.label,
                    style = VoloType.titleSmall,
                    color = VoloColors.text,
                )
                Text(
                    text = event.time,
                    style = VoloType.monoSmall,
                    color = VoloColors.textMuted,
                )
                if (event.tx != null) {
                    Text(
                        text = event.tx,
                        style = VoloType.monoSmall,
                        color = VoloColors.textMuted,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun ActivityScreenPreview() {
    VoloTheme {
        ActivityScreen()
    }
}

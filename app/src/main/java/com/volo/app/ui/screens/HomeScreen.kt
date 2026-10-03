package com.volo.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import com.volo.app.ui.components.DailyBarChart
import com.volo.app.ui.components.DuelCard
import com.volo.app.ui.components.EmptyState
import com.volo.app.ui.components.InfoCard
import com.volo.app.ui.components.MetricCard
import com.volo.app.ui.components.SectionHeader
import com.volo.app.ui.components.UsageState
import com.volo.app.ui.components.usageVariant
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

@Composable
fun HomeScreen(
    onOpenDuel: () -> Unit,
    hasActiveDuel: Boolean = true,
) {
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
                    text = "Stay in the frame.",
                    style = VoloType.headline,
                    color = VoloColors.text,
                )
            }
        }
        if (hasActiveDuel) {
            item {
                DuelCard(
                    status = usageVariant(UsageState.PASSING),
                    title = "No doomscrolling after 9pm",
                    opponent = "Instagram \u00B7 with Maya",
                    usageText = "18m / 30m",
                    stakeText = "5.00 USDC locked",
                    onClick = onOpenDuel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            item {
                EmptyState(
                    icon = Phosphor.Trophy,
                    title = "No active duel",
                    message = "You have no active duel right now. Create one to start a new challenge.",
                    actionLabel = "Create a duel",
                    onAction = onOpenDuel,
                    modifier = Modifier.fillMaxWidth(),
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
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
            ) {
                MetricCard(
                    value = "18m",
                    label = "of 30m used",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                MetricCard(
                    value = "5 days",
                    label = "current streak",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        item {
            InfoCard(
                title = "Usage access is on",
                subtitle = "Your next report is due in 42 minutes.",
                modifier = Modifier.fillMaxWidth(),
                leading = {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(VoloColors.safe.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Phosphor.CheckCircle,
                            contentDescription = null,
                            tint = VoloColors.safe,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun HomeScreenPreview() {
    VoloTheme {
        HomeScreen(onOpenDuel = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun HomeScreenEmptyPreview() {
    VoloTheme {
        HomeScreen(onOpenDuel = {}, hasActiveDuel = false)
    }
}

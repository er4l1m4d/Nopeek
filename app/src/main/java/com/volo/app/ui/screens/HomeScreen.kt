package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.volo.app.ui.components.FixtureRow
import com.volo.app.ui.components.Pot
import com.volo.app.ui.components.PotState
import com.volo.app.ui.components.PrimaryButton
import com.volo.app.ui.components.StatusLabel
import com.volo.app.ui.components.UsageState
import com.volo.app.ui.components.lifecycleVariant
import com.volo.app.ui.components.usageVariant
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.Volo
import com.volo.app.ui.theme.VoloShape
import com.volo.app.ui.theme.VoloText
import com.volo.app.ui.theme.VoloTheme

@Composable
fun HomeScreen(onOpenDuel: () -> Unit, hasActiveDuel: Boolean = true) {
    val colors = Volo.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Nopeek", style = VoloText.DisplayFigure, color = colors.onSurface)
            Icon(imageVector = Phosphor.Flag, contentDescription = "Duels", tint = colors.onSurfaceMuted, modifier = Modifier.size(28.dp))
        }
        if (hasActiveDuel) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = VoloShape.Md,
                    color = colors.surfaceRaised,
                    onClick = onOpenDuel,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusLabel(variant = usageVariant(UsageState.PASSING), leading = Phosphor.CheckCircle)
                            Text(text = "  · 18:42 left", style = VoloText.BodyMd, color = colors.onSurface)
                        }
                        Text(text = "TikTok · 30m limit", style = VoloText.TitleSm, color = colors.onSurface, modifier = Modifier.padding(top = 8.dp))
                        Text(text = "vs alex.skr", style = VoloText.BodyMd, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 4.dp))
                        Pot(state = PotState.WAITING, modifier = Modifier.padding(top = 12.dp))
                        PrimaryButton(label = "View duel", onClick = onOpenDuel, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), hero = true)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.padding(24.dp),
                    shape = VoloShape.Md,
                    color = colors.surfaceRaised,
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Phosphor.PlusBold, contentDescription = null, tint = colors.primary, modifier = Modifier.size(48.dp))
                        Text(text = "Nothing live today", style = VoloText.TitleMd, color = colors.onSurface, modifier = Modifier.padding(top = 12.dp))
                        Text(
                            text = "Start a duel and your allowance, stakes and record will show up here.",
                            style = VoloText.BodyMd,
                            color = colors.onSurfaceMuted,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        PrimaryButton(label = "Go to duels", onClick = onOpenDuel, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                    }
                }
            }
        }
        Text(text = "Recent", style = VoloText.TitleMd, color = colors.onSurface, modifier = Modifier.padding(top = 24.dp, start = 16.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(DemoRecent) { duel ->
                FixtureRow(
                    title = duel.title,
                    subtitle = duel.subtitle,
                    status = duel.status,
                    statusLeading = duel.icon,
                    youFraction = duel.youFraction,
                    opponentFraction = duel.opponentFraction,
                    amount = duel.amount,
                    timeLeft = duel.timeLeft,
                    onClick = {},
                )
            }
        }
    }
}

private data class Recent(
    val title: String,
    val subtitle: String,
    val status: com.volo.app.ui.components.StatusVariant,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val youFraction: Float,
    val opponentFraction: Float,
    val amount: String,
    val timeLeft: String,
)

private val DemoRecent = listOf(
    Recent("vs alex.skr", "TikTok · 30m a day", usageVariant(UsageState.PASSING), Phosphor.CheckCircle, 0.42f, 0.62f, "$10", "11 min left"),
    Recent("Mon · vs marcus.skr", "Won +$5", lifecycleVariant("SETTLED"), Phosphor.Trophy, 1f, 0.3f, "+$5", "Mon"),
    Recent("Sun · vs jay.skr", "Lost -$5", lifecycleVariant("SETTLED"), Phosphor.XCircle, 0.7f, 0.95f, "-$5", "Sun"),
)
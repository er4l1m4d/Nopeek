package com.nopeek.app.ui.screens

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
import com.nopeek.app.ui.components.FixtureRow
import com.nopeek.app.ui.components.Pot
import com.nopeek.app.ui.components.PotState
import com.nopeek.app.ui.components.PrimaryButton
import com.nopeek.app.ui.components.StatusLabel
import com.nopeek.app.ui.components.UsageState
import com.nopeek.app.ui.components.lifecycleVariant
import com.nopeek.app.ui.components.usageVariant
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekShape
import com.nopeek.app.ui.theme.NopeekSpace
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme

@Composable
fun HomeScreen(
    onOpenDuel: () -> Unit,
    onGoToDuels: () -> Unit = onOpenDuel,
    hasActiveDuel: Boolean = true,
) {
    val colors = Nopeek.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S16, start = NopeekSpace.S16, end = NopeekSpace.S16), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Nopeek", style = NopeekText.DisplayFigure, color = colors.onSurface)
            // Decorative: the Duels destination is already one tap away in the bar below, so an
            // accessible name here would announce a second, dead "Duels" control.
            Icon(imageVector = Phosphor.Flag, contentDescription = null, tint = colors.onSurfaceMuted, modifier = Modifier.size(28.dp))
        }
        if (hasActiveDuel) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S16, start = NopeekSpace.S16, end = NopeekSpace.S16)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NopeekShape.Md,
                    color = colors.surfaceRaised,
                ) {
                    Column(modifier = Modifier.padding(NopeekSpace.S16)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(NopeekSpace.S8),
                        ) {
                            StatusLabel(variant = usageVariant(UsageState.PASSING))
                            Text(text = "18:42 left", style = NopeekText.BodyMd, color = colors.onSurface)
                        }
                        Text(text = "TikTok · 30m limit", style = NopeekText.TitleSm, color = colors.onSurface, modifier = Modifier.padding(top = NopeekSpace.S8))
                        Text(text = "vs alex.skr", style = NopeekText.BodyMd, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = NopeekSpace.S4))
                        Pot(state = PotState.WAITING, modifier = Modifier.padding(top = NopeekSpace.S12))
                        // The card itself stays inert: one action per surface, so TalkBack reports a
                        // single "View duel" and no tap lands on the wrong target.
                        PrimaryButton(label = "View duel", onClick = onOpenDuel, modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S12), hero = true)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S32), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.padding(NopeekSpace.S24),
                    shape = NopeekShape.Md,
                    color = colors.surfaceRaised,
                ) {
                    Column(modifier = Modifier.padding(NopeekSpace.S24), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Phosphor.PlusBold, contentDescription = null, tint = colors.primary, modifier = Modifier.size(48.dp))
                        Text(text = "Nothing live today", style = NopeekText.TitleMd, color = colors.onSurface, modifier = Modifier.padding(top = NopeekSpace.S12))
                        Text(
                            text = "Start a duel and your allowance, stakes and record will show up here.",
                            style = NopeekText.BodyMd,
                            color = colors.onSurfaceMuted,
                            modifier = Modifier.padding(top = NopeekSpace.S8),
                        )
                        PrimaryButton(label = "Go to duels", onClick = onGoToDuels, modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S16))
                    }
                }
            }
        }
        Text(text = "Recent", style = NopeekText.TitleMd, color = colors.onSurface, modifier = Modifier.padding(top = NopeekSpace.S24, start = NopeekSpace.S16))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S8),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = NopeekSpace.S16, vertical = NopeekSpace.S4),
            verticalArrangement = Arrangement.spacedBy(NopeekSpace.S8),
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
    val status: com.nopeek.app.ui.components.StatusVariant,
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
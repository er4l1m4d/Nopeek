package com.nopeek.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nopeek.app.ui.components.FixtureRow
import com.nopeek.app.ui.components.TapIconButton
import com.nopeek.app.ui.components.UsageState
import com.nopeek.app.ui.components.lifecycleVariant
import com.nopeek.app.ui.components.usageVariant
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size

private data class OpenDuelDemo(
    val app: String,
    val limit: String,
    val window: String,
    val stakePool: String,
)

private val DemoOpenDuels = listOf(
    OpenDuelDemo("Instagram", "30m", "24h", "10.00 USDC"),
    OpenDuelDemo("TikTok", "60m", "48h", "6.00 USDC"),
    OpenDuelDemo("YouTube", "15m", "12h", "4.00 USDC"),
)

@Composable
fun OpenDuelsScreen(onBack: () -> Unit, hasOpenDuels: Boolean = true) {
    val colors = Nopeek.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TapIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(imageVector = Phosphor.ArrowLeft, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(24.dp))
            }
            Text(text = "Open duels", style = NopeekText.DisplayTitle, color = colors.onSurface, modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            text = "Anyone can take these. The first to accept owns half the stake.",
            style = NopeekText.BodyLg,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (!hasOpenDuels) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No open duels right now", style = NopeekText.TitleMd, color = colors.onSurfaceMuted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(DemoOpenDuels) { duel ->
                    FixtureRow(
                        title = duel.app,
                        subtitle = "${duel.limit} a day · ${duel.window} window",
                        status = lifecycleVariant("CREATED"),
                        statusLeading = Phosphor.ArrowRight,
                        youFraction = 0f,
                        opponentFraction = 0f,
                        amount = "$${duel.stakePool.split(" ").first()}",
                        timeLeft = "${duel.stakePool.split(" ").first()} stake",
                        onClick = {},
                    )
                }
            }
        }
    }
}
package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.components.EmptyState
import com.volo.app.ui.components.PrimaryButton
import com.volo.app.ui.components.RowCard
import com.volo.app.ui.components.TapIconButton
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

private data class OpenDuelDemo(
    val app: String,
    val limit: String,
    val window: String,
    val stakePool: String,
)

private val DemoOpenDuels = listOf(
    OpenDuelDemo(
        app = "Instagram",
        limit = "30m limit",
        window = "24h window",
        stakePool = "10.00 USDC pool",
    ),
    OpenDuelDemo(
        app = "TikTok",
        limit = "60m limit",
        window = "48h window",
        stakePool = "6.00 USDC pool",
    ),
    OpenDuelDemo(
        app = "YouTube",
        limit = "15m limit",
        window = "12h window",
        stakePool = "4.00 USDC pool",
    ),
)

/**
 * Open duels list on the dark design system (demo data only — no
 * wallet/matching wiring). One [RowCard] per duel (app + limit/window,
 * mono stake pool) with a full-width join [PrimaryButton] beneath it;
 * [EmptyState] when there is nothing to join.
 */
@Composable
fun OpenDuelsScreen(
    onBack: () -> Unit,
    hasOpenDuels: Boolean = true,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(VoloColors.bg),
        contentPadding = PaddingValues(
            horizontal = VoloDimens.spaceXl,
            vertical = VoloDimens.spaceXxl,
        ),
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceLg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd)) {
                TapIconButton(
                    contentDescription = "Back",
                    onClick = onBack,
                ) {
                    Icon(
                        imageVector = Phosphor.ArrowLeft,
                        contentDescription = null,
                        tint = VoloColors.text,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = "Open duels",
                    style = VoloType.headline,
                    color = VoloColors.text,
                )
                Text(
                    text = "Join an open challenge and match the pooled stake.",
                    style = VoloType.bodySmall,
                    color = VoloColors.textMuted,
                )
            }
        }
        if (!hasOpenDuels) {
            item {
                EmptyState(
                    icon = Phosphor.Trophy,
                    title = "No open duels",
                    message = "There are no open challenges right now. Create one to start a new duel.",
                    actionLabel = "Create a duel",
                    onAction = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(DemoOpenDuels) { duel ->
                Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm)) {
                    RowCard(
                        title = duel.app,
                        subtitle = "${duel.limit} · ${duel.window}",
                        modifier = Modifier.fillMaxWidth(),
                        trailing = {
                            Text(
                                text = duel.stakePool,
                                style = VoloType.monoSmall,
                                color = VoloColors.text,
                                maxLines = 1,
                            )
                        },
                    )
                    PrimaryButton(
                        label = "Join",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun OpenDuelsScreenPreview() {
    VoloTheme {
        OpenDuelsScreen(onBack = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun OpenDuelsScreenEmptyPreview() {
    VoloTheme {
        OpenDuelsScreen(onBack = {}, hasOpenDuels = false)
    }
}

package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.components.MetricRow
import com.volo.app.ui.components.OpponentUsageBar
import com.volo.app.ui.components.PrimaryButton
import com.volo.app.ui.components.StatusChip
import com.volo.app.ui.components.TapIconButton
import com.volo.app.ui.components.UsageRing
import com.volo.app.ui.components.UsageState
import com.volo.app.ui.components.usageVariant
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

@Composable
fun DuelScreen(pubkey: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoloColors.bg)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = VoloDimens.spaceXl,
                vertical = VoloDimens.spaceXxl,
            ),
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceLg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
        ) {
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
                text = pubkey,
                style = VoloType.monoSmall,
                color = VoloColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs)) {
            Text(
                text = "No doomscrolling after 9pm",
                style = VoloType.display,
                color = VoloColors.text,
            )
            Text(
                text = "Instagram · with Maya",
                style = VoloType.body,
                color = VoloColors.textMuted,
            )
        }
        StatusChip(
            variant = usageVariant(UsageState.PASSING),
            leading = Phosphor.CheckCircle,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
        ) {
            UsageRing(
                usedSeconds = 18 * 60,
                limitSeconds = 30 * 60,
                centerText = "18m / 30m",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VoloDimens.spaceXxxl),
            )
            Text(
                text = "ENDS IN 02:14:33",
                style = VoloType.mono,
                color = VoloColors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        OpponentUsageBar(
            youLabel = "YOU",
            youText = "18m / 30m",
            youFraction = 0.6f,
            opponentLabel = "MAYA",
            opponentText = "22m / 30m",
            opponentFraction = 22f / 30f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Last report 8m ago",
            style = VoloType.monoSmall,
            color = VoloColors.textMuted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm)) {
            MetricRow(
                title = "Stake",
                value = "5.00 USDC",
                modifier = Modifier.fillMaxWidth(),
            )
            MetricRow(
                title = "Window",
                value = "24H",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(VoloDimens.spaceSm))
        PrimaryButton(
            label = "Check usage",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun DuelScreenPreview() {
    VoloTheme {
        DuelScreen(pubkey = "Hx8vQ9demoChallenge42", onBack = {})
    }
}

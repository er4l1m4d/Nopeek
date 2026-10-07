package com.nopeek.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nopeek.app.ui.components.OutlineButton
import com.nopeek.app.ui.components.Pot
import com.nopeek.app.ui.components.PotState
import com.nopeek.app.ui.components.PrimaryButton
import com.nopeek.app.ui.components.ReceiptRow
import com.nopeek.app.ui.components.TapIconButton
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekSpace
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme

@Composable
fun WonScreen(
    stake: String,
    pot: String,
    youMinutes: String,
    opponentMinutes: String,
    opponentName: String,
    tx: String,
    onShare: () -> Unit,
    onRematch: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = Nopeek.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.win)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            // Inside the scroll: the Share button is the last element and must clear the system bar.
            .navigationBarsPadding()
            .padding(horizontal = NopeekSpace.S16),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S8),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "WON",
                style = NopeekText.DisplayMoment,
                color = colors.onWin,
                // The moment type is deliberately huge; at large system font scales it yields space
                // rather than shoving the dismiss control off-screen.
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f, fill = false),
            )
            TapIconButton(contentDescription = "Back", onClick = onDone) {
                Icon(imageVector = Phosphor.ArrowLeft, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.onWin)
            }
        }
        Text(
            text = "You beat $opponentName",
            style = NopeekText.TitleSm,
            color = colors.onWin,
            modifier = Modifier.padding(top = NopeekSpace.S8),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S24), verticalAlignment = Alignment.Bottom) {
            Text(text = "$youMinutes min", style = NopeekText.DisplayClock, color = colors.onWin)
            Spacer(Modifier.size(NopeekSpace.S8))
            Text(text = "vs $opponentMinutes min", style = NopeekText.BodyMd, color = colors.onWin)
        }
        Pot(state = PotState.PAID_OUT, modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S24))
        Text(
            text = "$$pot",
            style = NopeekText.DisplayHero,
            color = colors.onWin,
            modifier = Modifier.padding(top = NopeekSpace.S24),
        )
        Text(
            text = "Your $stake back, plus $opponentName's $stake. The whole pot.",
            style = NopeekText.BodyMd,
            color = colors.onWinMuted,
            modifier = Modifier.padding(top = NopeekSpace.S8),
        )
        Column(modifier = Modifier.padding(top = NopeekSpace.S24), verticalArrangement = Arrangement.spacedBy(NopeekSpace.S8)) {
            // Loud full-bleed surface: the receipt has to take the win foreground tokens, or the
            // default Chalk/Mute lands at 1.01:1 and 2.06:1 on volt.
            ReceiptRow(label = "You used", value = "$youMinutes min", labelColor = colors.onWinMuted, valueColor = colors.onWin)
            ReceiptRow(label = "$opponentName used", value = "$opponentMinutes min", labelColor = colors.onWinMuted, valueColor = colors.onWin)
            ReceiptRow(label = "Transaction", value = tx, labelColor = colors.onWinMuted, valueColor = colors.onWin)
        }
        OutlineButton(
            label = "Rematch",
            onClick = onRematch,
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S24),
            ring = colors.onWin,
            content = colors.onWin,
        )
        PrimaryButton(
            label = "Share",
            onClick = onShare,
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S12, bottom = NopeekSpace.S24),
            container = colors.onWin,
            content = colors.win,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun WonScreenPreview() {
    NopeekTheme {
        WonScreen(
            stake = "5.00",
            pot = "10.00",
            youMinutes = "27",
            opponentMinutes = "34",
            opponentName = "alex.skr",
            tx = "5Hq2…tK9x",
            onShare = {},
            onRematch = {},
            onDone = {},
        )
    }
}

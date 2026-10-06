package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.components.Pot
import com.volo.app.ui.components.PotState
import com.volo.app.ui.components.PrimaryButton
import com.volo.app.ui.components.ReceiptRow
import com.volo.app.ui.components.TapIconButton
import com.volo.app.ui.components.animatedFraction
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.Volo
import com.volo.app.ui.theme.VoloMotion
import com.volo.app.ui.theme.VoloText
import com.volo.app.ui.theme.VoloTheme
import kotlin.math.abs

@Composable
fun WonScreen(
    amount: String,
    youMinutes: String,
    opponentMinutes: String,
    opponentName: String,
    tx: String,
    onShare: () -> Unit,
    onRematch: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = Volo.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.win)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "WON",
                style = VoloText.DisplayMoment,
                color = colors.onWin,
            )
            TapIconButton(contentDescription = "Close", onClick = onDone) {
                Icon(imageVector = Phosphor.ArrowLeft, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.onWin)
            }
        }
        Text(
            text = "You beat $opponentName",
            style = VoloText.TitleSm,
            color = colors.onWin,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.Bottom) {
            Text(text = "27 min", style = VoloText.DisplayClock, color = colors.onWin)
            Spacer(Modifier.size(8.dp))
            Text(text = "vs $youMinutes", style = VoloText.BodyMd, color = colors.onWin)
        }
        Pot(state = PotState.PAID_OUT, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
        Text(
            text = "$$amount",
            style = VoloText.DisplayHero,
            color = colors.onWin,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "Plus your $amount and $opponentName's $amount, returned to each of you.",
            style = VoloText.BodyMd,
            color = colors.onWinMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(modifier = Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReceiptRow(label = "You used", value = "$youMinutes min")
            ReceiptRow(label = "$opponentName used", value = "$opponentMinutes min")
            ReceiptRow(label = "Transaction", value = tx)
        }
        PrimaryButton(
            label = "Rematch",
            onClick = onRematch,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            container = colors.onWin,
            content = colors.win,
        )
        PrimaryButton(
            label = "Share",
            onClick = onShare,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun WonScreenPreview() {
    VoloTheme {
        WonScreen(
            amount = "5.00",
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

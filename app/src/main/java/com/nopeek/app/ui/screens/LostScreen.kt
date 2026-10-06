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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nopeek.app.ui.components.OutlineButton
import com.nopeek.app.ui.components.ReceiptRow
import com.nopeek.app.ui.components.TapIconButton
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme
import java.util.Locale

@Composable
fun LostScreen(
    youMinutes: String,
    opponentMinutes: String,
    opponentName: String,
    tx: String,
    cooldownSecondsLeft: Long,
    onBack: () -> Unit,
    onRematch: () -> Unit,
) {
    val colors = Nopeek.colors
    val unlocked = cooldownSecondsLeft <= 0L
    val minutes = (cooldownSecondsLeft / 60).coerceAtLeast(0)
    val seconds = (cooldownSecondsLeft % 60).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.danger)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "LOST",
                style = NopeekText.DisplayMoment,
                color = colors.surface,
            )
            TapIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(imageVector = Phosphor.ArrowLeft, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.surface)
            }
        }
        Text(
            text = "$opponentName stayed under",
            style = NopeekText.TitleSm,
            color = colors.surface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.Bottom) {
            Text(text = "${youMinutes}m", style = NopeekText.DisplayClock, color = colors.surface)
            Spacer(Modifier.size(8.dp))
            Text(text = "vs ${opponentMinutes}m", style = NopeekText.BodyMd, color = colors.surface)
        }
        Text(
            text = "Your stake is theirs now.",
            style = NopeekText.DisplayTitle,
            color = colors.surface,
            modifier = Modifier.padding(top = 24.dp),
        )
        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReceiptRow(label = "You used", value = "${youMinutes}m", labelColor = colors.surface, valueColor = colors.surface)
            ReceiptRow(label = "$opponentName used", value = "${opponentMinutes}m", labelColor = colors.surface, valueColor = colors.surface)
            ReceiptRow(label = "Paid to", value = opponentName, labelColor = colors.surface, valueColor = colors.surface)
            ReceiptRow(label = "Transaction", value = tx, labelColor = colors.surface, valueColor = colors.surface)
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = if (unlocked) "You can rematch now" else String.format(Locale.US, "Rematch in %d:%02d", minutes, seconds),
                style = NopeekText.TitleSm,
                color = colors.surface,
            )
        }
        OutlineButton(
            label = if (unlocked) "Rematch" else "Locked",
            onClick = onRematch,
            enabled = unlocked,
            ring = colors.surface,
            content = colors.surface,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFF6A4D)
@Composable
private fun LostScreenPreview() {
    NopeekTheme {
        LostScreen(
            youMinutes = "34",
            opponentMinutes = "27",
            opponentName = "alex.skr",
            tx = "5Hq2…tK9x",
            cooldownSecondsLeft = 540L,
            onBack = {},
            onRematch = {},
        )
    }
}

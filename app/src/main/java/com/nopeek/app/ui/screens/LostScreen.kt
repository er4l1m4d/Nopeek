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
import com.nopeek.app.ui.components.ReceiptRow
import com.nopeek.app.ui.components.TapIconButton
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekSpace
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
            .background(colors.lose)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            // Inside the scroll: the rematch button is the last element and must clear the system bar.
            .navigationBarsPadding()
            .padding(horizontal = NopeekSpace.S16),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S8),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LOST",
                style = NopeekText.DisplayMoment,
                color = colors.onLose,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f, fill = false),
            )
            TapIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(imageVector = Phosphor.ArrowLeft, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.onLose)
            }
        }
        Text(
            text = "$opponentName stayed under",
            style = NopeekText.TitleSm,
            color = colors.onLose,
            modifier = Modifier.padding(top = NopeekSpace.S8),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S24), verticalAlignment = Alignment.Bottom) {
            Text(text = "${youMinutes}m", style = NopeekText.DisplayClock, color = colors.onLose)
            Spacer(Modifier.size(NopeekSpace.S8))
            Text(text = "vs ${opponentMinutes}m", style = NopeekText.BodyMd, color = colors.onLose)
        }
        Text(
            text = "Your stake is theirs now.",
            style = NopeekText.DisplayTitle,
            color = colors.onLose,
            modifier = Modifier.padding(top = NopeekSpace.S24),
        )
        Column(modifier = Modifier.padding(top = NopeekSpace.S16), verticalArrangement = Arrangement.spacedBy(NopeekSpace.S8)) {
            ReceiptRow(label = "You used", value = "${youMinutes}m", labelColor = colors.onLose, valueColor = colors.onLose)
            ReceiptRow(label = "$opponentName used", value = "${opponentMinutes}m", labelColor = colors.onLose, valueColor = colors.onLose)
            ReceiptRow(label = "Paid to", value = opponentName, labelColor = colors.onLose, valueColor = colors.onLose)
            ReceiptRow(label = "Transaction", value = tx, labelColor = colors.onLose, valueColor = colors.onLose)
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S32), contentAlignment = Alignment.Center) {
            Text(
                text = if (unlocked) "You can rematch now" else String.format(Locale.US, "Rematch in %d:%02d", minutes, seconds),
                style = NopeekText.TitleSm,
                color = colors.onLose,
            )
        }
        OutlineButton(
            label = if (unlocked) "Rematch" else "Locked",
            onClick = onRematch,
            enabled = unlocked,
            ring = colors.onLose,
            content = colors.onLose,
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S16, bottom = NopeekSpace.S24),
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

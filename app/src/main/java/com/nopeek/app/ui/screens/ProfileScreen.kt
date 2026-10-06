package com.nopeek.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nopeek.app.data.solana.openDuelsGate
import com.nopeek.app.data.solana.openDuelsLabel
import com.nopeek.app.ui.components.OutlineButton
import com.nopeek.app.ui.components.ReceiptRow
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekShape
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme

@Composable
fun ProfileScreen(onOpenDuels: () -> Unit = {}) {
    val colors = Nopeek.colors
    val skrRaw = 100_000_000L
    val skrDecimals = 6
    val open = openDuelsGate(skrRaw, skrDecimals)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(text = "Profile", style = NopeekText.DisplayTitle, color = colors.onSurface, modifier = Modifier.padding(top = 16.dp))
        Row(modifier = Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = colors.surfaceRaised,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(imageVector = Phosphor.User, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(32.dp).padding(8.dp))
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = "7xKp…9fQ2", style = NopeekText.TitleMd, color = colors.onSurface)
                Text(text = "Connected · devnet", style = NopeekText.BodyMd, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            shape = NopeekShape.Md,
            color = colors.surfaceRaised,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Your balances", style = NopeekText.TitleMd, color = colors.onSurface)
                ReceiptRow(label = "USDC", value = "${skrRaw / 1_000_000}.00")
                ReceiptRow(label = "SKR", value = "0.00")
                ReceiptRow(label = "Open Duels", value = openDuelsLabel(open))
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = NopeekShape.Md,
            color = colors.surfaceRaised,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Usage access", style = NopeekText.TitleMd, color = colors.onSurface)
                Text(
                    text = "Review what Nopeek measures.",
                    style = NopeekText.BodyMd,
                    color = colors.onSurfaceMuted,
                )
                OutlineButton(label = "Open settings", onClick = {}, modifier = Modifier.fillMaxWidth())
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
            shape = NopeekShape.Md,
            color = colors.surfaceRaised,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Privacy", style = NopeekText.TitleMd, color = colors.onSurface)
                Text(
                    text = "Nopeek only sees minutes per app. It never reads messages, photos or what you do inside apps.",
                    style = NopeekText.BodyMd,
                    color = colors.onSurfaceMuted,
                )
            }
        }
    }
}

private fun shorten(s: String, head: Int = 4, tail: Int = 4): String =
    if (s.length <= head + tail + 1) s else "${s.take(head)}…${s.takeLast(tail)}"
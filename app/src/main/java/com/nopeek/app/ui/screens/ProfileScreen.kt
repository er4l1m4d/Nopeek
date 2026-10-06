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
import com.nopeek.app.ui.theme.NopeekSpace
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
            .padding(horizontal = NopeekSpace.S16),
    ) {
        Text(text = "Profile", style = NopeekText.DisplayTitle, color = colors.onSurface, modifier = Modifier.padding(top = NopeekSpace.S16))
        Row(modifier = Modifier.padding(top = NopeekSpace.S24), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = NopeekShape.Full,
                color = colors.surfaceRaised,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(imageVector = Phosphor.User, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(32.dp).padding(NopeekSpace.S8))
            }
            Column(modifier = Modifier.padding(start = NopeekSpace.S16)) {
                Text(text = "7xKp…9fQ2", style = NopeekText.TitleMd, color = colors.onSurface)
                Text(text = "Connected · devnet", style = NopeekText.BodyMd, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = NopeekSpace.S4))
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S24),
            shape = NopeekShape.Md,
            color = colors.surfaceRaised,
        ) {
            Column(modifier = Modifier.padding(NopeekSpace.S16), verticalArrangement = Arrangement.spacedBy(NopeekSpace.S12)) {
                Text(text = "Your balances", style = NopeekText.TitleMd, color = colors.onSurface)
                ReceiptRow(label = "USDC", value = "${skrRaw / 1_000_000}.00")
                ReceiptRow(label = "SKR", value = "0.00")
                ReceiptRow(label = "Open Duels", value = openDuelsLabel(open))
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S16),
            shape = NopeekShape.Md,
            color = colors.surfaceRaised,
        ) {
            Column(modifier = Modifier.padding(NopeekSpace.S16), verticalArrangement = Arrangement.spacedBy(NopeekSpace.S12)) {
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
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S16, bottom = NopeekSpace.S24),
            shape = NopeekShape.Md,
            color = colors.surfaceRaised,
        ) {
            Column(modifier = Modifier.padding(NopeekSpace.S16), verticalArrangement = Arrangement.spacedBy(NopeekSpace.S12)) {
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
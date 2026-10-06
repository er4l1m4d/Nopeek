package com.nopeek.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nopeek.app.ui.components.ForfeitDialog
import com.nopeek.app.ui.components.Pot
import com.nopeek.app.ui.components.PotState
import com.nopeek.app.ui.components.PrimaryButton
import com.nopeek.app.ui.components.Scoreboard
import com.nopeek.app.ui.components.SharedTape
import com.nopeek.app.ui.components.TapIconButton
import com.nopeek.app.ui.components.UsageState
import com.nopeek.app.ui.components.usageVariant
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekSpace
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme

@Composable
fun DuelScreen(pubkey: String, onBack: () -> Unit, onForfeit: (() -> Unit)? = null) {
    val colors = Nopeek.colors
    var menuOpen by remember { mutableStateOf(false) }
    var confirmForfeit by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NopeekSpace.S16),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S8),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TapIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(imageVector = Phosphor.ArrowLeft, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.onSurface)
            }
            Box {
                TapIconButton(contentDescription = "More options", onClick = { menuOpen = true }) {
                    Icon(imageVector = Phosphor.LockKey, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.onSurface)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Forfeit", style = NopeekText.TitleSm, color = colors.danger) },
                        onClick = { menuOpen = false; confirmForfeit = true },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S16),
            horizontalArrangement = Arrangement.spacedBy(NopeekSpace.S16),
            verticalAlignment = Alignment.Bottom,
        ) {
            Pot(state = PotState.FUNDED)
            Column {
                Text(text = "$10 pot", style = NopeekText.DisplayFigure, color = colors.onSurface)
                Text(
                    text = "Your $5 and alex.skr's $5, held until midnight UTC.",
                    style = NopeekText.BodyMd,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = NopeekSpace.S4),
                )
            }
        }
        Scoreboard(
            youClock = "18:42",
            youStatus = usageVariant(UsageState.PASSING),
            youLeading = Phosphor.CheckCircle,
            opponentName = "alex.skr",
            opponentClock = "27:10",
            opponentStatus = usageVariant(UsageState.AT_RISK),
            opponentLeading = Phosphor.WarningCircle,
            talkBack = "You, 18 minutes 42 seconds, safe. alex.skr, 27 minutes 10 seconds, at risk. You are ahead by 8 minutes 28 seconds.",
            modifier = Modifier.padding(top = NopeekSpace.S20),
        )
        SharedTape(
            youFraction = 0.622f,
            opponentFraction = 0.906f,
            limitLabel = "30 min limit",
            leadText = "8:28 ahead",
            behind = false,
            modifier = Modifier.padding(top = NopeekSpace.S20),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S24),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(text = "11:18", style = NopeekText.DisplayClock, color = colors.onSurface)
            Column(modifier = Modifier.padding(end = NopeekSpace.S16)) {
                Text(text = "left today", style = NopeekText.BodyMd, color = colors.onSurfaceMuted)
            }
        }
        PrimaryButton(
            label = "Lock TikTok",
            onClick = {},
            modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S16, bottom = NopeekSpace.S24),
            hero = true,
        )
    }
    if (confirmForfeit) {
        ForfeitDialog(
            stakeLabel = "$5",
            opponentName = "alex.skr",
            onConfirm = { confirmForfeit = false; onForfeit?.invoke() },
            onDismiss = { confirmForfeit = false },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun DuelScreenPreview() {
    NopeekTheme { DuelScreen(pubkey = "Hx8vQ9demoChallenge42", onBack = {}, onForfeit = {}) }
}
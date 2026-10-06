package com.volo.app.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.components.ChecklistRow
import com.volo.app.ui.components.Pot
import com.volo.app.ui.components.PotState
import com.volo.app.ui.components.PrimaryButton
import com.volo.app.ui.components.Scoreboard
import com.volo.app.ui.components.StepperBar
import com.volo.app.ui.components.TextButton
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.Volo
import com.volo.app.ui.theme.VoloText
import com.volo.app.ui.theme.VoloTheme
import androidx.compose.foundation.clickable

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onGetTestUsdc: () -> Unit = {},
) {
    val colors = Volo.colors
    var step by remember { mutableStateOf(1) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        StepperBar(step = step, total = 5, modifier = Modifier.padding(top = 16.dp))
        when (step) {
            1 -> {
                Scoreboard(
                    youClock = "18:42",
                    youStatus = com.volo.app.ui.components.usageVariant(com.volo.app.ui.components.UsageState.PASSING),
                    youLeading = Phosphor.CheckCircle,
                    opponentName = "Friend",
                    opponentClock = "27:10",
                    opponentStatus = com.volo.app.ui.components.usageVariant(com.volo.app.ui.components.UsageState.AT_RISK),
                    opponentLeading = Phosphor.WarningCircle,
                    talkBack = "You, 18 minutes 42 seconds, safe. Friend, 27 minutes 10 seconds, at risk.",
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Stay under your limit. Win the pot.",
                    style = VoloText.DisplayTitle,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = 32.dp),
                )
                Text(
                    text = "Set a daily limit on an app, put a little money against a friend, and whoever stays under wins.",
                    style = VoloText.BodyLg,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                PrimaryButton(
                    label = "Get started",
                    onClick = { step = 2 },
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 24.dp),
                    hero = true,
                )
            }
            2 -> {
                OnboardingHead(title = "How a duel works", sub = null)
                NumberedStep(number = "1", title = "Pick an app and a limit", sub = "For example, 30 minutes a day on TikTok.")
                NumberedStep(number = "2", title = "Match stakes with a friend", sub = "You each lock the same amount into the pot.")
                NumberedStep(number = "3", title = "Stay under to take the pot", sub = "If they go over and you don't, it's yours.")
                Text(
                    text = "Test funds only. Nothing real is at risk yet.",
                    style = VoloText.BodyMd,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = 16.dp),
                )
                PrimaryButton(
                    label = "Continue",
                    onClick = { step = 3 },
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
            }
            3 -> {
                OnboardingHead(
                    title = "Let Nopeek count your minutes.",
                    sub = "Android keeps a record of how long each app is open. Nopeek reads it to check your limit.",
                )
                NumberedStep(number = "1", title = "Tap Open settings", sub = "")
                NumberedStep(number = "2", title = "Find Nopeek in the list", sub = "")
                NumberedStep(number = "3", title = "Turn on Allow usage access", sub = "")
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Phosphor.CheckCircle,
                        contentDescription = null,
                        tint = colors.you,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Nopeek only sees minutes per app. It never reads messages, photos or what you do inside apps.",
                        style = VoloText.BodyMd,
                        color = colors.onSurfaceMuted,
                    )
                }
                PrimaryButton(
                    label = "Open settings",
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                TextButton(
                    label = "Not now",
                    onClick = { step = 4 },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            4 -> {
                OnboardingHead(
                    title = "Connect your wallet.",
                    sub = "Your wallet holds your stake. You approve every move, and Nopeek can't spend without you.",
                )
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChecklistRow(title = "Wallet connected", subtitle = "Phantom, 7xKp…9fQ2", done = true)
                    ChecklistRow(title = "Get test USDC", subtitle = "Free devnet funds from Circle's faucet", done = false)
                }
                PrimaryButton(
                    label = "Get test USDC",
                    onClick = onGetTestUsdc,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                TextButton(
                    label = "I'll do this later",
                    onClick = { step = 5 },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            else -> {
                OnboardingHead(title = "You're in.", sub = "You have no duels yet. Start your first one.")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Pot(state = PotState.WAITING)
                }
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChecklistRow(title = "Usage access is on", subtitle = "Nopeek can check your limit", done = true)
                    ChecklistRow(title = "Wallet connected", subtitle = "7xKp…9fQ2", done = true)
                    ChecklistRow(title = "100 test USDC", subtitle = "Ready to stake", done = true)
                }
                PrimaryButton(
                    label = "Create your first duel",
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun OnboardingHead(title: String, sub: String?) {
    val colors = Volo.colors
    Text(
        text = title,
        style = VoloText.DisplayTitle,
        color = colors.onSurface,
        modifier = Modifier.padding(top = 8.dp),
    )
    if (sub != null) {
        Text(
            text = sub,
            style = VoloText.BodyLg,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun NumberedStep(number: String, title: String, sub: String) {
    val colors = Volo.colors
    Row(modifier = Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number, style = VoloText.LabelLg, color = colors.primary)
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(text = title, style = VoloText.TitleSm, color = colors.onSurface)
            if (sub.isNotEmpty()) {
                Text(text = sub, style = VoloText.BodyMd, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun OnboardingScreenPreview() {
    VoloTheme {
        OnboardingScreen(onDone = {})
    }
}
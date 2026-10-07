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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nopeek.app.ui.components.ChecklistRow
import com.nopeek.app.ui.components.Pot
import com.nopeek.app.ui.components.PotState
import com.nopeek.app.ui.components.PrimaryButton
import com.nopeek.app.ui.components.Scoreboard
import com.nopeek.app.ui.components.StepperBar
import com.nopeek.app.ui.components.TextButton
import com.nopeek.app.ui.components.UsageState
import com.nopeek.app.ui.components.usageVariant
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekShape
import com.nopeek.app.ui.theme.NopeekSpace
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onGetTestUsdc: () -> Unit = {},
) {
    val colors = Nopeek.colors
    var step by rememberSaveable { mutableStateOf(1) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            // Inside the scroll so the last element clears the system bar instead of sliding under it.
            .navigationBarsPadding()
            .padding(horizontal = NopeekSpace.S16),
    ) {
        StepperBar(step = step, total = 5, modifier = Modifier.padding(top = NopeekSpace.S16))
        when (step) {
            1 -> {
                Scoreboard(
                    youClock = "18:42",
                    youStatus = usageVariant(UsageState.PASSING),
                    youLeading = Phosphor.CheckCircle,
                    opponentName = "Friend",
                    opponentClock = "27:10",
                    opponentStatus = usageVariant(UsageState.AT_RISK),
                    opponentLeading = Phosphor.WarningCircle,
                    talkBack = "You, 18 minutes 42 seconds, safe. Friend, 27 minutes 10 seconds, at risk.",
                    modifier = Modifier.padding(top = NopeekSpace.S8),
                )
                Text(
                    text = "Stay under your limit. Win the pot.",
                    style = NopeekText.DisplayTitle,
                    color = colors.onSurface,
                    modifier = Modifier.padding(top = NopeekSpace.S32),
                )
                Text(
                    text = "Set a daily limit on an app, put a little money against a friend, and whoever stays under wins.",
                    style = NopeekText.BodyLg,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = NopeekSpace.S8),
                )
                PrimaryButton(
                    label = "Get started",
                    onClick = { step = 2 },
                    modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S32, bottom = NopeekSpace.S24),
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
                    style = NopeekText.BodyMd,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = NopeekSpace.S16),
                )
                PrimaryButton(
                    label = "Continue",
                    onClick = { step = 3 },
                    modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S24),
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
                    modifier = Modifier.padding(top = NopeekSpace.S8),
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
                        style = NopeekText.BodyMd,
                        color = colors.onSurfaceMuted,
                    )
                }
                PrimaryButton(
                    label = "Open settings",
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S16),
                )
                TextButton(
                    label = "Not now",
                    onClick = { step = 4 },
                    modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S8),
                )
            }
            4 -> {
                OnboardingHead(
                    title = "Connect your wallet.",
                    sub = "Your wallet holds your stake. You approve every move, and Nopeek can't spend without you.",
                )
                Column(modifier = Modifier.padding(top = NopeekSpace.S16), verticalArrangement = Arrangement.spacedBy(NopeekSpace.S8)) {
                    ChecklistRow(title = "Wallet connected", subtitle = "Phantom, 7xKp…9fQ2", done = true)
                    ChecklistRow(title = "Get test USDC", subtitle = "Free devnet funds from Circle's faucet", done = false)
                }
                PrimaryButton(
                    label = "Get test USDC",
                    onClick = onGetTestUsdc,
                    modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S16),
                )
                TextButton(
                    label = "I'll do this later",
                    onClick = { step = 5 },
                    modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S8),
                )
            }
            else -> {
                OnboardingHead(title = "You're in.", sub = "You have no duels yet. Start your first one.")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = NopeekSpace.S20),
                    contentAlignment = Alignment.Center,
                ) {
                    Pot(state = PotState.WAITING)
                }
                Column(modifier = Modifier.padding(top = NopeekSpace.S16), verticalArrangement = Arrangement.spacedBy(NopeekSpace.S8)) {
                    ChecklistRow(title = "Usage access is on", subtitle = "Nopeek can check your limit", done = true)
                    ChecklistRow(title = "Wallet connected", subtitle = "7xKp…9fQ2", done = true)
                    ChecklistRow(title = "100 test USDC", subtitle = "Ready to stake", done = true)
                }
                PrimaryButton(
                    label = "Create your first duel",
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S24, bottom = NopeekSpace.S24),
                )
            }
        }
    }
}

@Composable
private fun OnboardingHead(title: String, sub: String?) {
    val colors = Nopeek.colors
    Text(
        text = title,
        style = NopeekText.DisplayTitle,
        color = colors.onSurface,
        modifier = Modifier.padding(top = NopeekSpace.S8),
    )
    if (sub != null) {
        Text(
            text = sub,
            style = NopeekText.BodyLg,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = NopeekSpace.S8),
        )
    }
}

@Composable
private fun NumberedStep(number: String, title: String, sub: String) {
    val colors = Nopeek.colors
    Row(modifier = Modifier.padding(top = NopeekSpace.S16), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(NopeekShape.Full)
                .background(colors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number, style = NopeekText.LabelLg, color = colors.primary)
        }
        Spacer(Modifier.size(NopeekSpace.S12))
        Column {
            Text(text = title, style = NopeekText.TitleSm, color = colors.onSurface)
            if (sub.isNotEmpty()) {
                Text(text = sub, style = NopeekText.BodyMd, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun OnboardingScreenPreview() {
    NopeekTheme {
        OnboardingScreen(onDone = {})
    }
}
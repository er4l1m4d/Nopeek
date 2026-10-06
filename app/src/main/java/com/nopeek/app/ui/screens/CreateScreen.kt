package com.nopeek.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nopeek.app.data.Challenge
import com.nopeek.app.data.challenge.ChallengeDraft
import com.nopeek.app.data.challenge.message
import com.nopeek.app.data.challenge.parseOpponent
import com.nopeek.app.data.challenge.validateChallengeInput
import com.nopeek.app.data.local.AppDatabase
import com.nopeek.app.data.local.toEntity
import com.nopeek.app.data.wallet.WalletState
import com.nopeek.app.data.wallet.walletManager
import com.nopeek.app.ui.components.InlineError
import com.nopeek.app.ui.components.InstalledAppList
import com.nopeek.app.ui.components.PasteField
import com.nopeek.app.ui.components.Pot
import com.nopeek.app.ui.components.PotState
import com.nopeek.app.ui.components.PresetChip
import com.nopeek.app.ui.components.PrimaryButton
import com.nopeek.app.ui.components.ReceiptRow
import com.nopeek.app.ui.components.ReviewRow
import com.nopeek.app.ui.components.StepperBar
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekSpace
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

private data class WindowOption(val label: String, val seconds: Long)

private val windowOptions = listOf(
    WindowOption("1 hour", 3_600L),
    WindowOption("4 hours", 14_400L),
    WindowOption("12 hours", 43_200L),
    WindowOption("24 hours", 86_400L),
    WindowOption("7 days", 604_800L),
)

private val limitPresets = listOf(15, 30, 60, 120)

private val stakePresets = listOf(1_000_000L, 5_000_000L, 10_000_000L, 25_000_000L)

@Composable
fun CreateScreen(onCreated: () -> Unit) {
    var step by remember { mutableStateOf(1) }
    var targetPackage by remember { mutableStateOf<String?>(null) }
    var limitMinutes by remember { mutableStateOf<Int?>(null) }
    var stakeAmountRaw by remember { mutableStateOf(5_000_000L) }
    var opponent by remember { mutableStateOf("") }
    var windowSeconds by remember { mutableStateOf<Long?>(null) }
    var sentTx by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = Nopeek.colors
    val draft = ChallengeDraft(targetPackage, limitMinutes, stakeAmountRaw, opponent, windowSeconds)
    val errors = validateChallengeInput(draft)
    val resolvedOpponent = parseOpponent(opponent)
    val appLabel = remember(targetPackage) {
        com.nopeek.app.ui.components.loadInstalledApps(context)
            .firstOrNull { it.packageName == targetPackage }?.label ?: targetPackage.orEmpty()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = NopeekSpace.S16, end = NopeekSpace.S16, bottom = 104.dp),
        ) {
            if (sentTx == null) {
                StepperBar(step = step, total = 6, modifier = Modifier.padding(top = NopeekSpace.S16))
            }
            when (step) {
                1 -> {
                    StepHead("Which app?", "Pick the one you want to cut back on.")
                    val apps = remember { com.nopeek.app.ui.components.loadInstalledApps(context) }
                    InstalledAppList(
                        apps = apps,
                        selectedPackage = targetPackage,
                        onSelected = { targetPackage = it },
                    )
                }
                2 -> {
                    StepHead("How long a day?", "Stay under this and you win.")
                    Text(
                        text = "${limitMinutes ?: 30}",
                        style = NopeekText.DisplayHero,
                        color = colors.onSurface,
                        modifier = Modifier.padding(top = NopeekSpace.S24),
                    )
                    Text(text = "min a day", style = NopeekText.BodyLg, color = colors.onSurfaceMuted)
                    ChipGrid(
                        options = limitPresets.map { it.toString() },
                        selected = limitMinutes?.toString(),
                        onSelect = { limitMinutes = it.toInt() },
                        modifier = Modifier.padding(top = NopeekSpace.S16),
                    )
                }
                3 -> {
                    StepHead("How long does it run?", "The clock starts when your opponent accepts.")
                    val selected = windowOptions.firstOrNull { it.seconds == windowSeconds }
                    Text(
                        text = selected?.label?.split(" ")?.first() ?: "24",
                        style = NopeekText.DisplayHero,
                        color = colors.onSurface,
                        modifier = Modifier.padding(top = NopeekSpace.S24),
                    )
                    Text(
                        text = selected?.label?.split(" ")?.getOrNull(1) ?: "hours",
                        style = NopeekText.BodyLg,
                        color = colors.onSurfaceMuted,
                    )
                    ChipGrid(
                        options = windowOptions.map { it.label },
                        selected = selected?.label,
                        onSelect = { label -> windowSeconds = windowOptions.firstOrNull { it.label == label }?.seconds },
                        modifier = Modifier.padding(top = NopeekSpace.S16),
                    )
                    Text(
                        text = "You need to stay under ${limitMinutes ?: 30} min in that window.",
                        style = NopeekText.BodyMd,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(top = NopeekSpace.S16),
                    )
                }
                4 -> {
                    StepHead("What’s on the line?", "Your opponent matches it.")
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = NopeekSpace.S24),
                        horizontalArrangement = Arrangement.spacedBy(NopeekSpace.S20),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = "$" + formatUsdc(stakeAmountRaw).split(".")[0],
                            style = NopeekText.DisplayHero,
                            color = colors.onSurface,
                        )
                        Pot(state = PotState.FUNDED)
                    }
                    ChipGrid(
                        options = stakePresets.map { "$" + formatUsdc(it).split(".")[0] },
                        selected = "$" + formatUsdc(stakeAmountRaw).split(".")[0],
                        onSelect = { label ->
                            stakePresets.firstOrNull { "$" + formatUsdc(it).split(".")[0] == label }?.let {
                                stakeAmountRaw = it
                            }
                        },
                        modifier = Modifier.padding(top = NopeekSpace.S16),
                    )
                    Text(
                        text = "You have 42.50 USDC. These are test funds on devnet.",
                        style = NopeekText.BodyMd,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(top = NopeekSpace.S16),
                    )
                }
                5 -> {
                    StepHead("Who are you facing?", null)
                    PasteField(
                        prompt = "Paste an address or .skr name",
                        actionLabel = "Paste",
                        onAction = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let { opponent = it }
                        },
                        modifier = Modifier.padding(top = NopeekSpace.S16),
                    )
                    if (resolvedOpponent != null) {
                        Text(
                            text = "Resolves to $resolvedOpponent",
                            style = NopeekText.BodyMd,
                            color = colors.you,
                            modifier = Modifier.padding(top = NopeekSpace.S12),
                        )
                    }
                    errors.forEach { error ->
                        InlineError(
                            message = error.message(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = NopeekSpace.S12),
                        )
                    }
                }
                6 -> {
                    StepHead("Review and approve.", null)
                    Column(modifier = Modifier.padding(top = NopeekSpace.S20)) {
                        ReviewRow(label = "App", value = appLabel.ifBlank { "—" }, onEdit = { step = 1 })
                        ReviewRow(label = "Daily limit", value = "${limitMinutes ?: 0} min", onEdit = { step = 2 })
                        ReviewRow(
                            label = "Duration",
                            value = windowOptions.firstOrNull { it.seconds == windowSeconds }?.label.orEmpty(),
                            onEdit = { step = 3 },
                        )
                        ReviewRow(label = "Opponent", value = resolvedOpponent ?: opponent, onEdit = { step = 5 })
                        ReviewRow(label = "Your stake", value = "$" + formatUsdc(stakeAmountRaw), onEdit = { step = 4 })
                    }
                    Text(
                        text = "Your $" + formatUsdc(stakeAmountRaw) + " locks when you approve in your wallet. Test funds on devnet — nothing real moves.",
                        style = NopeekText.BodyMd,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(top = NopeekSpace.S16),
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = NopeekSpace.S16),
                        contentAlignment = Alignment.Center,
                    ) {
                        Pot(state = PotState.WAITING, modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        text = "Sent.",
                        style = NopeekText.DisplayHero,
                        color = colors.onSurface,
                        modifier = Modifier.padding(top = NopeekSpace.S20),
                    )
                    Text(
                        text = "We’ll tell you when ${resolvedOpponent ?: opponent} accepts. Until then your $" + formatUsdc(stakeAmountRaw) + " stays locked.",
                        style = NopeekText.BodyLg,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(top = NopeekSpace.S8),
                    )
                    ReceiptRow(
                        label = "Transaction",
                        value = sentTx ?: "",
                        modifier = Modifier.padding(top = NopeekSpace.S20),
                    )
                    PrimaryButton(
                        label = "Back to duels",
                        onClick = onCreated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = NopeekSpace.S16, bottom = NopeekSpace.S24),
                        hero = true,
                    )
                }
            }
        }
        if (step <= 6) {
            val walletOk = walletManager.state is WalletState.Connected
                val (ctaLabel, ctaEnabled, ctaAction) = when (step) {
                1 -> Triple("Continue", targetPackage != null, { step = 2 })
                2 -> Triple("Continue", limitMinutes != null, { step = 3 })
                3 -> Triple("Continue", windowSeconds != null, { step = 4 })
                4 -> Triple("Continue", true, { step = 5 })
                5 -> Triple("Continue", resolvedOpponent != null && errors.isEmpty(), { step = 6 })
                else -> Triple(
                    if (walletOk) "Approve in wallet" else "Connect wallet to approve",
                    walletOk && errors.isEmpty(),
                    { approve(resolvedOpponent, scope, targetPackage, limitMinutes, stakeAmountRaw, windowSeconds, context, onCreated) { sentTx = it; step = 7 } },
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = NopeekSpace.S16, top = NopeekSpace.S12, end = NopeekSpace.S16, bottom = NopeekSpace.S24),
            ) {
                PrimaryButton(label = ctaLabel, onClick = ctaAction, enabled = ctaEnabled, hero = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun approve(
    resolvedOpponent: String?,
    scope: CoroutineScope,
    targetPackage: String?,
    limitMinutes: Int?,
    stakeAmountRaw: Long,
    windowSeconds: Long?,
    context: Context,
    onCreated: () -> Unit,
    onSent: (String) -> Unit,
) {
    val now = System.currentTimeMillis()
    val challenge = Challenge(
        id = UUID.randomUUID().toString(),
        creatorWallet = walletManager.walletAddress,
        opponentWallet = requireNotNull(resolvedOpponent),
        targetApp = requireNotNull(targetPackage),
        limitSeconds = requireNotNull(limitMinutes) * 60,
        stakeAmountRaw = stakeAmountRaw,
        windowSeconds = requireNotNull(windowSeconds),
        startTime = now,
        endTime = now + requireNotNull(windowSeconds) * 1000L,
    )
    scope.launch {
        AppDatabase.getInstance(context).challengeDao().insert(challenge.toEntity())
        onSent("5Hq2…tK9x")
        onCreated()
    }
}

@Composable
private fun StepHead(title: String, sub: String?) {
    val colors = Nopeek.colors
    Text(text = title, style = NopeekText.DisplayTitle, color = colors.onSurface)
    if (sub != null) {
        Text(
            text = sub,
            style = NopeekText.BodyLg,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = NopeekSpace.S8),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGrid(options: List<String>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NopeekSpace.S8),
        verticalArrangement = Arrangement.spacedBy(NopeekSpace.S8),
    ) {
        options.forEach { option ->
            PresetChip(label = option, selected = option == selected, onClick = { onSelect(option) })
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun CreateScreenPreview() {
    NopeekTheme {
        CreateScreen(onCreated = {})
    }
}

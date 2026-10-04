package com.volo.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.data.Challenge
import com.volo.app.data.challenge.ChallengeDraft
import com.volo.app.data.challenge.message
import com.volo.app.data.challenge.parseOpponent
import com.volo.app.data.challenge.validateChallengeInput
import com.volo.app.data.local.AppDatabase
import com.volo.app.data.local.toEntity
import com.volo.app.data.wallet.WalletState
import com.volo.app.data.wallet.walletManager
import com.volo.app.ui.components.AppSelector
import com.volo.app.ui.components.InlineError
import com.volo.app.ui.components.LimitSelector
import com.volo.app.ui.components.MetricRow
import com.volo.app.ui.components.PrimaryButton
import com.volo.app.ui.components.SecondaryButton
import com.volo.app.ui.components.SectionHeader
import com.volo.app.ui.components.SelectChip
import com.volo.app.ui.components.StakeInput
import com.volo.app.ui.components.formatUsdc
import com.volo.app.ui.components.loadInstalledApps
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(onCreated: () -> Unit) {
    var targetPackage by remember { mutableStateOf<String?>(null) }
    var limitMinutes by remember { mutableStateOf<Int?>(null) }
    var stakeAmountRaw by remember { mutableStateOf(5_000_000L) }
    var opponent by remember { mutableStateOf("") }
    var windowSeconds by remember { mutableStateOf<Long?>(null) }
    var showReview by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val walletConnected = walletManager.state is WalletState.Connected
    val draft = ChallengeDraft(targetPackage, limitMinutes, stakeAmountRaw, opponent, windowSeconds)
    val errors = validateChallengeInput(draft)
    val resolvedOpponent = parseOpponent(opponent)
    val appLabel = remember(targetPackage) {
        loadInstalledApps(context)
            .firstOrNull { it.packageName == targetPackage }?.label ?: targetPackage.orEmpty()
    }
    val windowLabel = windowOptions.firstOrNull { it.seconds == windowSeconds }?.label.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoloColors.bg)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = VoloDimens.spaceXl,
                vertical = VoloDimens.spaceXxl,
            ),
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs)) {
            Text(
                text = "Create a duel",
                style = VoloType.headline,
                color = VoloColors.text,
            )
            Text(
                text = "Make the promise specific. Make it matter.",
                style = VoloType.body,
                color = VoloColors.textMuted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd)) {
            SectionHeader(
                eyebrow = "STEP 1",
                title = "Target app",
                modifier = Modifier.fillMaxWidth(),
            )
            AppSelector(selectedPackage = targetPackage, onSelected = { targetPackage = it })
        }

        Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd)) {
            SectionHeader(
                eyebrow = "STEP 2",
                title = "Daily limit",
                modifier = Modifier.fillMaxWidth(),
            )
            LimitSelector(selectedMinutes = limitMinutes, onSelected = { limitMinutes = it })
        }

        Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd)) {
            SectionHeader(
                eyebrow = "STEP 3",
                title = "Window",
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
                verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
            ) {
                windowOptions.forEach { option ->
                    SelectChip(
                        label = option.label,
                        selected = windowSeconds == option.seconds,
                        onClick = { windowSeconds = option.seconds },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd)) {
            SectionHeader(
                eyebrow = "STEP 4",
                title = "Stake",
                modifier = Modifier.fillMaxWidth(),
            )
            StakeInput(
                stakeAmountRaw = stakeAmountRaw,
                onStakeChange = { stakeAmountRaw = it },
                walletConnected = walletConnected,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd)) {
            SectionHeader(
                eyebrow = "STEP 5",
                title = "Opponent",
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = opponent,
                onValueChange = { opponent = it },
                label = { Text("Wallet address or name.skr") },
                textStyle = VoloType.mono,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = VoloColors.text,
                    unfocusedTextColor = VoloColors.text,
                    focusedBorderColor = VoloColors.copper,
                    unfocusedBorderColor = VoloColors.line,
                    cursorColor = VoloColors.copper,
                    focusedLabelColor = VoloColors.textMuted,
                    unfocusedLabelColor = VoloColors.textMuted,
                ),
            )
            val resolved = resolvedOpponent
            if (resolved != null && resolved != opponent.trim()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
                ) {
                    Icon(
                        imageVector = Phosphor.CheckCircle,
                        contentDescription = null,
                        tint = VoloColors.copper,
                        modifier = Modifier.size(VoloDimens.spaceLg),
                    )
                    Text(
                        text = "Resolves to $resolved",
                        style = VoloType.monoSmall,
                        color = VoloColors.textMuted,
                    )
                }
            } else if (resolved != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
                ) {
                    Icon(
                        imageVector = Phosphor.CheckCircle,
                        contentDescription = null,
                        tint = VoloColors.safe,
                        modifier = Modifier.size(VoloDimens.spaceLg),
                    )
                    Text(
                        text = "Valid wallet address",
                        style = VoloType.monoSmall,
                        color = VoloColors.textMuted,
                    )
                }
            }
        }

        errors.forEach { error ->
            InlineError(
                message = error.message(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd)) {
            SectionHeader(
                eyebrow = "REVIEW",
                title = "Your duel",
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(VoloDimens.radiusCard),
                color = VoloColors.surface,
                contentColor = VoloColors.text,
                border = BorderStroke(1.dp, VoloColors.line),
            ) {
                Column(
                    modifier = Modifier.padding(VoloDimens.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
                ) {
                    MetricRow(
                        title = "App",
                        value = appLabel.ifBlank { "—" },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricRow(
                        title = "Daily limit",
                        value = limitMinutes?.let { "$it MIN" } ?: "—",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricRow(
                        title = "Window",
                        value = windowLabel.ifBlank { "—" },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricRow(
                        title = "Stake",
                        value = "${formatUsdc(stakeAmountRaw)} USDC",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricRow(
                        title = "Opponent",
                        value = (resolvedOpponent ?: opponent).ifBlank { "—" },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        PrimaryButton(
            label = "Review duel",
            onClick = { if (errors.isEmpty()) showReview = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showReview) {
        AlertDialog(
            onDismissRequest = { showReview = false },
            containerColor = VoloColors.surfaceRaised,
            shape = RoundedCornerShape(VoloDimens.radiusCard),
            title = {
                Text("Confirm duel", style = VoloType.title, color = VoloColors.text)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm)) {
                    MetricRow(
                        title = "App",
                        value = appLabel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricRow(
                        title = "Daily limit",
                        value = "${limitMinutes ?: 0} MIN",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricRow(
                        title = "Window",
                        value = windowLabel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricRow(
                        title = "Stake",
                        value = "${formatUsdc(stakeAmountRaw)} USDC",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MetricRow(
                        title = "Opponent",
                        value = resolvedOpponent ?: opponent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    label = "Confirm",
                    onClick = {
                        showReview = false
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
                            onCreated()
                        }
                    },
                )
            },
            dismissButton = {
                SecondaryButton(label = "Cancel", onClick = { showReview = false })
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun CreateScreenPreview() {
    VoloTheme {
        CreateScreen(onCreated = {})
    }
}

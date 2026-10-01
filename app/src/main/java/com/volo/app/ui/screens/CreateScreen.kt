package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volo.app.data.Challenge
import com.volo.app.data.challenge.ChallengeDraft
import com.volo.app.data.challenge.parseOpponent
import com.volo.app.data.challenge.validateChallengeInput
import com.volo.app.data.challenge.message
import com.volo.app.data.local.AppDatabase
import com.volo.app.data.local.toEntity
import com.volo.app.data.wallet.WalletState
import com.volo.app.data.wallet.walletManager
import kotlinx.coroutines.launch
import java.util.UUID
import com.volo.app.ui.PrimaryButton
import com.volo.app.ui.SelectChip
import com.volo.app.ui.components.AppSelector
import com.volo.app.ui.components.FieldCard
import com.volo.app.ui.components.LimitSelector
import com.volo.app.ui.components.StakeInput
import com.volo.app.ui.components.formatUsdc
import com.volo.app.ui.components.loadInstalledApps
import com.volo.app.ui.theme.Clay
import com.volo.app.ui.theme.Ink
import com.volo.app.ui.theme.Muted

private data class WindowOption(val label: String, val seconds: Long)

private val windowOptions = listOf(
    WindowOption("1 hour", 3_600L),
    WindowOption("4 hours", 14_400L),
    WindowOption("12 hours", 43_200L),
    WindowOption("24 hours", 86_400L),
    WindowOption("7 days", 604_800L)
)

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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Create a duel", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Make the promise specific. Make it matter.", color = Muted, fontSize = 15.sp)

        AppSelector(selectedPackage = targetPackage, onSelected = { targetPackage = it })

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DAILY LIMIT", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            LimitSelector(selectedMinutes = limitMinutes, onSelected = { limitMinutes = it })
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("WINDOW", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                windowOptions.forEach { option ->
                    SelectChip(
                        label = option.label,
                        selected = windowSeconds == option.seconds,
                        onClick = { windowSeconds = option.seconds }
                    )
                }
            }
        }

        StakeInput(
            stakeAmountRaw = stakeAmountRaw,
            onStakeChange = { stakeAmountRaw = it },
            walletConnected = walletConnected
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = opponent,
                onValueChange = { opponent = it },
                label = { Text("Opponent (wallet address or name.skr)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            val resolved = resolvedOpponent
            if (resolved != null && resolved != opponent.trim()) {
                Text("Resolves to $resolved", color = Muted, fontSize = 12.sp)
            } else if (resolved != null) {
                Text("Valid wallet address", color = Muted, fontSize = 12.sp)
            }
        }

        errors.forEach { error ->
            Text(error.message(), color = Clay, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton("Review duel") {
            if (errors.isEmpty()) showReview = true
        }
    }

    if (showReview) {
        AlertDialog(
            onDismissRequest = { showReview = false },
            title = { Text("Confirm duel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SummaryLine(
                        "App",
                        loadInstalledApps(androidx.compose.ui.platform.LocalContext.current)
                            .firstOrNull { it.packageName == targetPackage }?.label ?: targetPackage.orEmpty()
                    )
                    SummaryLine("Daily limit", "${limitMinutes} minutes")
                    SummaryLine("Window", windowOptions.firstOrNull { it.seconds == windowSeconds }?.label ?: "")
                    SummaryLine("Stake", "${formatUsdc(stakeAmountRaw)} USDC")
                    SummaryLine("Opponent", resolvedOpponent ?: opponent)
                }
            },
            confirmButton = {
                TextButton(onClick = {
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
                        endTime = now + requireNotNull(windowSeconds) * 1000L
                    )
                    scope.launch {
                        AppDatabase.getInstance(context).challengeDao().insert(challenge.toEntity())
                        onCreated()
                    }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showReview = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 13.sp)
        Text(value, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
    }
}

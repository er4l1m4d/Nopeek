package com.volo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volo.app.ui.theme.Ink
import com.volo.app.ui.theme.Muted
import kotlin.math.roundToLong

/**
 * Stake entry in whole USDC decimal form. Stored raw as USDC base units (1 USDC = 1_000_000).
 */
@Composable
fun StakeInput(
    stakeAmountRaw: Long,
    onStakeChange: (Long) -> Unit,
    walletConnected: Boolean
) {
    var text by remember { mutableStateOf(formatUsdc(stakeAmountRaw)) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                if (input.isEmpty() || input.matches(Regex("^\\d{0,3}(\\.\\d{0,2})?$"))) {
                    text = input
                    val amount = input.toDoubleOrNull()
                    if (amount != null) {
                        onStakeChange((amount * 1_000_000.0).roundToLong())
                    }
                }
            },
            label = { Text("Stake (USDC)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (walletConnected) {
                "Wallet connected · devnet USDC balance arrives with Phase 7"
            } else {
                "Connect your wallet on Profile to stake"
            },
            color = Muted,
            fontSize = 12.sp
        )
        Text(
            "= ${formatUsdc(stakeAmountRaw)} USDC",
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

fun formatUsdc(raw: Long): String = String.format("%.2f", raw / 1_000_000.0)

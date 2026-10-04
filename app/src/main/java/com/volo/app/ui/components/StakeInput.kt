package com.volo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType
import kotlin.math.roundToLong

/**
 * Stake entry in whole USDC decimal form. Stored raw as USDC base units (1 USDC = 1_000_000).
 */
@Composable
fun StakeInput(
    stakeAmountRaw: Long,
    onStakeChange: (Long) -> Unit,
    walletConnected: Boolean,
) {
    var text by remember { mutableStateOf(formatUsdc(stakeAmountRaw)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
    ) {
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
            label = { Text("Stake") },
            suffix = { Text("USDC", style = VoloType.monoSmall, color = VoloColors.textMuted) },
            textStyle = VoloType.mono,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                focusedSuffixColor = VoloColors.textMuted,
                unfocusedSuffixColor = VoloColors.textMuted,
            ),
        )
        Text(
            if (walletConnected) {
                "Wallet connected · devnet USDC balance arrives with Phase 7"
            } else {
                "Connect your wallet on Profile to stake"
            },
            style = VoloType.bodySmall,
            color = VoloColors.textMuted,
        )
        Text(
            "= ${formatUsdc(stakeAmountRaw)} USDC",
            style = VoloType.mono,
            color = VoloColors.text,
        )
    }
}

fun formatUsdc(raw: Long): String = String.format("%.2f", raw / 1_000_000.0)

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun StakeInputPreview() {
    VoloTheme {
        Column(modifier = Modifier.padding(VoloDimens.spaceLg)) {
            StakeInput(stakeAmountRaw = 5_000_000L, onStakeChange = {}, walletConnected = false)
        }
    }
}

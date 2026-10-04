package com.volo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LimitSelector(selectedMinutes: Int?, onSelected: (Int) -> Unit) {
    val presets = listOf(15, 30, 60, 120)
    var showCustom by remember { mutableStateOf(false) }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
    ) {
        presets.forEach { minutes ->
            SelectChip(
                label = if (minutes >= 60) "${minutes / 60}h" else "${minutes}m",
                selected = selectedMinutes == minutes,
                onClick = { onSelected(minutes) },
            )
        }
        SelectChip(
            label = "Custom",
            selected = selectedMinutes != null && selectedMinutes !in presets,
            onClick = { showCustom = true },
        )
    }

    if (showCustom) {
        CustomLimitDialog(
            initialMinutes = selectedMinutes?.takeIf { it !in presets },
            onDismiss = { showCustom = false },
            onConfirm = { minutes ->
                onSelected(minutes)
                showCustom = false
            },
        )
    }
}

@Composable
private fun CustomLimitDialog(initialMinutes: Int?, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(initialMinutes?.toString() ?: "") }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VoloColors.surfaceRaised,
        shape = RoundedCornerShape(VoloDimens.radiusCard),
        title = {
            Text("Custom daily limit", style = VoloType.title, color = VoloColors.text)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(4) },
                    label = { Text("Minutes per day") },
                    textStyle = VoloType.mono,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                if (text.isNotEmpty() && !valid) {
                    InlineError(
                        message = "Enter a positive number of minutes.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                label = "Set",
                onClick = { if (valid) onConfirm(parsed!!) },
                enabled = valid,
            )
        },
        dismissButton = {
            SecondaryButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun LimitSelectorPreview() {
    VoloTheme {
        Column(modifier = Modifier.padding(VoloDimens.spaceLg)) {
            LimitSelector(selectedMinutes = 30, onSelected = {})
        }
    }
}

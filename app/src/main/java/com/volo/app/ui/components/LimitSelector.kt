package com.volo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.volo.app.ui.SelectChip

@Composable
fun LimitSelector(selectedMinutes: Int?, onSelected: (Int) -> Unit) {
    val presets = listOf(15, 30, 60, 120)
    var showCustom by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { minutes ->
            SelectChip(
                label = if (minutes >= 60) "${minutes / 60}h" else "${minutes}m",
                selected = selectedMinutes == minutes,
                onClick = { onSelected(minutes) }
            )
        }
        SelectChip(
            label = "Custom",
            selected = selectedMinutes != null && selectedMinutes !in presets,
            onClick = { showCustom = true }
        )
    }

    if (showCustom) {
        CustomLimitDialog(
            initialMinutes = selectedMinutes?.takeIf { it !in presets },
            onDismiss = { showCustom = false },
            onConfirm = { minutes ->
                onSelected(minutes)
                showCustom = false
            }
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
        title = { Text("Custom daily limit") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter { it.isDigit() }.take(4) },
                label = { Text("Minutes per day") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(parsed!!) }, enabled = valid) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

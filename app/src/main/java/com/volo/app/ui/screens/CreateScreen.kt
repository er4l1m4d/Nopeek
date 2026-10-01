package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volo.app.ui.PrimaryButton
import com.volo.app.ui.theme.Ink
import com.volo.app.ui.theme.Muted

@Composable
fun CreateScreen(onCreated: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Create a duel", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Make the promise specific. Make it matter.", color = Muted, fontSize = 15.sp)
        FormRow("Target app", "Instagram")
        FormRow("Daily limit", "30 minutes")
        FormRow("Duration", "24 hours")
        FormRow("Stake", "5.00 USDC")
        FormRow("Opponent", "Enter wallet address")
        Spacer(Modifier.weight(1f))
        PrimaryButton("Review duel", onCreated)
    }
}

@Composable
private fun FormRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Text(value, color = if (value.startsWith("Enter")) Muted else Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

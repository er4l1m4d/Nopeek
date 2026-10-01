package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volo.app.ui.theme.DuelMuted
import com.volo.app.ui.theme.Ink
import com.volo.app.ui.theme.Muted

@Composable
fun ProfileScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Profile", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Wallet, contentDescription = null, tint = Color(0xFFD17A54), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text("Wallet connected", color = Color.White, fontWeight = FontWeight.Bold); Text("6k9T...cRWmmj", color = DuelMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            Text("2.48 USDC", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        ProfileRow("Seeker status", "Verification unavailable on this device")
        ProfileRow("SKR balance", "Not connected")
        ProfileRow("Usage access", "Enabled")
        ProfileRow("Privacy", "Review what Volo measures")
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(value, color = Muted, fontSize = 12.sp) }
        Icon(Icons.Outlined.ArrowForward, contentDescription = "Open $label", tint = Muted, modifier = Modifier.size(18.dp))
    }
}

package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Shield
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
import com.volo.app.ui.StatusChip
import com.volo.app.ui.theme.Celadon
import com.volo.app.ui.theme.DuelMuted
import com.volo.app.ui.theme.Ink
import com.volo.app.ui.theme.Muted

@Composable
fun HomeScreen(onOpenDuel: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp, 28.dp, 24.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tuesday, September 29", color = Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text("Stay in the frame.", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        item { DuelCard(onOpenDuel) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("18m", "of 30m used", Modifier.weight(1f))
                MetricCard("5 days", "current streak", Modifier.weight(1f))
            }
        }
        item { VerificationCard() }
    }
}

@Composable
private fun DuelCard(onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink).clickable(onClick = onOpen).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusChip("PASSING", Celadon, Ink)
            Text("ACTIVE DUEL", color = DuelMuted, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("No doomscrolling after 9pm", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Instagram · with Maya", color = DuelMuted, fontSize = 14.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("18m / 30m", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
                Text("5.00 USDC locked", color = DuelMuted, fontSize = 12.sp)
            }
            Icon(Icons.Outlined.ArrowForward, contentDescription = "View duel", tint = Color(0xFFD17A54))
        }
    }
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(value, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun VerificationCard() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Celadon.copy(alpha = .55f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Shield, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Usage access is on", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Your next report is due in 42 minutes.", color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Outlined.ArrowForward, contentDescription = "Open usage settings", tint = Ink, modifier = Modifier.size(18.dp))
    }
}

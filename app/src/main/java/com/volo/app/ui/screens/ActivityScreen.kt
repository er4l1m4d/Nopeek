package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volo.app.ui.theme.Celadon
import com.volo.app.ui.theme.Ink
import com.volo.app.ui.theme.Muted
import com.volo.app.ui.theme.SurfaceMuted

@Composable
fun ActivityScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Activity", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        val events = listOf("Duel created" to "Today, 8:42 PM", "Usage report verified" to "Today, 7:18 PM", "Stake locked" to "Today, 6:59 PM", "Previous stake returned" to "Sep 21")
        events.forEach { (title, time) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(if (title.contains("verified") || title.contains("returned")) Celadon else SurfaceMuted), contentAlignment = Alignment.Center) {
                    Icon(if (title.contains("Stake")) Icons.Outlined.Wallet else Icons.Outlined.Shield, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(time, color = Muted, fontSize = 12.sp) }
            }
        }
    }
}

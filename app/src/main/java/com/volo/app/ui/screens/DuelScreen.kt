package com.volo.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volo.app.ui.PrimaryButton
import com.volo.app.ui.theme.Duel
import com.volo.app.ui.theme.DuelCopper
import com.volo.app.ui.theme.DuelLine
import com.volo.app.ui.theme.DuelMuted
import com.volo.app.ui.theme.DuelSafe

@Composable
fun DuelScreen(pubkey: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Duel).padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White) }
        Text("ACTIVE DUEL · 24H WINDOW · $pubkey", color = DuelMuted, fontSize = 11.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
        Text("No doomscrolling\nafter 9pm", color = Color.White, fontSize = 34.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold)
        Text("Instagram · with Maya", color = DuelMuted, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) { UsageRing() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("YOUR USAGE", color = DuelMuted, fontSize = 10.sp, letterSpacing = 1.sp); Text("18m / 30m", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 17.sp) }
            Column(horizontalAlignment = Alignment.End) { Text("MAYA'S REPORT", color = DuelMuted, fontSize = 10.sp, letterSpacing = 1.sp); Text("22m / 30m", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 17.sp) }
        }
        OpponentBar()
        Spacer(Modifier.weight(1f))
        PrimaryButton("Check usage", {})
        Text("Last opponent report: 8 minutes ago", color = DuelMuted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun UsageRing() {
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(DuelLine, -90f, 360f, false, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
            drawArc(DuelCopper, -90f, 216f, false, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("18m", color = Color.White, fontSize = 42.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); Text("remaining 12m", color = DuelMuted, fontSize = 12.sp) }
    }
}

@Composable
private fun OpponentBar() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(DuelLine)) { Box(Modifier.fillMaxWidth(.73f).height(8.dp).clip(CircleShape).background(DuelSafe)) }
    }
}

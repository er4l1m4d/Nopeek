package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volo.app.ui.theme.Clay
import com.volo.app.ui.theme.Duel
import com.volo.app.ui.theme.DuelMuted
import com.volo.app.ui.theme.Paper

/**
 * Plan 8.2 Step 3: both-fail scenario -> Recovery Lock screen.
 * Shown when the oracle reports bothFail=true — stakes are locked on-chain
 * until recovery completes, so the screen explains the state and links the challenge.
 */
@Composable
fun RecoveryLockScreen(pubkey: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Duel)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Spacer(Modifier.height(48.dp))
        Column(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1B262B))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = Clay,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Clay.copy(alpha = 0.18f))
                    .padding(14.dp),
            )
            Text(
                "Recovery Lock",
                color = Paper,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Both players failed verification. The stake is locked on-chain " +
                    "and will not be paid out until recovery completes.",
                color = DuelMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
            )
            Text(
                pubkey,
                color = Clay,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

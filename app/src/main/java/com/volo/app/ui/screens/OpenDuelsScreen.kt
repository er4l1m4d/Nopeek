package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volo.app.ui.theme.Celadon
import com.volo.app.ui.theme.Ink
import com.volo.app.ui.theme.Muted
import com.volo.app.ui.theme.Paper

/**
 * Plan 9.1 Step 4: placeholder Open Duels screen behind the SKR gate —
 * demonstrates the gating mechanism only, no matching logic.
 */
@Composable
fun OpenDuelsScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
        }
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.EmojiEvents,
                contentDescription = null,
                tint = Celadon,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Celadon.copy(alpha = 0.25f))
                    .padding(16.dp),
            )
            Text(
                "Open Duels",
                color = Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                "Feature unlocked in full release",
                color = Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

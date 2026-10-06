package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.volo.app.ui.components.AppTile
import com.volo.app.ui.components.MiniTape
import com.volo.app.ui.components.StatusLabel
import com.volo.app.ui.components.TapIconButton
import com.volo.app.ui.components.lifecycleVariant
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.Volo
import com.volo.app.ui.theme.VoloShape
import com.volo.app.ui.theme.VoloText
import com.volo.app.ui.theme.VoloTheme

@Composable
fun RecoveryLockScreen(pubkey: String, onBack: () -> Unit) {
    val colors = Volo.colors
    val pubkeyShort = shorten(pubkey)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.warning)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TapIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(imageVector = Phosphor.ArrowLeft, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.onPrimary)
            }
        }
        Text(
            text = "DUEL LOCKED",
            style = VoloText.DisplayFigure,
            color = colors.onPrimary,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Sorry, we couldn't settle your duel. The pot is being held until a host can review.",
            style = VoloText.BodyLg,
            color = colors.onPrimary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            shape = VoloShape.Md,
            color = colors.onPrimary.copy(alpha = 0.12f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusLabel(variant = lifecycleVariant("BOTH_FAIL_LOCKED"), leading = Phosphor.LockKey)
                    Spacer(Modifier.size(8.dp))
                    Text(text = pubkeyShort, style = VoloText.BodyMd, color = colors.onPrimary)
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
            AppTile(modifier = Modifier.size(120.dp), icon = null, fallback = "LOCK")
        }
        MiniTape(
            youFraction = 1f,
            opponentFraction = 1f,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Column(modifier = Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "What happened", style = VoloText.TitleSm, color = colors.onPrimary)
            Text(
                text = "Both you and your opponent ended in conditions that prevented a clean settlement. The host will review the duel and refund any disputed stake.",
                style = VoloText.BodyMd,
                color = colors.onPrimary,
            )
        }
    }
}

private fun shorten(s: String, head: Int = 4, tail: Int = 4): String {
    if (s.length <= head + tail + 1) return s
    return "${s.take(head)}…${s.takeLast(tail)}"
}
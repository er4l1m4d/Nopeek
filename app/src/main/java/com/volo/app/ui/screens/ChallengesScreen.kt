package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volo.app.data.local.AppDatabase
import com.volo.app.data.local.ChallengeEntity
import com.volo.app.ui.StatusChip
import com.volo.app.ui.theme.Celadon
import com.volo.app.ui.theme.Clay
import com.volo.app.ui.theme.Copper
import com.volo.app.ui.theme.Ink
import com.volo.app.ui.theme.Muted
import com.volo.app.ui.theme.SurfaceMuted

/** Plan 7.3 Step 3: status -> chip label/colors (green=passing, red=exceeded/fail, yellow=pending). */
internal fun statusLabel(localStatus: String): String = when (localStatus) {
    "CREATED" -> "PENDING"
    "ACTIVE" -> "ACTIVE"
    "PENDING_VERIFICATION" -> "VERIFY"
    "BOTH_FAIL_LOCKED" -> "LOCKED"
    "SETTLED" -> "SETTLED"
    "REFUNDED" -> "REFUNDED"
    else -> localStatus
}

internal fun statusColors(localStatus: String): Pair<Color, Color> = when (localStatus) {
    "ACTIVE" -> Celadon to Ink
    "PENDING_VERIFICATION", "BOTH_FAIL_LOCKED" -> Clay to Color.White
    "CREATED" -> Copper to Color.White
    else -> SurfaceMuted to Muted
}

/**
 * Plan 10.1 Step 4: challenge rows badge known .skr opponents as
 * "Opponent: alice.skr [Unverified]"; raw wallets keep the short-address line.
 */
internal fun opponentSubtitle(opponentWallet: String, limitSeconds: Int): String {
    val handle = com.volo.app.data.identity.MockSeekerProvider.handleFor(opponentWallet)
    val left = if (handle != null) {
        "Opponent: $handle [Unverified]"
    } else {
        "vs ${opponentWallet.take(4)}…${opponentWallet.takeLast(4)}"
    }
    return "$left · ${limitSeconds / 60}m limit"
}

/**
 * Plan 8.2: an ACTIVE challenge whose window has ended is ready for the
 * user-signed on-chain settle transaction (pure — tested).
 */
internal fun canSettle(entity: ChallengeEntity, nowMs: Long): Boolean =
    entity.localStatus == "ACTIVE" &&
        entity.challengePubkey != null &&
        nowMs > entity.endTime

@Composable
fun ChallengesScreen(
    onOpenDuel: () -> Unit,
    onOpenRecovery: (String) -> Unit = {},
    onSettle: (ChallengeEntity) -> Unit = {},
) {
    val context = LocalContext.current
    val challenges by remember {
        AppDatabase.getInstance(context).challengeDao().observeActive()
    }.collectAsState(initial = emptyList())

    val activeCount = challenges.count { it.localStatus == "ACTIVE" }
    val pendingCount = challenges.count { it.localStatus == "CREATED" }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp)) {
        Text("Challenges", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("ACTIVE $activeCount", Copper, Color.White)
            Text(
                "Pending $pendingCount",
                color = Muted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (challenges.isEmpty()) {
            Text(
                "No active challenges yet. Create one from the Create tab.",
                color = Muted,
                fontSize = 13.sp,
            )
        } else {
            val now = System.currentTimeMillis()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                challenges.forEach { entity ->
                    ChallengeEntityRow(
                        entity = entity,
                        onClick = {
                            if (entity.localStatus == "BOTH_FAIL_LOCKED" && entity.challengePubkey != null) {
                                onOpenRecovery(entity.challengePubkey)
                            } else {
                                onOpenDuel()
                            }
                        },
                        onSettle = if (canSettle(entity, now)) onSettle else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeEntityRow(
    entity: ChallengeEntity,
    onClick: () -> Unit,
    onSettle: ((ChallengeEntity) -> Unit)? = null,
) {
    val (label, background, foreground) = statusPresentation(entity.localStatus)
    val detailColor = if (entity.localStatus == "ACTIVE") Copper else Muted
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                entity.targetApp,
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                opponentSubtitle(entity.opponentWallet, entity.limitSeconds),
                color = Muted,
                fontSize = 12.sp,
            )
            Text(
                "${entity.stakeAmountRaw / 1_000_000} USDC stake",
                color = detailColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (onSettle != null) {
            Text(
                "Settle",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Copper)
                    .clickable { onSettle(entity) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        StatusChip(label, background, foreground)
    }
}

private fun statusPresentation(localStatus: String): Triple<String, Color, Color> {
    val (background, foreground) = statusColors(localStatus)
    return Triple(statusLabel(localStatus), background, foreground)
}

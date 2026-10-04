package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.volo.app.data.local.AppDatabase
import com.volo.app.data.local.ChallengeEntity
import com.volo.app.ui.components.PrimaryButton
import com.volo.app.ui.components.RowCard
import com.volo.app.ui.components.SelectChip
import com.volo.app.ui.components.StatusChip
import com.volo.app.ui.components.StatusVariant
import com.volo.app.ui.components.lifecycleVariant
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloType

private enum class ChallengeFilter(val label: String) {
    ACTIVE("Active"),
    PENDING("Pending"),
    COMPLETED("Completed"),
}

private fun ChallengeFilter.matches(localStatus: String): Boolean = when (this) {
    ChallengeFilter.ACTIVE ->
        localStatus == "ACTIVE" ||
            localStatus == "PENDING_VERIFICATION" ||
            localStatus == "BOTH_FAIL_LOCKED"
    ChallengeFilter.PENDING -> localStatus == "CREATED"
    ChallengeFilter.COMPLETED ->
        localStatus == "SETTLED" || localStatus == "REFUNDED"
}

private fun statusLeadingIcon(variant: StatusVariant): ImageVector? = when (variant.container) {
    VoloColors.safe -> Phosphor.CheckCircle
    VoloColors.risk -> Phosphor.WarningCircle
    VoloColors.fail -> Phosphor.XCircle
    else -> null
}

private fun windowLabel(windowSeconds: Long): String = when {
    windowSeconds <= 0L -> "—"
    windowSeconds % 86_400L == 0L -> "${windowSeconds / 86_400L}d window"
    windowSeconds % 3_600L == 0L -> "${windowSeconds / 3_600L}h window"
    windowSeconds % 60L == 0L -> "${windowSeconds / 60L}m window"
    else -> "${windowSeconds}s window"
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
    var filter by remember { mutableStateOf(ChallengeFilter.ACTIVE) }
    val visible = remember(challenges, filter) {
        challenges.filter { filter.matches(it.localStatus) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(VoloColors.bg),
        contentPadding = PaddingValues(
            horizontal = VoloDimens.spaceXl,
            vertical = VoloDimens.spaceXxl,
        ),
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceLg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd)) {
                Text(
                    text = "Challenges",
                    style = VoloType.headline,
                    color = VoloColors.text,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm)) {
                    ChallengeFilter.entries.forEach { option ->
                        SelectChip(
                            label = option.label,
                            selected = filter == option,
                            onClick = { filter = option },
                        )
                    }
                }
            }
        }
        if (challenges.isEmpty()) {
            item {
                Text(
                    text = "No challenges yet. Create one from the Create tab.",
                    style = VoloType.bodySmall,
                    color = VoloColors.textMuted,
                )
            }
        } else if (visible.isEmpty()) {
            item {
                Text(
                    text = "No ${filter.label.lowercase()} challenges yet.",
                    style = VoloType.bodySmall,
                    color = VoloColors.textMuted,
                )
            }
        } else {
            val now = System.currentTimeMillis()
            items(visible, key = { it.id }) { entity ->
                Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm)) {
                    ChallengeEntityRow(
                        entity = entity,
                        onClick = {
                            if (entity.localStatus == "BOTH_FAIL_LOCKED" && entity.challengePubkey != null) {
                                onOpenRecovery(entity.challengePubkey)
                            } else {
                                onOpenDuel()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (canSettle(entity, now)) {
                        PrimaryButton(
                            label = "Settle",
                            onClick = { onSettle(entity) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeEntityRow(
    entity: ChallengeEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val variant = lifecycleVariant(entity.localStatus)
    RowCard(
        title = entity.targetApp,
        subtitle = "${opponentSubtitle(entity.opponentWallet, entity.limitSeconds)} · ${windowLabel(entity.windowSeconds)}",
        onClick = onClick,
        modifier = modifier,
        trailing = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs),
            ) {
                Text(
                    text = "${entity.stakeAmountRaw / 1_000_000} USDC stake",
                    style = VoloType.monoSmall,
                    color = VoloColors.textMuted,
                )
                StatusChip(
                    variant = variant,
                    leading = statusLeadingIcon(variant),
                )
            }
        },
    )
}

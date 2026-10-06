package com.nopeek.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nopeek.app.data.identity.MockSeekerProvider
import com.nopeek.app.data.local.AppDatabase
import com.nopeek.app.data.local.ChallengeEntity
import com.nopeek.app.ui.components.FixtureRow
import com.nopeek.app.ui.components.StatusVariant
import com.nopeek.app.ui.components.lifecycleVariant
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekRef
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme
import java.util.Locale

enum class DuelTab(val label: String) {
    ACTIVE("Active"),
    INVITES("Invites"),
    SETTLED("Settled"),
}

private fun DuelTab.matches(localStatus: String): Boolean = when (this) {
    DuelTab.ACTIVE -> localStatus != "CREATED" && localStatus != "SETTLED" && localStatus != "REFUNDED"
    DuelTab.INVITES -> localStatus == "CREATED"
    DuelTab.SETTLED -> localStatus == "SETTLED" || localStatus == "REFUNDED"
}

private fun statusLeadingIcon(variant: StatusVariant): ImageVector? {
    val color = variant.color
    return when {
        color == NopeekRef.Volt -> Phosphor.CheckCircle
        color == NopeekRef.Amber -> Phosphor.WarningCircle
        color == NopeekRef.Coral -> Phosphor.XCircle
        else -> null
    }
}

private fun windowLabel(windowSeconds: Long): String = when {
    windowSeconds <= 0L -> "—"
    windowSeconds % 86_400L == 0L -> "${windowSeconds / 86_400L}d window"
    windowSeconds % 3_600L == 0L -> "${windowSeconds / 3_600L}h window"
    windowSeconds % 60L == 0L -> "${windowSeconds / 60L}m window"
    else -> "${windowSeconds}s window"
}

fun formatUsdc(raw: Long): String = String.format(Locale.US, "%.2f", raw / 1_000_000.0)

fun totalAtStake(entities: List<ChallengeEntity>): String {
    var total = 0L
    entities.filter { DuelTab.ACTIVE.matches(it.localStatus) }.forEach { total += it.stakeAmountRaw }
    return "$" + formatUsdc(total)
}

fun timeLeftLabel(endTimeMs: Long, nowMs: Long): String {
    val left = endTimeMs - nowMs
    if (left <= 0L) return "Ended"
    val mins = left / 60_000L
    return if (mins < 60L) "$mins min left" else "${mins / 60}h ${mins % 60}m left"
}

fun opponentSubtitle(opponentWallet: String, limitSeconds: Int): String {
    val handle = MockSeekerProvider.handleFor(opponentWallet)
    val left = if (handle != null) "Opponent: $handle [Unverified]" else "vs ${opponentWallet.take(4)}…${opponentWallet.takeLast(4)}"
    return "$left · ${limitSeconds / 60}m limit"
}

fun canSettle(entity: ChallengeEntity, nowMs: Long): Boolean {
    return entity.localStatus == "ACTIVE" && entity.challengePubkey != null && nowMs > entity.endTime
}

@Composable
fun DuelsScreen(
    onOpenDuel: () -> Unit,
    onOpenRecovery: ((String) -> Unit)? = null,
    onSettle: ((ChallengeEntity) -> Unit)? = null,
    onNewDuel: (() -> Unit)? = null,
) {
    val colors = Nopeek.colors
    val context = LocalContext.current
    val challenges by remember {
        AppDatabase.getInstance(context).challengeDao().observeActive()
    }.collectAsState(emptyList())
    var tab by remember { mutableStateOf(DuelTab.ACTIVE) }
    val counts = remember(challenges) {
        DuelTab.entries.associateWith { t -> challenges.count { t.matches(it.localStatus) } }
    }
    val visible = remember(challenges, tab) {
        challenges.filter { tab.matches(it.localStatus) }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
        ) {
            item {
                Text(text = "DUELS", style = NopeekText.DisplayTitle, color = colors.onSurface)
                Text(
                    text = if (challenges.isEmpty()) "No stakes yet. Start your first duel." else "You have ${totalAtStake(challenges)} on the line.",
                    style = NopeekText.BodyMd,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                DuelTabs(
                    tab = tab,
                    counts = counts,
                    onSelect = { tab = it },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        text = "No ${tab.label.lowercase(Locale.ROOT)} duels yet.",
                        style = NopeekText.BodyMd,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            } else {
                val now = System.currentTimeMillis()
                items(visible, key = { it.id }) { entity ->
                    DuelEntityRow(
                        entity = entity,
                        nowMs = now,
                        showTape = tab == DuelTab.ACTIVE,
                        onClick = {
                            when {
                                entity.localStatus == "BOTH_FAIL_LOCKED" -> entity.challengePubkey?.let { onOpenRecovery?.invoke(it) }
                                canSettle(entity, System.currentTimeMillis()) -> onSettle?.invoke(entity)
                                else -> onOpenDuel()
                            }
                        },
                    )
                }
            }
        }
        NewDuelFab(
            onClick = { onNewDuel?.invoke() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun DuelTabs(
    tab: DuelTab,
    counts: Map<DuelTab, Int>,
    onSelect: (DuelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            DuelTab.entries.forEach { option ->
                TabCell(
                    label = "${option.label} ${counts[option] ?: 0}",
                    selected = tab == option,
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabCell(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Nopeek.colors
    Column(
        modifier = modifier
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = NopeekText.LabelLg,
            color = if (selected) colors.onSurface else colors.onSurfaceMuted,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(3.dp)
                .background(if (selected) colors.primary else Color.Transparent),
        )
    }
}

@Composable
fun NewDuelFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Nopeek.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = true,
        shape = CircleShape,
        color = colors.primary,
        contentColor = colors.onPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Phosphor.PlusBold, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text = "New duel", style = NopeekText.TitleSm)
        }
    }
}

@Composable
private fun DuelEntityRow(
    entity: ChallengeEntity,
    nowMs: Long,
    showTape: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val variant = lifecycleVariant(entity.localStatus)
    val handle = MockSeekerProvider.handleFor(entity.opponentWallet)
    val title = if (handle != null) "vs $handle" else "vs ${entity.opponentWallet.take(4)}…${entity.opponentWallet.takeLast(4)}"
    FixtureRow(
        title = title,
        subtitle = "${entity.targetApp}, ${entity.limitSeconds / 60} min a day",
        status = variant,
        statusLeading = statusLeadingIcon(variant),
        youFraction = 0f,
        opponentFraction = 0f,
        amount = "$" + formatUsdc(entity.stakeAmountRaw),
        timeLeft = timeLeftLabel(entity.endTime, nowMs),
        onClick = onClick,
        modifier = modifier,
        appIcon = null,
        appFallback = entity.targetApp.take(2).uppercase(Locale.ROOT),
        showTape = showTape,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun DuelTabsPreview() {
    NopeekTheme {
        DuelTabs(
            tab = DuelTab.ACTIVE,
            counts = mapOf(DuelTab.ACTIVE to 2, DuelTab.INVITES to 1, DuelTab.SETTLED to 14),
            onSelect = {},
        )
    }
}

package com.volo.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.components.MetricRow
import com.volo.app.ui.components.PrimaryButton
import com.volo.app.ui.components.SectionHeader
import com.volo.app.ui.components.StatusChip
import com.volo.app.ui.components.TapIconButton
import com.volo.app.ui.components.lifecycleVariant
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

/**
 * Recovery Lock on the dark design system (demo data only).
 * Shown when the oracle reports bothFail — stakes are locked on-chain
 * until recovery completes.
 * Anatomy: fail-accented treatment, mono lock metadata/countdown,
 * explanation + single [PrimaryButton].
 */
@Composable
fun RecoveryLockScreen(pubkey: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoloColors.bg)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = VoloDimens.spaceXl,
                vertical = VoloDimens.spaceXxl,
            ),
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceLg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
        ) {
            TapIconButton(
                contentDescription = "Back",
                onClick = onBack,
            ) {
                Icon(
                    imageVector = Phosphor.ArrowLeft,
                    contentDescription = null,
                    tint = VoloColors.text,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = pubkey,
                style = VoloType.monoSmall,
                color = VoloColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        StatusChip(
            variant = lifecycleVariant("BOTH_FAIL_LOCKED"),
            leading = Phosphor.XCircle,
        )
        Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs)) {
            Text(
                text = "Recovery lock",
                style = VoloType.headline,
                color = VoloColors.text,
            )
            Text(
                text = "Both players failed verification. The stake is locked on-chain and will not be paid out until recovery completes.",
                style = VoloType.bodySmall,
                color = VoloColors.textMuted,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(VoloDimens.radiusCard),
            color = VoloColors.surfaceRaised,
            contentColor = VoloColors.text,
            border = BorderStroke(1.dp, VoloColors.line),
        ) {
            Column(
                modifier = Modifier.padding(VoloDimens.spaceXl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(VoloColors.fail.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Phosphor.LockKey,
                        contentDescription = null,
                        tint = VoloColors.fail,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = "UNLOCKS IN 23:59:59",
                    style = VoloType.mono,
                    color = VoloColors.fail,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Nobody can withdraw while the lock is active — recovery releases the stake automatically.",
                    style = VoloType.bodySmall,
                    color = VoloColors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        SectionHeader(
            eyebrow = "RECOVERY",
            title = "Lock details",
            modifier = Modifier.fillMaxWidth(),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(VoloDimens.radiusCard),
            color = VoloColors.surface,
            contentColor = VoloColors.text,
            border = BorderStroke(1.dp, VoloColors.line),
        ) {
            Column(
                modifier = Modifier.padding(VoloDimens.spaceLg),
                verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
            ) {
                MetricRow(
                    title = "Challenge",
                    value = shorten(pubkey),
                    modifier = Modifier.fillMaxWidth(),
                )
                MetricRow(
                    title = "Locked stake",
                    value = "10.00 USDC",
                    modifier = Modifier.fillMaxWidth(),
                )
                MetricRow(
                    title = "Recovery window",
                    value = "72H",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        PrimaryButton(
            label = "Back to challenges",
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun shorten(address: String): String =
    if (address.length <= 12) address else "${address.take(4)}…${address.takeLast(6)}"

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun RecoveryLockScreenPreview() {
    VoloTheme {
        RecoveryLockScreen(pubkey = "Hx8vQ9demoChallenge42", onBack = {})
    }
}

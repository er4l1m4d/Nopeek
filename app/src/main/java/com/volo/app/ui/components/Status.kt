package com.volo.app.ui.components

import androidx.compose.ui.graphics.Color
import com.volo.app.ui.theme.VoloColors

data class StatusVariant(
    val label: String,
    val container: Color,
    val content: Color,
)

enum class UsageState {
    PASSING,
    AT_RISK,
    FAILED,
    UNVERIFIED,
}

fun lifecycleVariant(localStatus: String): StatusVariant {
    if (localStatus.isBlank()) return StatusVariant("UNKNOWN", VoloColors.surfaceHigh, VoloColors.textMuted)
    return when (localStatus) {
    "ACTIVE" -> StatusVariant("ACTIVE", VoloColors.safe, VoloColors.bg)
    "CREATED" -> StatusVariant("PENDING", VoloColors.copper, VoloColors.bg)
    "PENDING_VERIFICATION" -> StatusVariant("VERIFY", VoloColors.risk, VoloColors.bg)
    "BOTH_FAIL_LOCKED" -> StatusVariant("LOCKED", VoloColors.fail, VoloColors.bg)
    "SETTLED" -> StatusVariant("SETTLED", VoloColors.surfaceHigh, VoloColors.textMuted)
    "REFUNDED" -> StatusVariant("REFUNDED", VoloColors.surfaceHigh, VoloColors.textMuted)
    else -> StatusVariant(localStatus, VoloColors.surfaceHigh, VoloColors.textMuted)
    }
}

fun usageVariant(state: UsageState): StatusVariant = when (state) {
    UsageState.PASSING -> StatusVariant("PASSING", VoloColors.safe, VoloColors.bg)
    UsageState.AT_RISK -> StatusVariant("AT RISK", VoloColors.risk, VoloColors.bg)
    UsageState.FAILED -> StatusVariant("FAILED", VoloColors.fail, VoloColors.bg)
    UsageState.UNVERIFIED -> StatusVariant("UNVERIFIED", VoloColors.surfaceHigh, VoloColors.textMuted)
}

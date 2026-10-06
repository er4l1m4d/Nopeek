package com.nopeek.app.ui.components

import androidx.compose.ui.graphics.Color
import com.nopeek.app.ui.theme.NopeekDarkColors

data class StatusVariant(
    val label: String,
    val container: Color,
    val content: Color,
) {
    val color: Color get() = content
    constructor(label: String, color: Color) : this(label, color, color)
}

enum class UsageState {
    PASSING,
    AT_RISK,
    FAILED,
    UNVERIFIED,
}

fun lifecycleVariant(localStatus: String): StatusVariant {
    val bg = com.nopeek.app.ui.theme.NopeekColors.bg
    val safe = com.nopeek.app.ui.theme.NopeekColors.safe
    val copper = com.nopeek.app.ui.theme.NopeekColors.copper
    val risk = com.nopeek.app.ui.theme.NopeekColors.risk
    val fail = com.nopeek.app.ui.theme.NopeekColors.fail
    val surfaceHigh = com.nopeek.app.ui.theme.NopeekColors.surfaceHigh
    val textMuted = com.nopeek.app.ui.theme.NopeekColors.textMuted
    return when (localStatus) {
        "ACTIVE" -> StatusVariant("ACTIVE", safe, bg)
        "CREATED" -> StatusVariant("PENDING", copper, bg)
        "PENDING_VERIFICATION" -> StatusVariant("VERIFY", risk, bg)
        "BOTH_FAIL_LOCKED" -> StatusVariant("LOCKED", fail, bg)
        "SETTLED" -> StatusVariant("SETTLED", surfaceHigh, textMuted)
        "REFUNDED" -> StatusVariant("REFUNDED", surfaceHigh, textMuted)
        else -> StatusVariant(localStatus, surfaceHigh, textMuted)
    }
}

fun usageVariant(state: UsageState): StatusVariant {
    val bg = com.nopeek.app.ui.theme.NopeekColors.bg
    val safe = com.nopeek.app.ui.theme.NopeekColors.safe
    val risk = com.nopeek.app.ui.theme.NopeekColors.risk
    val fail = com.nopeek.app.ui.theme.NopeekColors.fail
    val surfaceHigh = com.nopeek.app.ui.theme.NopeekColors.surfaceHigh
    val textMuted = com.nopeek.app.ui.theme.NopeekColors.textMuted
    return when (state) {
        UsageState.PASSING -> StatusVariant("PASSING", safe, bg)
        UsageState.AT_RISK -> StatusVariant("AT RISK", risk, bg)
        UsageState.FAILED -> StatusVariant("FAILED", fail, bg)
        UsageState.UNVERIFIED -> StatusVariant("UNVERIFIED", surfaceHigh, textMuted)
    }
}

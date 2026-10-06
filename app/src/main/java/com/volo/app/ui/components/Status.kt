package com.volo.app.ui.components

import androidx.compose.ui.graphics.Color
import com.volo.app.ui.theme.VoloDarkColors

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
    val bg = com.volo.app.ui.theme.VoloColors.bg
    val safe = com.volo.app.ui.theme.VoloColors.safe
    val copper = com.volo.app.ui.theme.VoloColors.copper
    val risk = com.volo.app.ui.theme.VoloColors.risk
    val fail = com.volo.app.ui.theme.VoloColors.fail
    val surfaceHigh = com.volo.app.ui.theme.VoloColors.surfaceHigh
    val textMuted = com.volo.app.ui.theme.VoloColors.textMuted
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
    val bg = com.volo.app.ui.theme.VoloColors.bg
    val safe = com.volo.app.ui.theme.VoloColors.safe
    val risk = com.volo.app.ui.theme.VoloColors.risk
    val fail = com.volo.app.ui.theme.VoloColors.fail
    val surfaceHigh = com.volo.app.ui.theme.VoloColors.surfaceHigh
    val textMuted = com.volo.app.ui.theme.VoloColors.textMuted
    return when (state) {
        UsageState.PASSING -> StatusVariant("PASSING", safe, bg)
        UsageState.AT_RISK -> StatusVariant("AT RISK", risk, bg)
        UsageState.FAILED -> StatusVariant("FAILED", fail, bg)
        UsageState.UNVERIFIED -> StatusVariant("UNVERIFIED", surfaceHigh, textMuted)
    }
}

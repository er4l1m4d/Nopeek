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
    val scheme = NopeekDarkColors
    return when (localStatus) {
        "ACTIVE" -> StatusVariant("ACTIVE", scheme.you, scheme.background)
        "CREATED" -> StatusVariant("PENDING", scheme.copper, scheme.background)
        "PENDING_VERIFICATION" -> StatusVariant("VERIFY", scheme.warning, scheme.background)
        "BOTH_FAIL_LOCKED" -> StatusVariant("LOCKED", scheme.danger, scheme.background)
        "SETTLED" -> StatusVariant("SETTLED", scheme.surfaceHigh, scheme.onSurfaceMuted)
        "REFUNDED" -> StatusVariant("REFUNDED", scheme.surfaceHigh, scheme.onSurfaceMuted)
        else -> StatusVariant(localStatus, scheme.surfaceHigh, scheme.onSurfaceMuted)
    }
}

fun usageVariant(state: UsageState): StatusVariant {
    val scheme = NopeekDarkColors
    return when (state) {
        UsageState.PASSING -> StatusVariant("PASSING", scheme.you, scheme.background)
        UsageState.AT_RISK -> StatusVariant("AT RISK", scheme.warning, scheme.background)
        UsageState.FAILED -> StatusVariant("FAILED", scheme.danger, scheme.background)
        UsageState.UNVERIFIED -> StatusVariant("UNVERIFIED", scheme.surfaceHigh, scheme.onSurfaceMuted)
    }
}

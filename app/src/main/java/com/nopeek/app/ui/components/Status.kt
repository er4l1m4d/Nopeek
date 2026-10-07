package com.nopeek.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.NopeekDarkColors

/**
 * The rendered pair for a status pill. [StatusLabel] fills [container] and paints [content] on it,
 * so every pair must clear AA on its own — a variant is never "text colour plus someone else's
 * background". Live/risk states take a loud fill with Pitch text; terminal states stay quiet
 * (Turf2 with Chalk text) so a settled duel never outshouts the one you are in.
 *
 * [glyph] belongs to the state, not to the caller: inferring it from colour left the warning and
 * danger rows with no glyph at all, which is colour-only communication.
 */
data class StatusVariant(
    val label: String,
    val container: Color,
    val content: Color,
    val glyph: ImageVector? = null,
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
        "ACTIVE" -> StatusVariant("Live", scheme.you, scheme.background)
        "CREATED" -> StatusVariant("Waiting", scheme.copper, scheme.background)
        "PENDING_VERIFICATION" -> StatusVariant("Checking", scheme.warning, scheme.background)
        "BOTH_FAIL_LOCKED" -> StatusVariant("Locked", scheme.danger, scheme.background, Phosphor.LockKey)
        "SETTLED" -> StatusVariant("Settled", scheme.surfaceHigh, scheme.onSurface)
        "REFUNDED" -> StatusVariant("Refunded", scheme.surfaceHigh, scheme.onSurface)
        else -> StatusVariant(localStatus, scheme.surfaceHigh, scheme.onSurface)
    }
}

fun usageVariant(state: UsageState): StatusVariant {
    val scheme = NopeekDarkColors
    return when (state) {
        UsageState.PASSING -> StatusVariant("Safe", scheme.you, scheme.background, Phosphor.CheckCircle)
        UsageState.AT_RISK -> StatusVariant("At risk", scheme.warning, scheme.background, Phosphor.WarningCircle)
        UsageState.FAILED -> StatusVariant("Over the limit", scheme.danger, scheme.background, Phosphor.XCircle)
        UsageState.UNVERIFIED -> StatusVariant("Unverified", scheme.surfaceHigh, scheme.onSurface)
    }
}

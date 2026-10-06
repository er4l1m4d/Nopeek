package com.nopeek.app.ui.components

fun fractionOf(value: Float, max: Float): Float {
    if (max <= 0f || value <= 0f) return 0f
    return (value / max).coerceAtMost(1f)
}

fun sweepDegrees(fraction: Float): Float = fraction.coerceIn(0f, 1f) * 360f

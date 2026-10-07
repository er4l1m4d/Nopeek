package com.nopeek.app.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * One WCAG 2.x contrast implementation for the whole test source set.
 *
 * The design system's own bar is >= 5.5 for text (stricter than AA's 4.5); non-text cues are held
 * to AA's 3.0. Keeping the math here stops each test file from quietly redefining the threshold.
 */
const val AA_TEXT = 4.5
const val AA_LARGE_TEXT = 3.0
const val AA_NON_TEXT = 3.0
const val DESIGN_SYSTEM_TEXT = 5.5

fun contrastOf(a: Color, b: Color): Double {
    val l1 = luminanceOf(a)
    val l2 = luminanceOf(b)
    val hi = if (l1 > l2) l1 else l2
    val lo = if (l1 > l2) l2 else l1
    return (hi + 0.05) / (lo + 0.05)
}

/** Flattens [fg] over [bg] at its own alpha, so translucent tokens are judged as rendered. */
fun composited(fg: Color, bg: Color): Color {
    val a = fg.alpha.coerceIn(0f, 1f)
    return Color(
        red = fg.red * a + bg.red * (1f - a),
        green = fg.green * a + bg.green * (1f - a),
        blue = fg.blue * a + bg.blue * (1f - a),
        alpha = 1f,
    )
}

fun contrastMessage(what: String, ratio: Double, floor: Double): String =
    "$what measures ${"%.2f".format(ratio)}:1 and needs >= ${"%.1f".format(floor)}:1"

private fun luminanceOf(color: Color): Double {
    fun channel(value: Float): Double {
        val s = value.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

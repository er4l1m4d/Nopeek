package com.nopeek.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

val NopeekEase: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, "animator_duration_scale", 1f) == 0f
    }
}

@Composable
fun animatedFraction(
    target: Float,
    durationMs: Int = 1300,
    delayMs: Int = 0,
): Float {
    if (rememberReducedMotion()) return target
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val value by animateFloatAsState(
        if (started) target else 0f,
        tween(durationMs, delayMs, NopeekEase),
        0f,
        "tape",
    )
    return value
}

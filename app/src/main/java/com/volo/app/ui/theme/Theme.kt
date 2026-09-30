package com.volo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VoloColors = lightColorScheme(
    primary = Color(0xFFB7613F),
    onPrimary = Color.White,
    background = Color(0xFFF4F5F1),
    onBackground = Color(0xFF172126),
    surface = Color.White,
    onSurface = Color(0xFF172126),
    outline = Color(0xFFD7DEDA)
)

@Composable
fun VoloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VoloColors, content = content)
}

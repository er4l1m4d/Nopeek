package com.nopeek.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme

@Composable
fun Radio(on: Boolean, modifier: Modifier = Modifier) {
    val colors = Nopeek.colors
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (on) colors.you else Color.Transparent)
            .border(2.dp, if (on) Color.Transparent else colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) {
            Icon(imageVector = Phosphor.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.background)
        }
    }
}

@Composable
fun AppRow(
    name: String,
    average: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    fallback: String? = "?",
) {
    val colors = Nopeek.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(top = 8.dp)
            .heightIn(min = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTile(icon = icon, fallback = fallback)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = NopeekText.TitleSm, color = colors.onSurface)
            Text(text = average, style = NopeekText.BodyMd, color = colors.onSurfaceMuted)
        }
        Radio(on = selected)
    }
}

@Composable
fun ChecklistRow(title: String, subtitle: String, done: Boolean, modifier: Modifier = Modifier) {
    val colors = Nopeek.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Radio(on = done)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = NopeekText.TitleSm, color = colors.onSurface)
            Text(text = subtitle, style = NopeekText.BodyMd, color = colors.onSurfaceMuted)
        }
    }
}

@Composable
fun PasteField(prompt: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Nopeek.colors
    Surface(
        onClick = onAction,
        modifier = modifier.height(56.dp),
        enabled = true,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        contentColor = colors.onSurfaceMuted,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, colors.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = prompt, style = NopeekText.BodyMd, color = colors.onSurfaceMuted, modifier = Modifier.weight(1f))
            Text(text = actionLabel, style = NopeekText.LabelLg, color = colors.primary)
        }
    }
}

@Composable
fun ReviewRow(label: String, value: String, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Nopeek.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onEdit)
            .heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = NopeekText.BodyMd, color = colors.onSurfaceMuted, modifier = Modifier.weight(1f))
        Text(text = value, style = NopeekText.LabelLg, color = colors.onSurface)
        Icon(imageVector = Phosphor.CaretRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onSurfaceMuted)
    }
}

@Composable
fun StepperBar(step: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = Nopeek.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(total) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (i < step) colors.primary else colors.surfaceHigh),
                )
            }
        }
        Text(text = "$step of $total", style = NopeekText.LabelLg, color = colors.onSurfaceMuted)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun RowsPreview() {
    NopeekTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppRow(name = "TikTok", average = "1h 52m a day on average", selected = true, onClick = {})
            ChecklistRow(title = "Wallet connected", subtitle = "Phantom, 7xKp…9fQ2", done = true)
            PasteField(prompt = "Paste an address or .skr name", actionLabel = "Paste", onAction = {})
            ReviewRow(label = "Daily limit", value = "30 min", onEdit = {})
            StepperBar(step = 2, total = 6)
        }
    }
}

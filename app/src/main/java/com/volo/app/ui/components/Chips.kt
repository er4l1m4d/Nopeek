package com.volo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.icons.Phosphor

import com.volo.app.ui.theme.Volo
import com.volo.app.ui.theme.VoloText
import com.volo.app.ui.theme.VoloTheme

@Composable
fun StatusLabel(
    variant: StatusVariant,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(imageVector = leading, contentDescription = null, modifier = Modifier.size(14.dp), tint = variant.color)
        }
        Text(text = variant.label, style = VoloText.LabelLg, color = variant.color)
    }
}

@Composable
fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Volo.colors
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = false,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (selected) colors.you else colors.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) colors.you else colors.outline),
    ) {
        Box(modifier = Modifier.height(48.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(text = label, style = VoloText.TitleSm, color = if (selected) colors.you else colors.onSurface)
        }
    }
}

/** SelectChip delegates to PresetChip for backward compatibility. */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PresetChip(label = label, selected = selected, onClick = onClick, modifier = modifier)
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun StatusLabelPreview() {
    VoloTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusLabel(variant = usageVariant(UsageState.PASSING), leading = Phosphor.CheckCircle)
            StatusLabel(variant = usageVariant(UsageState.AT_RISK), leading = Phosphor.WarningCircle)
            StatusLabel(variant = usageVariant(UsageState.FAILED), leading = Phosphor.XCircle)
            StatusLabel(variant = lifecycleVariant("CREATED"))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun PresetChipPreview() {
    VoloTheme {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PresetChip(label = "30", selected = true, onClick = {})
            PresetChip(label = "60", selected = false, onClick = {})
            PresetChip(label = "120", selected = false, onClick = {})
        }
    }
}

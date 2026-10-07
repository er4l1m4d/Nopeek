package com.nopeek.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nopeek.app.ui.icons.Phosphor

import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekShape
import com.nopeek.app.ui.theme.NopeekSize
import com.nopeek.app.ui.theme.NopeekSpace
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme

@Composable
fun StatusLabel(
    variant: StatusVariant,
    modifier: Modifier = Modifier,
    leading: ImageVector? = variant.glyph,
) {
    Row(
        modifier = modifier
            .clip(NopeekShape.Full)
            .background(variant.container)
            .padding(horizontal = NopeekSpace.S8, vertical = NopeekSpace.S4),
        horizontalArrangement = Arrangement.spacedBy(NopeekSpace.S4, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(imageVector = leading, contentDescription = null, modifier = Modifier.size(14.dp), tint = variant.color)
        }
        Text(text = variant.label, style = NopeekText.LabelSm, color = variant.color)
    }
}

@Composable
fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Nopeek.colors
    val container by animateColorAsState(
        targetValue = if (selected && enabled) colors.you else colors.surfaceHigh,
        animationSpec = tween(180, easing = NopeekEase),
        label = "presetChipContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> colors.onSurfaceMuted
            selected -> colors.onPrimary
            else -> colors.onSurface
        },
        animationSpec = tween(180, easing = NopeekEase),
        label = "presetChipContent",
    )
    // The unselected tonal fill alone measures 1.73:1 against Pitch, which reads as floating text
    // rather than an option; the hairline gives the row edges without competing with the volt
    // selection. Selected state changes fill, ring, tick and text colour together, so it is never
    // signalled by hue alone.
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            this[SemanticsProperties.Role] = Role.RadioButton
            this.selected = selected
        },
        enabled = enabled,
        shape = NopeekShape.Full,
        color = container,
        contentColor = content,
        border = if (selected) null else BorderStroke(1.dp, colors.outline),
    ) {
        Row(
            modifier = Modifier.height(NopeekSize.ButtonStd).padding(horizontal = NopeekSpace.S16),
            horizontalArrangement = Arrangement.spacedBy(NopeekSpace.S8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The tick slot is always measured, so selecting never reflows the row.
            Box(modifier = Modifier.size(16.dp).alpha(if (selected) 1f else 0f)) {
                Icon(imageVector = Phosphor.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = content)
            }
            Text(text = label, style = NopeekText.TitleSm, color = content)
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
    NopeekTheme {
        Column(modifier = Modifier.padding(NopeekSpace.S16), verticalArrangement = Arrangement.spacedBy(NopeekSpace.S12)) {
            StatusLabel(variant = usageVariant(UsageState.PASSING))
            StatusLabel(variant = usageVariant(UsageState.AT_RISK))
            StatusLabel(variant = usageVariant(UsageState.FAILED))
            StatusLabel(variant = lifecycleVariant("CREATED"))
            StatusLabel(variant = lifecycleVariant("BOTH_FAIL_LOCKED"))
            StatusLabel(variant = lifecycleVariant("SETTLED"))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun PresetChipPreview() {
    NopeekTheme {
        Row(modifier = Modifier.padding(NopeekSpace.S16), horizontalArrangement = Arrangement.spacedBy(NopeekSpace.S12)) {
            PresetChip(label = "30", selected = true, onClick = {})
            PresetChip(label = "60", selected = false, onClick = {})
            PresetChip(label = "120", selected = false, onClick = {})
        }
    }
}

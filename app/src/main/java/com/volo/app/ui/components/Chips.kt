package com.volo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.theme.Satoshi
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

// Status chips pair every tinted container with a text label (never
// color-only) and accept an optional leading Phosphor glyph — T8+ screens
// pass CheckCircle/WarningCircle/XCircle per status.

@Composable
fun StatusChip(
    variant: StatusVariant,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = variant.container,
        contentColor = variant.content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            if (leading != null) {
                Icon(
                    imageVector = leading,
                    contentDescription = null,
                    tint = variant.content,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = variant.label,
                style = VoloType.label,
                color = variant.content,
            )
        }
    }
}

private val SelectChipLabel = VoloType.bodySmall.copy(
    fontFamily = Satoshi,
    fontWeight = FontWeight.Bold,
)

@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) VoloColors.copper else VoloColors.surfaceRaised,
        contentColor = if (selected) VoloColors.bg else VoloColors.textMuted,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = SelectChipLabel,
                color = if (selected) VoloColors.bg else VoloColors.textMuted,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun StatusChipPreview() {
    VoloTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(variant = usageVariant(UsageState.PASSING))
            StatusChip(variant = usageVariant(UsageState.AT_RISK))
            StatusChip(variant = usageVariant(UsageState.FAILED))
            StatusChip(variant = usageVariant(UsageState.UNVERIFIED))
            StatusChip(variant = lifecycleVariant("CREATED"))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun SelectChipPreview() {
    VoloTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectChip(label = "Active", selected = true, onClick = {})
            SelectChip(label = "Pending", selected = false, onClick = {})
        }
    }
}

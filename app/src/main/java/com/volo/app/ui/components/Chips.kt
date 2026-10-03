package com.volo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.theme.Satoshi
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

// NOTE: the brief calls for an optional leading Phosphor icon per status.
// Phosphor ImageVectors are vendored in Task 7 (icons-phosphor was rejected by
// the Task 0 spike); the icon slot lands there. Until then the chip is
// label-only — never color-only, per the accessibility rule.

@Composable
fun StatusChip(
    variant: StatusVariant,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = variant.container,
        contentColor = variant.content,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
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

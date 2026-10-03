package com.volo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.theme.Satoshi
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

private val ButtonLabel = VoloType.body.copy(
    fontFamily = Satoshi,
    fontWeight = FontWeight.Bold,
)

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(VoloDimens.radiusControl),
        color = if (enabled) VoloColors.copper else VoloColors.surfaceHigh,
        contentColor = if (enabled) VoloColors.bg else VoloColors.textMuted,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = VoloDimens.spaceXl,
                vertical = VoloDimens.spaceMd,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = ButtonLabel)
        }
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(VoloDimens.radiusControl),
        color = Color.Transparent,
        contentColor = if (enabled) VoloColors.text else VoloColors.textMuted,
        border = BorderStroke(1.dp, VoloColors.line),
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = VoloDimens.spaceXl,
                vertical = VoloDimens.spaceMd,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = ButtonLabel)
        }
    }
}

@Composable
fun TapIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = VoloColors.text,
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides VoloColors.text) {
                content()
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun PrimaryButtonPreview() {
    VoloTheme {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            PrimaryButton(
                label = "Continue",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
            Box(modifier = Modifier.padding(top = 12.dp)) {
                PrimaryButton(
                    label = "Disabled",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun SecondaryButtonPreview() {
    VoloTheme {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            SecondaryButton(
                label = "Cancel",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
            Box(modifier = Modifier.padding(top = 12.dp)) {
                SecondaryButton(
                    label = "Disabled",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun TapIconButtonPreview() {
    VoloTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            TapIconButton(
                contentDescription = "Go back",
                onClick = {},
            ) {
                Text(text = "‹", style = VoloType.title)
            }
        }
    }
}

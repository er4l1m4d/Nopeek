package com.volo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.volo.app.ui.theme.Volo
import com.volo.app.ui.theme.VoloSize
import com.volo.app.ui.theme.VoloText
import com.volo.app.ui.theme.VoloTheme

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hero: Boolean = false,
    container: Color? = null,
    content: Color? = null,
) {
    val colors = Volo.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(if (hero) VoloSize.ButtonHero else VoloSize.ButtonStd),
        enabled = enabled,
        shape = CircleShape,
        color = container ?: (if (enabled) colors.primary else colors.surfaceHigh),
        contentColor = content ?: (if (enabled) colors.onPrimary else colors.onSurfaceMuted),
    ) {
        Box(modifier = Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Text(text = label, style = VoloText.TitleSm)
        }
    }
}

@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    ring: Color? = null,
    content: Color? = null,
) {
    val colors = Volo.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(VoloSize.ButtonStd),
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = content ?: (if (enabled) colors.onSurface else colors.onSurfaceMuted),
        border = BorderStroke(1.5.dp, ring ?: (if (enabled) colors.outline else colors.surfaceHigh)),
    ) {
        Box(modifier = Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Text(text = label, style = VoloText.TitleSm)
        }
    }
}

@Composable
fun TextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Volo.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(VoloSize.ButtonStd),
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (enabled) colors.onSurfaceMuted else colors.surfaceHigh,
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text = label, style = VoloText.TitleSm)
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
    val colors = Volo.colors
    Surface(
        onClick = onClick,
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        enabled = false,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = colors.onSurface,
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/** SecondaryButton delegates to TextButton for backward compatibility. */
@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(label = label, onClick = onClick, modifier = modifier, enabled = enabled)
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun ButtonsPreview() {
    VoloTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            PrimaryButton(label = "Lock TikTok", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false, hero = true)
            PrimaryButton(label = "Accept $5", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
            PrimaryButton(label = "Disabled", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
            OutlineButton(label = "Rematch", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
            TextButton(label = "Decline", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
        }
    }
}

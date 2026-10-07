package com.nopeek.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekShape
import com.nopeek.app.ui.theme.NopeekSize
import com.nopeek.app.ui.theme.NopeekSpace
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme

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
    val colors = Nopeek.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(if (hero) NopeekSize.ButtonHero else NopeekSize.ButtonStd),
        enabled = enabled,
        shape = NopeekShape.Full,
        color = container ?: (if (enabled) colors.primary else colors.surfaceHigh),
        contentColor = content ?: (if (enabled) colors.onPrimary else colors.onSurfaceMuted),
    ) {
        Box(modifier = Modifier.padding(horizontal = NopeekSpace.S24), contentAlignment = Alignment.Center) {
            Text(text = label, style = NopeekText.TitleSm)
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
    val colors = Nopeek.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(NopeekSize.ButtonStd),
        enabled = enabled,
        shape = NopeekShape.Full,
        color = Color.Transparent,
        contentColor = content ?: (if (enabled) colors.onSurface else colors.onSurfaceMuted),
        border = BorderStroke(1.5.dp, ring ?: (if (enabled) colors.outline else colors.surfaceHigh)),
    ) {
        Box(modifier = Modifier.padding(horizontal = NopeekSpace.S24), contentAlignment = Alignment.Center) {
            Text(text = label, style = NopeekText.TitleSm)
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
    val colors = Nopeek.colors
    // Disabled stays legible: the old Turf2 label measured 1.73:1 against Pitch, which reads as a
    // rendering bug rather than an inert control.
    val content = if (enabled) colors.onSurfaceMuted else colors.onSurfaceMuted.copy(alpha = 0.6f)
    Surface(
        onClick = onClick,
        modifier = modifier.height(NopeekSize.ButtonStd),
        enabled = enabled,
        shape = NopeekShape.Full,
        color = Color.Transparent,
        contentColor = content,
    ) {
        Box(modifier = Modifier.padding(horizontal = NopeekSpace.S16), contentAlignment = Alignment.Center) {
            Text(text = label, style = NopeekText.TitleSm, color = content)
        }
    }
}

@Composable
fun TapIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = Nopeek.colors
    Surface(
        onClick = onClick,
        modifier = modifier
            .semantics {
                this[SemanticsProperties.Role] = Role.Button
                this.contentDescription = contentDescription
            }
            .sizeIn(minWidth = NopeekSize.Touch, minHeight = NopeekSize.Touch),
        enabled = enabled,
        shape = NopeekShape.Full,
        color = Color.Transparent,
        contentColor = colors.onSurface,
    ) {
        Box(modifier = Modifier.size(NopeekSize.Touch), contentAlignment = Alignment.Center) {
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
    NopeekTheme {
        Column(modifier = Modifier.padding(NopeekSpace.S16), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(NopeekSpace.S12)) {
            PrimaryButton(label = "Lock TikTok", onClick = {}, modifier = Modifier.fillMaxWidth(), hero = true)
            PrimaryButton(label = "Lock TikTok", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false, hero = true)
            PrimaryButton(label = "Accept $5", onClick = {}, modifier = Modifier.fillMaxWidth())
            OutlineButton(label = "Rematch", onClick = {}, modifier = Modifier.fillMaxWidth())
            OutlineButton(label = "Rematch", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
            TextButton(label = "Decline", onClick = {}, modifier = Modifier.fillMaxWidth())
            TextButton(label = "Decline", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
        }
    }
}

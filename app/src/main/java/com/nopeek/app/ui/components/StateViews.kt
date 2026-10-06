package com.nopeek.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nopeek.app.ui.icons.Phosphor
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekShape
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Nopeek.colors
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = colors.primary,
        )
        Text(text = title, style = NopeekText.TitleMd, color = colors.onSurface, textAlign = TextAlign.Center)
        Text(text = message, style = NopeekText.BodyMd, color = colors.onSurfaceMuted, textAlign = TextAlign.Center)
        PrimaryButton(label = actionLabel, onClick = onAction)
    }
}

@Composable
fun InlineError(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val colors = Nopeek.colors
    Surface(
        modifier = modifier,
        shape = NopeekShape.Sm,
        color = colors.danger.copy(alpha = 0.12f),
        contentColor = colors.danger,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = message, style = NopeekText.BodyMd, color = colors.danger, modifier = Modifier.weight(1f))
            if (onRetry != null) {
                TextButton(label = "Retry", onClick = onRetry)
            }
        }
    }
}

@Composable
fun LoadingSkeleton(height: Dp, modifier: Modifier = Modifier) {
    val colors = Nopeek.colors
    Surface(
        modifier = modifier.height(height),
        shape = NopeekShape.Md,
        color = colors.surfaceHigh,
    ) {}
}

@Composable
fun ForfeitDialog(stakeLabel: String, opponentName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = Nopeek.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceRaised,
        title = {
            Text(text = "Forfeit the duel?", style = NopeekText.TitleMd, color = colors.onSurface)
        },
        text = {
            Text(
                text = "Your $stakeLabel stake goes to $opponentName and your streak breaks.",
                style = NopeekText.BodyMd,
                color = colors.onSurfaceMuted,
            )
        },
        confirmButton = {
            PrimaryButton(label = "Forfeit", onClick = onConfirm, container = colors.danger, content = colors.surface)
        },
        dismissButton = {
            OutlineButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun EmptyStatePreview() {
    NopeekTheme {
        EmptyState(
            icon = Phosphor.Flag,
            title = "No open duels",
            message = "There are no open challenges right now. Check back soon or create your own.",
            actionLabel = "Create a duel",
            onAction = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun InlineErrorPreview() {
    NopeekTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InlineError(
                message = "Couldn't load duels. Check your connection and try again.",
                onRetry = {},
                modifier = Modifier.fillMaxWidth(),
            )
            InlineError(
                message = "Invalid address — double-check and try again.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun LoadingSkeletonPreview() {
    NopeekTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingSkeleton(
                height = 120.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            LoadingSkeleton(
                height = 56.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

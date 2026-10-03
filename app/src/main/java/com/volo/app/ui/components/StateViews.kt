package com.volo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(VoloDimens.spaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = VoloColors.copper,
        )
        Text(
            text = title,
            style = VoloType.title,
            color = VoloColors.text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = VoloType.bodySmall,
            color = VoloColors.textMuted,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            label = actionLabel,
            onClick = onAction,
        )
    }
}

@Composable
fun InlineError(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(VoloDimens.radiusControl),
        color = VoloColors.fail.copy(alpha = 0.12f),
        contentColor = VoloColors.fail,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = VoloDimens.spaceLg,
                vertical = VoloDimens.spaceMd,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
        ) {
            Text(
                text = message,
                style = VoloType.bodySmall,
                color = VoloColors.fail,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                SecondaryButton(
                    label = "Retry",
                    onClick = onRetry,
                )
            }
        }
    }
}

@Composable
fun LoadingSkeleton(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(VoloDimens.radiusCard),
        color = VoloColors.surfaceHigh,
    ) {
    }
}

// Preview-only glyph: StateViews.kt must stay free of Material icon imports
// (the single sanctioned Material-icon stand-in lives in DuelCard), so the EmptyState
// preview draws a hand-built plus-mark instead of importing an icon pack.
// Task 7+ screens pass real (Phosphor) ImageVectors to EmptyState.
private val PreviewGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "PreviewGlyph",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString("M12 5v14M5 12h14").toNodes(),
        stroke = SolidColor(VoloColors.copper),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
    ).build()
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun EmptyStatePreview() {
    VoloTheme {
        EmptyState(
            icon = PreviewGlyph,
            title = "No open duels",
            message = "There are no open challenges right now. Check back soon or create your own.",
            actionLabel = "Create a duel",
            onAction = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun InlineErrorPreview() {
    VoloTheme {
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

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun LoadingSkeletonPreview() {
    VoloTheme {
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

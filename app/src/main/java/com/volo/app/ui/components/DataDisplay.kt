package com.volo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

@Composable
fun UsageRing(
    usedSeconds: Int,
    limitSeconds: Int,
    centerText: String,
    modifier: Modifier = Modifier,
) {
    val fraction = fractionOf(usedSeconds.toFloat(), limitSeconds.toFloat())
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = "Usage $centerText" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2f
            val arcTopLeft = Offset(inset, inset)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val style = Stroke(width = stroke, cap = StrokeCap.Round)
            drawArc(
                color = VoloColors.surfaceHigh,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = style,
            )
            drawArc(
                color = VoloColors.copper,
                startAngle = -90f,
                sweepAngle = sweepDegrees(fraction),
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = style,
            )
        }
        Text(
            text = centerText,
            style = VoloType.monoLarge,
            color = VoloColors.text,
        )
    }
}

@Composable
fun OpponentUsageBar(
    youLabel: String,
    youText: String,
    youFraction: Float,
    opponentLabel: String,
    opponentText: String,
    opponentFraction: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics {
            contentDescription = "$youLabel $youText, $opponentLabel $opponentText"
        },
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
    ) {
        UsageCompareRow(
            label = youLabel,
            text = youText,
            // fractionOf(x, 1f) is the clamp-to-unit-range path of the
            // tested helper: negatives/zero -> 0f, overshoot -> 1f.
            fraction = fractionOf(youFraction, 1f),
            color = VoloColors.copper,
        )
        UsageCompareRow(
            label = opponentLabel,
            text = opponentText,
            fraction = fractionOf(opponentFraction, 1f),
            color = VoloColors.textMuted,
        )
    }
}

@Composable
private fun UsageCompareRow(
    label: String,
    text: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = VoloType.monoSmall,
                color = VoloColors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = text,
                style = VoloType.monoSmall,
                color = VoloColors.text,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(VoloDimens.radiusPill))
                .background(VoloColors.surfaceHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(VoloDimens.radiusPill))
                    .background(color),
            )
        }
    }
}

@Composable
fun SectionHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs),
        ) {
            Text(
                text = eyebrow,
                style = VoloType.label,
                color = VoloColors.textMuted,
            )
            Text(
                text = title,
                style = VoloType.title,
                color = VoloColors.text,
            )
        }
        if (action != null) {
            action()
        }
    }
}

@Composable
fun MetricRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
    ) {
        Text(
            text = title,
            style = VoloType.body,
            color = VoloColors.text,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = VoloType.mono,
            color = VoloColors.text,
            textAlign = TextAlign.End,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun UsageRingPreview() {
    VoloTheme {
        UsageRing(
            usedSeconds = 18 * 60,
            limitSeconds = 30 * 60,
            centerText = "18m / 30m",
            modifier = Modifier
                .padding(16.dp)
                .size(180.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun OpponentUsageBarPreview() {
    VoloTheme {
        OpponentUsageBar(
            youLabel = "YOU",
            youText = "18m",
            youFraction = 0.6f,
            opponentLabel = "MAYA",
            opponentText = "24m",
            opponentFraction = 0.8f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun SectionHeaderPreview() {
    VoloTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                eyebrow = "THIS WEEK",
                title = "Screen time",
                modifier = Modifier.fillMaxWidth(),
            )
            SectionHeader(
                eyebrow = "DUEL",
                title = "Usage breakdown",
                modifier = Modifier.fillMaxWidth(),
                action = {
                    Text(
                        text = "See all",
                        style = VoloType.monoSmall,
                        color = VoloColors.copper,
                    )
                },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun MetricRowPreview() {
    VoloTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricRow(
                title = "Stake",
                value = "5.00 USDC",
                modifier = Modifier.fillMaxWidth(),
            )
            MetricRow(
                title = "Time left",
                value = "02:14:33",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

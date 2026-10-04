package com.volo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType

// Pure chart math (JVM-tested in ChartMathTest). Rings and bars normalize
// through these so the visuals can never divide by zero or overshoot.

fun fractionOf(value: Float, max: Float): Float {
    if (max <= 0f || value <= 0f) return 0f
    return (value / max).coerceAtMost(1f)
}

fun sweepDegrees(fraction: Float): Float = fraction.coerceIn(0f, 1f) * 360f

data class UsageBarItem(
    val label: String,
    val duration: String,
    val fraction: Float,
)

@Composable
fun UsageBreakdownBars(
    items: List<UsageBarItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics {
            contentDescription = items.joinToString(prefix = "Usage by app: ") { "${it.label} ${it.duration}" }
        },
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
    ) {
        items.forEach { item ->
            // fractionOf(x, 1f) is the clamp-to-unit-range path of the
            // tested helper: negatives/zero -> 0f, overshoot -> 1f.
            val clamped = fractionOf(item.fraction, 1f)
            Column(
                verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.label,
                        style = VoloType.bodySmall,
                        color = VoloColors.text,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.duration,
                        style = VoloType.monoSmall,
                        color = VoloColors.textMuted,
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
                            .fillMaxWidth(clamped)
                            .clip(RoundedCornerShape(VoloDimens.radiusPill))
                            .background(VoloColors.copper),
                    )
                }
            }
        }
    }
}

@Composable
fun DailyBarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    val count = minOf(values.size, labels.size)
    val shownValues = values.take(count)
    val shownLabels = labels.take(count)
    val max = shownValues.maxOrNull() ?: 0f
    Column(
        modifier = modifier.semantics {
            contentDescription = if (count == 0) {
                "Daily usage chart, no data"
            } else {
                shownLabels.zip(shownValues).joinToString(prefix = "Daily usage: ") { (day, value) -> "$day ${value}m" }
            }
        },
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceSm),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            val axisY = size.height - 1.dp.toPx()
            drawLine(
                color = VoloColors.textMuted,
                start = Offset(0f, axisY),
                end = Offset(size.width, axisY),
                strokeWidth = 1.dp.toPx(),
            )
            if (count == 0) return@Canvas
            val slot = size.width / count
            val barWidth = slot * 0.55f
            val corner = CornerRadius(4.dp.toPx())
            val topPad = 8.dp.toPx()
            shownValues.forEachIndexed { index, value ->
                val barHeight = fractionOf(value, max) * (axisY - topPad)
                val left = index * slot + (slot - barWidth) / 2f
                drawRoundRect(
                    color = VoloColors.copper,
                    topLeft = Offset(left, axisY - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            shownLabels.forEach { tick ->
                Text(
                    text = tick,
                    style = VoloType.monoSmall,
                    color = VoloColors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = VoloColors.copper,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                contentDescription = if (values.isEmpty()) {
                    "Trend chart, no data"
                } else {
                    "Trend chart from ${values.first()} to ${values.last()}"
                }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        if (values.size == 1) {
            drawCircle(
                color = lineColor,
                radius = 2.dp.toPx(),
                center = center,
            )
            return@Canvas
        }
        val min = values.minOrNull() ?: 0f
        val range = (values.maxOrNull() ?: 0f) - min
        val padX = 2.dp.toPx()
        val padY = 4.dp.toPx()
        val points = values.mapIndexed { index, value ->
            // fractionOf(value - min, range) is the min-max normalize path:
            // flat series (range <= 0) render as a mid-line.
            val norm = if (range <= 0f) 0.5f else fractionOf(value - min, range)
            val x = padX + index * (size.width - 2 * padX) / (values.size - 1)
            val y = size.height - padY - norm * (size.height - 2 * padY)
            Offset(x, y)
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun UsageBreakdownBarsPreview() {
    VoloTheme {
        UsageBreakdownBars(
            items = listOf(
                UsageBarItem(label = "Instagram", duration = "18m", fraction = 0.6f),
                UsageBarItem(label = "YouTube", duration = "9m", fraction = 0.3f),
                UsageBarItem(label = "X", duration = "3m", fraction = 0.1f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun DailyBarChartPreview() {
    VoloTheme {
        DailyBarChart(
            values = listOf(12f, 28f, 18f, 30f, 9f, 22f, 15f),
            labels = listOf("M", "T", "W", "T", "F", "S", "S"),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun SparklinePreview() {
    VoloTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Sparkline(
                values = listOf(4f, 9f, 6f, 14f, 11f, 18f, 15f),
                modifier = Modifier.fillMaxWidth(),
            )
            Sparkline(
                values = listOf(4f, 9f, 6f, 14f, 11f, 18f, 15f),
                modifier = Modifier.fillMaxWidth(),
                lineColor = VoloColors.textMuted,
            )
        }
    }
}

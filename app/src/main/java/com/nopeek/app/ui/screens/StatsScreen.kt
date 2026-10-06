package com.nopeek.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nopeek.app.ui.components.ReceiptRow
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekSpace
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme

enum class Form { WON, LOST, IDLE }

private val DemoDays = listOf("M", "T", "W", "T", "F", "S", "S")
private val DemoForm = listOf(Form.WON, Form.WON, Form.IDLE, Form.LOST, Form.WON, Form.IDLE, Form.WON)

@Composable
fun StatsScreen() {
    val colors = Nopeek.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NopeekSpace.S16),
    ) {
        Text(text = "Stats", style = NopeekText.DisplayTitle, color = colors.onSurface, modifier = Modifier.padding(top = NopeekSpace.S16))
        Text(text = "Your history", style = NopeekText.BodyLg, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = NopeekSpace.S8))
        Box(modifier = Modifier.fillMaxWidth().padding(top = NopeekSpace.S24), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(text = "27h 14m", style = NopeekText.DisplayClock, color = colors.primary)
        }
        Text(text = "total time won", style = NopeekText.BodyMd, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = NopeekSpace.S4))
        Column(modifier = Modifier.padding(top = NopeekSpace.S32), verticalArrangement = Arrangement.spacedBy(NopeekSpace.S12)) {
            ReceiptRow(label = "Duels won", value = "7")
            ReceiptRow(label = "Duels lost", value = "2")
            ReceiptRow(label = "Win rate", value = "78%")
            ReceiptRow(label = "Best streak", value = "5")
        }
        Column(modifier = Modifier.padding(top = NopeekSpace.S32)) {
            Text(text = "Last 7 days", style = NopeekText.TitleMd, color = colors.onSurface)
            Row(DemoDays.size, Modifier.padding(top = NopeekSpace.S12)) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = NopeekSpace.S4)
                        .background(
                            when (DemoForm[i]) {
                                Form.WON -> colors.you
                                Form.LOST -> colors.danger
                                Form.IDLE -> colors.surfaceRaised
                            }
                        )
                        .padding(NopeekSpace.S16),
                ) {
                    // Foreground follows the surface: volt and coral are loud fills and need Pitch text (AA).
                    Text(
                        text = DemoDays[i],
                        style = NopeekText.LabelSm,
                        color = when (DemoForm[i]) {
                            Form.WON -> colors.onWin
                            Form.LOST -> colors.onLose
                            Form.IDLE -> colors.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Row(count: Int, modifier: Modifier = Modifier, content: @Composable (Int) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        repeat(count) { i -> content(i) }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun StatsScreenPreview() {
    NopeekTheme {
        StatsScreen()
    }
}
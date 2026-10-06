package com.volo.app.ui.screens

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
import com.volo.app.ui.components.ReceiptRow
import com.volo.app.ui.theme.Volo
import com.volo.app.ui.theme.VoloText
import com.volo.app.ui.theme.VoloTheme

enum class Form { WON, LOST, IDLE }

private val DemoDays = listOf("M", "T", "W", "T", "F", "S", "S")
private val DemoForm = listOf(Form.WON, Form.WON, Form.IDLE, Form.LOST, Form.WON, Form.IDLE, Form.WON)

@Composable
fun StatsScreen() {
    val colors = Volo.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(text = "Stats", style = VoloText.DisplayTitle, color = colors.onSurface, modifier = Modifier.padding(top = 16.dp))
        Text(text = "Your history", style = VoloText.BodyLg, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 8.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text(text = "27h 14m", style = VoloText.DisplayClock, color = colors.primary)
        }
        Text(text = "total time won", style = VoloText.BodyMd, color = colors.onSurfaceMuted, modifier = Modifier.padding(top = 4.dp))
        Column(modifier = Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReceiptRow(label = "Duels won", value = "7")
            ReceiptRow(label = "Duels lost", value = "2")
            ReceiptRow(label = "Win rate", value = "78%")
            ReceiptRow(label = "Best streak", value = "5")
        }
        Column(modifier = Modifier.padding(top = 32.dp)) {
            Text(text = "Last 7 days", style = VoloText.TitleMd, color = colors.onSurface)
            Row(DemoDays.size, Modifier.padding(top = 12.dp)) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .background(when (DemoForm[i]) { Form.WON -> colors.you; Form.LOST -> colors.danger; Form.IDLE -> colors.surfaceRaised })
                        .padding(16.dp),
                ) {
                    Text(text = DemoDays[i], style = VoloText.LabelSm, color = colors.onSurface)
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
    VoloTheme {
        StatsScreen()
    }
}